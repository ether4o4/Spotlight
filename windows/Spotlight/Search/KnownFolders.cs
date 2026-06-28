using System.IO;

namespace NeverSoft.Spotlight.Search;

/// <summary>
/// The set of folders Spotlight searches. The Android app walks external storage; on
/// Windows we walk the user's real content folders (time-boxed identically), which keeps
/// the search fast and relevant without indexing. Order matters: earlier roots are walked
/// first, so the budget is spent on the places content actually lives.
/// </summary>
public static class KnownFolders
{
    private static string Profile =>
        Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);

    private static string? Downloads()
    {
        var p = Profile;
        return string.IsNullOrEmpty(p) ? null : Path.Combine(p, "Downloads");
    }

    private static string? OneDrive() => Environment.GetEnvironmentVariable("OneDrive");

    /// <summary>General roots for the "All / Files / Hidden" walks.</summary>
    public static IReadOnlyList<string> General()
    {
        var list = new List<string?>
        {
            Environment.GetFolderPath(Environment.SpecialFolder.Desktop),
            Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),
            Downloads(),
            Environment.GetFolderPath(Environment.SpecialFolder.MyPictures),
            Environment.GetFolderPath(Environment.SpecialFolder.MyVideos),
            Environment.GetFolderPath(Environment.SpecialFolder.MyMusic),
            OneDrive(),
            // Profile root last: lets dotfiles (.gitconfig, .ssh, ...) surface for the
            // Hidden filter only if the budget hasn't already been spent on real content.
            Profile,
        };
        return Dedupe(list);
    }

    /// <summary>Roots for the media walk — where photos, video and audio actually live.</summary>
    public static IReadOnlyList<string> Media()
    {
        var list = new List<string?>
        {
            Environment.GetFolderPath(Environment.SpecialFolder.MyPictures),
            Environment.GetFolderPath(Environment.SpecialFolder.MyVideos),
            Environment.GetFolderPath(Environment.SpecialFolder.MyMusic),
            Environment.GetFolderPath(Environment.SpecialFolder.Desktop),
            Downloads(),
            OneDrive(),
        };
        return Dedupe(list);
    }

    private static List<string> Dedupe(IEnumerable<string?> roots)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var result = new List<string>();
        foreach (var r in roots)
        {
            if (string.IsNullOrEmpty(r)) continue;
            if (seen.Add(r)) result.Add(r);
        }
        return result;
    }
}
