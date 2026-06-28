using System.IO;

namespace NeverSoft.Spotlight.Services;

/// <summary>Per-user storage location for Spotlight settings and usage data.</summary>
public static class AppPaths
{
    public static string DataDir { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "NeverSoft", "Spotlight");

    public static void EnsureDataDir()
    {
        try { Directory.CreateDirectory(DataDir); }
        catch { /* ignore */ }
    }
}
