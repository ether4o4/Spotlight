using System.IO;
using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Search;

/// <summary>
/// An in-memory snapshot of every file and folder name under the user's known folders,
/// built once in the background. Searching then filters this snapshot in memory, so typing
/// narrows the list instantly and deterministically: type "." and everything whose *name*
/// contains a dot appears; add characters and the list shrinks. Matching is always on the
/// name (never the full path) — you never have to type a path.
///
/// Until the first build finishes, the providers fall back to the live time-boxed walk, so
/// search works from the very first keystroke; results just get snappier once the index is up.
/// </summary>
public sealed class FileIndex
{
    private readonly record struct Entry(
        string Name, string NameLower, string FullName,
        bool IsDir, bool IsHidden, string Ext, long Size, long Ticks);

    private const int MaxEntries = 200_000;
    private const long BuildBudgetMs = 20_000; // generous; runs off the UI thread

    private volatile Entry[] _entries = Array.Empty<Entry>();
    private long _builtTick;
    private int _building; // 0 = idle, 1 = building (guarded by Interlocked)

    public bool Ready => _entries.Length > 0;
    public int Count => _entries.Length;

    /// <summary>Rebuild in the background if the snapshot is missing or older than the max age.</summary>
    public void EnsureFresh(long maxAgeMs = 120_000)
    {
        if (_entries.Length == 0 || Environment.TickCount64 - _builtTick > maxAgeMs)
            BuildInBackground();
    }

    public void BuildInBackground()
    {
        if (Interlocked.Exchange(ref _building, 1) == 1) return; // a build is already running
        Task.Run(() =>
        {
            try { Build(); }
            catch { /* ignore — keep any previous snapshot */ }
            finally { Interlocked.Exchange(ref _building, 0); }
        });
    }

    private void Build()
    {
        var list = new List<Entry>(8192);
        var stack = new Stack<string>();
        foreach (var root in KnownFolders.General().Reverse())
        {
            try { if (Directory.Exists(root)) stack.Push(root); }
            catch { /* ignore */ }
        }

        long deadline = Environment.TickCount64 + BuildBudgetMs;

        while (stack.Count > 0)
        {
            if (list.Count >= MaxEntries || Environment.TickCount64 > deadline) break;

            var dir = stack.Pop();
            string[] entries;
            try { entries = Directory.GetFileSystemEntries(dir); }
            catch { continue; }

            foreach (var path in entries)
            {
                if (list.Count >= MaxEntries) break;

                try
                {
                    var attrs = File.GetAttributes(path);
                    bool isDir = (attrs & FileAttributes.Directory) != 0;
                    var name = Path.GetFileName(path);
                    if (string.IsNullOrEmpty(name)) name = path;
                    bool isHidden = name.StartsWith('.') || (attrs & FileAttributes.Hidden) != 0;

                    if (isDir)
                    {
                        if (FileWalker.IsSkippedDir(name)) continue;
                        if ((attrs & FileAttributes.ReparsePoint) == 0) stack.Push(path);
                        long dirTicks = 0;
                        try { dirTicks = Directory.GetLastWriteTimeUtc(path).Ticks; } catch { /* ignore */ }
                        list.Add(new Entry(name, name.ToLowerInvariant(), path, true, isHidden, string.Empty, 0, dirTicks));
                    }
                    else
                    {
                        long size = 0, ticks = 0;
                        try { var fi = new FileInfo(path); size = fi.Length; ticks = fi.LastWriteTimeUtc.Ticks; }
                        catch { /* ignore */ }
                        list.Add(new Entry(name, name.ToLowerInvariant(), path, false, isHidden,
                            FileTypes.ExtensionOf(name), size, ticks));
                    }
                }
                catch { /* skip a single bad entry */ }
            }
        }

        _entries = list.ToArray();
        _builtTick = Environment.TickCount64;
    }

    /// <summary>Filter the snapshot in memory. <paramref name="lowerQuery"/> is matched as a
    /// substring of the entry's name; an empty query matches everything (browse).</summary>
    public List<SearchResult> Query(string lowerQuery, FileSearchSpec spec, int limit, CancellationToken ct)
    {
        var snapshot = _entries;
        var output = new List<SearchResult>(Math.Min(limit, 256));

        for (int i = 0; i < snapshot.Length; i++)
        {
            if (output.Count >= limit) break;
            if ((i & 0x3FFF) == 0 && ct.IsCancellationRequested) break;

            ref readonly var e = ref snapshot[i];

            if (e.IsDir && !spec.IncludeFolders) continue;
            if (!e.IsDir && !spec.IncludeFiles) continue;
            if (spec.HiddenOnly && !e.IsHidden) continue;
            if (!e.IsDir && spec.Extensions != null && !spec.Extensions.Contains(e.Ext)) continue;
            if (spec.DownloadsOnly &&
                e.FullName.IndexOf("\\Downloads", StringComparison.OrdinalIgnoreCase) < 0) continue;
            if (lowerQuery.Length > 0 && !e.NameLower.Contains(lowerQuery)) continue;

            output.Add(ToResult(in e));
        }

        return output;
    }

    private static SearchResult ToResult(in Entry e)
    {
        var parent = Path.GetDirectoryName(e.FullName) ?? e.FullName;
        if (e.IsDir)
        {
            return new SearchResult(
                Id: "folder:" + e.FullName,
                Title: e.Name,
                Subtitle: parent,
                Type: ResultType.Folder,
                Launch: new LaunchAction.OpenPath(e.FullName),
                DateModified: e.Ticks,
                IconPath: e.FullName);
        }

        return new SearchResult(
            Id: "file:" + e.FullName,
            Title: e.Name,
            Subtitle: parent,
            Type: FileTypes.TypeForExtension(e.Ext),
            Launch: new LaunchAction.OpenPath(e.FullName),
            SizeBytes: e.Size,
            DateModified: e.Ticks,
            IconPath: e.FullName);
    }
}
