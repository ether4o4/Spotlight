using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Search;

/// <summary>
/// Finds matching files/folders by name. Once the in-memory <see cref="FileIndex"/> is built,
/// queries filter it instantly (so the list narrows live as you type); before then it falls
/// back to the time-boxed live walk — the Windows analog of the Android FileSearchProvider.
/// </summary>
public sealed class FileSearchProvider
{
    private readonly FileIndex _index;

    public FileSearchProvider(FileIndex index) => _index = index;

    public List<SearchResult> Search(
        string query, FileSearchSpec spec, int limit, long deadlineTicks, CancellationToken ct)
    {
        if (_index.Ready)
            return _index.Query(query.ToLowerInvariant(), spec, limit, ct);

        return FileWalker.Walk(KnownFolders.General(), query, spec, limit, deadlineTicks, ct);
    }
}
