using System.IO;
using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Search;

/// <summary>Describes what a file walk should collect. Mirrors the Android FileSearchSpec.</summary>
// HiddenOnly = only entries that are hidden ('.'-prefixed or the Hidden attribute).
// Extensions = restrict to these extensions (null = any). DownloadsOnly = under a Downloads dir.
public sealed record FileSearchSpec(
    bool IncludeFiles = true,
    bool IncludeFolders = false,
    bool HiddenOnly = false,
    IReadOnlySet<string>? Extensions = null,
    bool DownloadsOnly = false);

/// <summary>
/// A time-boxed, cancellable, iterative (stack-based) filesystem walk shared by the
/// file and media providers. This is the Windows analog of the Android filesystem walk
/// in FileSearchProvider — same budget/visited caps, just rooted at the user's known
/// folders instead of external storage.
/// </summary>
public static class FileWalker
{
    private const int MaxVisited = 120_000;

    // Directories we never descend into: huge/system trees that would burn the budget.
    private static readonly string[] SkipNames =
    {
        "$Recycle.Bin", "System Volume Information", "Windows",
        "Program Files", "Program Files (x86)", "ProgramData",
    };

    public static List<SearchResult> Walk(
        IEnumerable<string> roots,
        string query,
        FileSearchSpec spec,
        int limit,
        long deadlineTicks,
        CancellationToken ct)
    {
        var q = query.ToLowerInvariant();
        var output = new List<SearchResult>();
        var stack = new Stack<string>();

        // Push so that the most relevant roots (early in the list) are popped first.
        foreach (var root in roots.Reverse())
        {
            try { if (Directory.Exists(root)) stack.Push(root); }
            catch { /* ignore unreadable root */ }
        }

        int visited = 0;

        while (stack.Count > 0)
        {
            if (ct.IsCancellationRequested ||
                output.Count >= limit ||
                visited >= MaxVisited ||
                Environment.TickCount64 > deadlineTicks)
            {
                break;
            }

            var dir = stack.Pop();
            string[] entries;
            try { entries = Directory.GetFileSystemEntries(dir); }
            catch { continue; } // access denied, removed mid-walk, etc.

            foreach (var path in entries)
            {
                if (output.Count >= limit) break;
                visited++;

                FileSystemInfo info;
                bool isDir;
                try
                {
                    var attrs = File.GetAttributes(path);
                    isDir = (attrs & FileAttributes.Directory) != 0;
                    info = isDir ? new DirectoryInfo(path) : new FileInfo(path);
                }
                catch { continue; }

                var name = info.Name;

                if (isDir)
                {
                    if (IsSkippedDir(name) || IsReparsePoint(info)) continue;
                    stack.Push(path);
                    if (spec.IncludeFolders && Matches(name, path, q, spec, isDir: true))
                        output.Add(FolderResult((DirectoryInfo)info));
                }
                else if (spec.IncludeFiles && Matches(name, path, q, spec, isDir: false))
                {
                    output.Add(FileResult((FileInfo)info));
                }
            }
        }

        return output;
    }

    private static bool Matches(string name, string path, string lowerQuery, FileSearchSpec spec, bool isDir)
    {
        if (!name.ToLowerInvariant().Contains(lowerQuery)) return false;
        if (spec.HiddenOnly && !IsHidden(name, path)) return false;
        if (spec.DownloadsOnly && !path.Contains("\\Downloads", StringComparison.OrdinalIgnoreCase)) return false;
        if (!isDir && spec.Extensions != null && !spec.Extensions.Contains(FileTypes.ExtensionOf(name))) return false;
        return true;
    }

    private static bool IsHidden(string name, string path)
    {
        if (name.StartsWith('.')) return true;
        try { return (File.GetAttributes(path) & FileAttributes.Hidden) != 0; }
        catch { return false; }
    }

    internal static bool IsReparsePoint(FileSystemInfo info) =>
        (info.Attributes & FileAttributes.ReparsePoint) != 0;

    /// <summary>Huge/system directory trees the walk and the index both refuse to descend into.</summary>
    internal static bool IsSkippedDir(string name)
    {
        foreach (var s in SkipNames)
            if (string.Equals(name, s, StringComparison.OrdinalIgnoreCase)) return true;
        return false;
    }

    private static SearchResult FileResult(FileInfo f)
    {
        var type = FileTypes.TypeForName(f.Name);
        long size = 0, ticks = 0;
        try { size = f.Length; } catch { /* ignore */ }
        try { ticks = f.LastWriteTimeUtc.Ticks; } catch { /* ignore */ }

        return new SearchResult(
            Id: "file:" + f.FullName,
            Title: f.Name,
            Subtitle: f.DirectoryName ?? f.FullName,
            Type: type,
            Launch: new LaunchAction.OpenPath(f.FullName),
            SizeBytes: size,
            DateModified: ticks,
            IconPath: f.FullName);
    }

    private static SearchResult FolderResult(DirectoryInfo d)
    {
        long ticks = 0;
        try { ticks = d.LastWriteTimeUtc.Ticks; } catch { /* ignore */ }

        return new SearchResult(
            Id: "folder:" + d.FullName,
            Title: d.Name,
            Subtitle: d.Parent?.FullName ?? d.FullName,
            Type: ResultType.Folder,
            Launch: new LaunchAction.OpenPath(d.FullName),
            DateModified: ticks,
            IconPath: d.FullName);
    }
}
