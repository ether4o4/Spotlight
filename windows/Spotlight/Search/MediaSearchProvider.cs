using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Search;

/// <summary>
/// Finds images / video / audio by name. Uses the in-memory <see cref="FileIndex"/> (filtered
/// to the requested media extensions) for instant live narrowing, falling back to a time-boxed
/// walk of the media folders before the index is ready. The Android app uses MediaStore; Windows
/// has no equally reliable index, so we build our own.
/// </summary>
public sealed class MediaSearchProvider
{
    private readonly FileIndex _index;

    public MediaSearchProvider(FileIndex index) => _index = index;

    public List<SearchResult> Search(
        string query, IReadOnlyCollection<ResultType> types, int limit, long deadlineTicks, CancellationToken ct)
    {
        var exts = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        if (types.Contains(ResultType.Image)) exts.UnionWith(FileTypes.Image);
        if (types.Contains(ResultType.Video)) exts.UnionWith(FileTypes.Video);
        if (types.Contains(ResultType.Audio)) exts.UnionWith(FileTypes.Audio);

        var spec = new FileSearchSpec(IncludeFiles: true, IncludeFolders: false, Extensions: exts);

        if (_index.Ready)
            return _index.Query(query.ToLowerInvariant(), spec, limit, ct);

        return FileWalker.Walk(KnownFolders.Media(), query, spec, limit, deadlineTicks, ct);
    }
}
