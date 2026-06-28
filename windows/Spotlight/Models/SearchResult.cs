namespace NeverSoft.Spotlight.Models;

/// <summary>The kind of thing a result represents, mapped to an icon + readable label.</summary>
public enum ResultType
{
    App,
    Image,
    Video,
    Audio,
    Document,
    Archive,
    Program,   // Windows analog of the Android APK type (.exe/.msi/.bat/...)
    File,
    Folder,
    Contact,
}

/// <summary>What happens when a result row is activated.</summary>
public abstract record LaunchAction
{
    /// <summary>Launch an installed app. <paramref name="AppId"/> is an AppUserModelID
    /// (launched via shell:AppsFolder) when <paramref name="UseAppsFolder"/> is true,
    /// otherwise a filesystem path / shortcut that is shell-executed directly.</summary>
    public sealed record LaunchApp(string AppId, bool UseAppsFolder) : LaunchAction;

    /// <summary>Open a file or folder in its default handler / Explorer.</summary>
    public sealed record OpenPath(string Path) : LaunchAction;

    /// <summary>Open a Windows .contact file in the People/Contacts handler.</summary>
    public sealed record OpenContact(string Path) : LaunchAction;
}

/// <summary>A single row in the results list. Mirrors the Android SearchResult.</summary>
public sealed record SearchResult(
    string Id,
    string Title,
    string Subtitle,
    ResultType Type,
    LaunchAction Launch,
    long SizeBytes = 0,
    long DateModified = 0,
    // IconPath: source file/app path, used to load a real shell icon when available.
    string? IconPath = null);
