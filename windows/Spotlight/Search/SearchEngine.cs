using NeverSoft.Spotlight.Models;
using NeverSoft.Spotlight.Services;

namespace NeverSoft.Spotlight.Search;

/// <summary>
/// Aggregates every provider into one device-wide search, routed by the active filter
/// selection — a faithful port of the Android SearchEngine. Each filter only queries the
/// providers it needs (so "Media" never walks for documents, "Files" never lists apps, and
/// so on), then results are ranked (exact → prefix → substring), personalized by usage, and
/// de-duplicated. Runs off the UI thread and is fully cancellable so each keystroke can
/// abandon the previous query.
/// </summary>
public sealed class SearchEngine
{
    public const int DefaultLimit = 400;
    public const long WalkBudgetMs = 2500;

    private readonly AppSearchProvider _apps = new();
    private readonly ContactSearchProvider _contacts = new();
    private readonly MediaSearchProvider _media;
    private readonly FileSearchProvider _files;
    private readonly UsageStore _usage;

    public SearchEngine(UsageStore usage, FileIndex index)
    {
        _usage = usage;
        _media = new MediaSearchProvider(index);
        _files = new FileSearchProvider(index);
    }

    public Task<IReadOnlyList<SearchResult>> SearchAsync(
        string query, FilterSelection selection, int limit = DefaultLimit, CancellationToken ct = default)
        => Task.Run(() => Search(query, selection, limit, ct), ct);

    private IReadOnlyList<SearchResult> Search(
        string query, FilterSelection selection, int limit, CancellationToken ct)
    {
        // An empty query is a valid "browse" request: every provider treats it as match-all,
        // so the list populates from the active filter before the user types anything.
        var q = query.Trim();
        long deadline = Environment.TickCount64 + WalkBudgetMs;
        var results = new List<SearchResult>();

        switch (selection.Primary)
        {
            case PrimaryFilter.All:
                results.AddRange(_apps.Search(q, 40, ct));
                ct.ThrowIfCancellationRequested();
                results.AddRange(_contacts.Search(q, 30, ct));
                ct.ThrowIfCancellationRequested();
                results.AddRange(_media.Search(
                    q, new[] { ResultType.Image, ResultType.Video, ResultType.Audio }, 90, deadline, ct));
                ct.ThrowIfCancellationRequested();
                results.AddRange(_files.Search(
                    q, new FileSearchSpec(IncludeFiles: true, IncludeFolders: true), 160, deadline, ct));
                break;

            case PrimaryFilter.Media:
                var types = selection.Sub switch
                {
                    SubFilter.Photos => new[] { ResultType.Image },
                    SubFilter.Videos => new[] { ResultType.Video },
                    SubFilter.Audio => new[] { ResultType.Audio },
                    _ => new[] { ResultType.Image, ResultType.Video, ResultType.Audio },
                };
                results.AddRange(_media.Search(q, types, limit, deadline, ct));
                break;

            case PrimaryFilter.Files:
                var spec = selection.Sub switch
                {
                    SubFilter.Documents => new FileSearchSpec(Extensions: FileTypes.Document),
                    SubFilter.Downloads => new FileSearchSpec(DownloadsOnly: true),
                    SubFilter.Archives => new FileSearchSpec(Extensions: FileTypes.Archive),
                    SubFilter.Programs => new FileSearchSpec(Extensions: FileTypes.Program),
                    _ => new FileSearchSpec(IncludeFiles: true, IncludeFolders: true),
                };
                results.AddRange(_files.Search(q, spec, limit, deadline, ct));
                break;

            case PrimaryFilter.Hidden:
                var hidden = selection.Sub switch
                {
                    SubFilter.HiddenFolders =>
                        new FileSearchSpec(IncludeFiles: false, IncludeFolders: true, HiddenOnly: true),
                    SubFilter.HiddenFiles =>
                        new FileSearchSpec(IncludeFiles: true, IncludeFolders: false, HiddenOnly: true),
                    _ =>
                        new FileSearchSpec(IncludeFiles: true, IncludeFolders: true, HiddenOnly: true),
                };
                results.AddRange(_files.Search(q, hidden, limit, deadline, ct));
                break;
        }

        ct.ThrowIfCancellationRequested();
        return RankAndDedupe(results, q, limit);
    }

    private IReadOnlyList<SearchResult> RankAndDedupe(List<SearchResult> list, string query, int limit)
    {
        var lower = query.ToLowerInvariant();
        return list
            .GroupBy(r => r.Id)
            .Select(g => g.First())                       // distinctBy id, preserving order
            .OrderBy(r => MatchScore(r.Title, lower))     // exact → prefix → substring
            .ThenBy(r => TypeOrder(r.Type))               // apps & contacts float up in mixed results
            .ThenByDescending(r => _usage.Score(r.Id))    // luxury: personalize by how often you launch it
            .ThenByDescending(r => r.DateModified)        // newest first
            .Take(limit)
            .ToList();
    }

    /// <summary>Lower is better: exact match first, then prefix, then substring.</summary>
    private static int MatchScore(string title, string lowerQuery)
    {
        var t = title.ToLowerInvariant();
        if (t == lowerQuery) return 0;
        if (t.StartsWith(lowerQuery, StringComparison.Ordinal)) return 1;
        return 2;
    }

    /// <summary>Surface apps & contacts above raw files in mixed (All) results.</summary>
    private static int TypeOrder(ResultType type) => type switch
    {
        ResultType.App => 0,
        ResultType.Contact => 1,
        ResultType.Image or ResultType.Video or ResultType.Audio => 2,
        ResultType.Document or ResultType.Program or ResultType.Archive => 3,
        ResultType.Folder => 4,
        ResultType.File => 5,
        _ => 6,
    };
}
