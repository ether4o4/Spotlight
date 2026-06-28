using System.Diagnostics;
using System.IO;
using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Search;

/// <summary>Executes a result's <see cref="LaunchAction"/> — the Windows analog of the
/// Android launch intents (apps launch, files/folders open in their default handler).</summary>
public static class Launcher
{
    public static void Launch(LaunchAction action)
    {
        switch (action)
        {
            case LaunchAction.LaunchApp app when app.UseAppsFolder:
                Start("explorer.exe", "shell:AppsFolder\\" + app.AppId);
                break;
            case LaunchAction.LaunchApp app:
                Shell(app.AppId);
                break;
            case LaunchAction.OpenPath p:
                Shell(p.Path);
                break;
            case LaunchAction.OpenContact c:
                Shell(c.Path);
                break;
        }
    }

    /// <summary>Reveal a file or folder in File Explorer (Ctrl+Enter action).</summary>
    public static void RevealInExplorer(LaunchAction action)
    {
        string? path = action switch
        {
            LaunchAction.OpenPath p => p.Path,
            LaunchAction.OpenContact c => c.Path,
            _ => null,
        };
        if (string.IsNullOrEmpty(path)) return;

        try
        {
            if (Directory.Exists(path))
                Start("explorer.exe", "\"" + path + "\"");
            else if (File.Exists(path))
                Start("explorer.exe", "/select,\"" + path + "\"");
        }
        catch { /* ignore */ }
    }

    /// <summary>The filesystem path behind a result, if any (used for "copy path").</summary>
    public static string? PathOf(LaunchAction action) => action switch
    {
        LaunchAction.OpenPath p => p.Path,
        LaunchAction.OpenContact c => c.Path,
        LaunchAction.LaunchApp app when !app.UseAppsFolder => app.AppId,
        _ => null,
    };

    private static void Shell(string path)
    {
        try { Start(path, null, useShell: true); }
        catch { /* ignore launch failures */ }
    }

    private static void Start(string fileName, string? arguments, bool useShell = true)
    {
        var psi = new ProcessStartInfo
        {
            FileName = fileName,
            UseShellExecute = useShell,
        };
        if (arguments != null) psi.Arguments = arguments;
        Process.Start(psi);
    }
}
