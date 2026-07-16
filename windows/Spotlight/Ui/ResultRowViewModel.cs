using System.ComponentModel;
using System.Windows.Media;
using NeverSoft.Spotlight.Interop;
using NeverSoft.Spotlight.Models;
using NeverSoft.Spotlight.Search;

namespace NeverSoft.Spotlight.Ui;

/// <summary>Row shown in the results list. Wraps a <see cref="SearchResult"/> and lazily loads a
/// real shell icon/thumbnail (falling back to the vector glyph until/unless one arrives).</summary>
public sealed class ResultRowViewModel : INotifyPropertyChanged
{
    public SearchResult Result { get; }

    public ResultRowViewModel(SearchResult result)
    {
        Result = result;
        Meta = BuildMeta(result);
    }

    public string Title => Result.Title;
    public string Subtitle => Result.Subtitle;
    public ResultType Type => Result.Type;
    public string Meta { get; }

    private ImageSource? _icon;
    public ImageSource? Icon
    {
        get => _icon;
        private set
        {
            if (ReferenceEquals(_icon, value)) return;
            _icon = value;
            PropertyChanged?.Invoke(this, IconChanged);
        }
    }

    private bool _iconRequested;

    /// <summary>Load the real icon once. Must be called on the UI (STA) thread.</summary>
    public void EnsureIcon()
    {
        if (_iconRequested) return;
        _iconRequested = true;

        var path = Result.IconPath;
        if (string.IsNullOrEmpty(path)) return; // apps & contacts keep the glyph
        Icon = IconCache.Get(Result.Type, path!);
    }

    private static string BuildMeta(SearchResult r)
    {
        var parts = new List<string>(2);
        if (r.Type != ResultType.Folder && r.Type != ResultType.App &&
            r.Type != ResultType.Contact && r.SizeBytes > 0)
            parts.Add(Format.Size(r.SizeBytes));
        if (r.DateModified > 0)
            parts.Add(Format.Date(r.DateModified));
        return string.Join("  ·  ", parts);
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    private static readonly PropertyChangedEventArgs IconChanged = new(nameof(Icon));
}

/// <summary>UI-thread icon cache. Keys generic types by extension and image/video by full path
/// (so thumbnails stay unique while generic icons are shared), bounded to keep memory in check.</summary>
internal static class IconCache
{
    private const int IconSize = 32;
    private const int MaxEntries = 4000;
    private static readonly Dictionary<string, ImageSource?> Cache = new();

    public static ImageSource? Get(ResultType type, string path)
    {
        string key = type is ResultType.Image or ResultType.Video
            ? "p:" + path
            : "e:" + FileTypes.ExtensionOf(path) + (type == ResultType.Folder ? ":dir" : string.Empty);

        if (Cache.TryGetValue(key, out var cached)) return cached;

        var img = ShellIcon.TryGetImage(path, IconSize);
        if (Cache.Count < MaxEntries) Cache[key] = img;
        return img;
    }
}

/// <summary>Human-readable size and date formatting for result metadata.</summary>
internal static class Format
{
    private static readonly string[] Units = { "B", "KB", "MB", "GB", "TB" };

    public static string Size(long bytes)
    {
        double size = bytes;
        int unit = 0;
        while (size >= 1024 && unit < Units.Length - 1) { size /= 1024; unit++; }
        return unit == 0 ? $"{bytes} {Units[unit]}" : $"{size:0.#} {Units[unit]}";
    }

    public static string Date(long utcTicks)
    {
        try
        {
            var local = new DateTime(utcTicks, DateTimeKind.Utc).ToLocalTime();
            return local.ToString("d MMM yyyy");
        }
        catch { return string.Empty; }
    }
}
