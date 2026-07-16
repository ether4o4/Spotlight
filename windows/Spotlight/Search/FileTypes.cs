using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Search;

/// <summary>Extension-based classification shared by the file/media providers.
/// Ported from the Android FileTypes, with the Android "APK" bucket replaced by a
/// Windows "Program" bucket (.exe/.msi/.bat/...).</summary>
public static class FileTypes
{
    public static readonly IReadOnlySet<string> Image = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        { "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "raw", "dng", "tif", "tiff", "ico", "avif" };

    public static readonly IReadOnlySet<string> Video = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        { "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "mpg", "mpeg", "m2ts" };

    public static readonly IReadOnlySet<string> Audio = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        { "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "amr", "mid", "aiff", "alac" };

    public static readonly IReadOnlySet<string> Document = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
    {
        "pdf", "doc", "docx", "txt", "rtf", "odt", "md",
        "xls", "xlsx", "csv", "ods",
        "ppt", "pptx", "odp",
        "epub", "mobi", "json", "xml", "html", "htm", "log",
    };

    public static readonly IReadOnlySet<string> Archive = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        { "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz", "cab" };

    /// <summary>Windows executables / installers (the localized analog of Android APKs).</summary>
    public static readonly IReadOnlySet<string> Program = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        { "exe", "msi", "msix", "appx", "bat", "cmd", "ps1", "com", "scr", "vbs", "lnk" };

    public static string ExtensionOf(string name)
    {
        int dot = name.LastIndexOf('.');
        if (dot < 0 || dot == name.Length - 1) return string.Empty;
        return name[(dot + 1)..].ToLowerInvariant();
    }

    public static ResultType TypeForExtension(string ext)
    {
        if (Image.Contains(ext)) return ResultType.Image;
        if (Video.Contains(ext)) return ResultType.Video;
        if (Audio.Contains(ext)) return ResultType.Audio;
        if (Document.Contains(ext)) return ResultType.Document;
        if (Archive.Contains(ext)) return ResultType.Archive;
        if (Program.Contains(ext)) return ResultType.Program;
        return ResultType.File;
    }

    public static ResultType TypeForName(string name) => TypeForExtension(ExtensionOf(name));
}
