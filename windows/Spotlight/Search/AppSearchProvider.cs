using System.Runtime.InteropServices;
using System.Threading;
using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Search;

/// <summary>One launchable app discovered from the shell AppsFolder.</summary>
internal sealed record AppEntry(string Name, string LaunchId, bool UseAppsFolder, string? IconPath);

/// <summary>
/// Finds installed, launchable apps whose name matches the query — the Windows analog of
/// the Android AppSearchProvider (which queries PackageManager for LAUNCHER activities).
/// We enumerate the shell "AppsFolder" via the Shell.Application COM object, which yields
/// both classic Win32 apps and UWP/Store apps with their display names and launch ids.
/// </summary>
public sealed class AppSearchProvider
{
    public List<SearchResult> Search(string query, int limit, CancellationToken ct)
    {
        var q = query.ToLowerInvariant();
        var output = new List<SearchResult>();

        foreach (var app in AppCatalog.Apps)
        {
            if (ct.IsCancellationRequested || output.Count >= limit) break;
            if (!app.Name.ToLowerInvariant().Contains(q)) continue;

            output.Add(new SearchResult(
                Id: "app:" + app.LaunchId,
                Title: app.Name,
                Subtitle: "App",
                Type: ResultType.App,
                Launch: new LaunchAction.LaunchApp(app.LaunchId, app.UseAppsFolder),
                IconPath: app.IconPath));
        }

        // De-duplicate apps that expose the same display name.
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        return output.Where(r => seen.Add(r.Title)).ToList();
    }
}

/// <summary>
/// Lazily enumerates and caches the AppsFolder once per process. Enumeration touches COM,
/// so it is done on first use and reused for every subsequent keystroke.
/// </summary>
internal static class AppCatalog
{
    private static List<AppEntry>? _apps;
    private static readonly object Gate = new();

    public static IReadOnlyList<AppEntry> Apps
    {
        get
        {
            if (_apps != null) return _apps;
            lock (Gate)
            {
                _apps ??= Enumerate();
                return _apps;
            }
        }
    }

    private static List<AppEntry> Enumerate()
    {
        // Shell.Application and the AppsFolder are apartment-threaded (STA) shell objects. The
        // search runs on a pooled (MTA) thread, so marshal the enumeration onto a dedicated STA
        // thread — on an MTA thread it can throw RPC_E_WRONG_THREAD or silently return nothing.
        if (Thread.CurrentThread.GetApartmentState() == ApartmentState.STA)
            return EnumerateCore();

        List<AppEntry> result = new();
        var thread = new Thread(() => result = EnumerateCore()) { IsBackground = true };
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        return result;
    }

    private static List<AppEntry> EnumerateCore()
    {
        var list = new List<AppEntry>();
        dynamic? shell = null, folder = null, items = null;
        try
        {
            var shellType = Type.GetTypeFromProgID("Shell.Application");
            if (shellType == null) return list;

            shell = Activator.CreateInstance(shellType);
            if (shell == null) return list;

            // "shell:AppsFolder" == the virtual folder of every launchable application.
            folder = shell.NameSpace("shell:AppsFolder");
            if (folder == null) return list;

            items = folder.Items();
            int count = items.Count;
            for (int i = 0; i < count; i++)
            {
                dynamic? item = null;
                try
                {
                    item = items.Item(i);
                    if (item == null) continue;

                    string name = item.Name;
                    if (string.IsNullOrWhiteSpace(name)) continue;

                    string? aumid = null;
                    try { aumid = item.ExtendedProperty("System.AppUserModel.ID") as string; }
                    catch { /* not all entries expose an AUMID */ }

                    string? itemPath = null;
                    try { itemPath = item.Path as string; }
                    catch { /* ignore */ }

                    if (!string.IsNullOrEmpty(aumid))
                    {
                        // Launch via shell:AppsFolder\<AUMID> — works for Win32 and UWP.
                        list.Add(new AppEntry(name, aumid!, UseAppsFolder: true, IconPath: null));
                    }
                    else if (!string.IsNullOrEmpty(itemPath))
                    {
                        // Fall back to shell-executing the resolved path/shortcut.
                        list.Add(new AppEntry(name, itemPath!, UseAppsFolder: false, IconPath: itemPath));
                    }
                }
                catch { /* skip a single bad entry */ }
                finally { Release(item); }
            }
        }
        catch { /* COM unavailable — return whatever we gathered */ }
        finally
        {
            Release(items);
            Release(folder);
            Release(shell);
        }

        return list
            .GroupBy(a => a.LaunchId, StringComparer.OrdinalIgnoreCase)
            .Select(g => g.First())
            .ToList();
    }

    private static void Release(object? comObject)
    {
        try
        {
            if (comObject != null && Marshal.IsComObject(comObject))
                Marshal.FinalReleaseComObject(comObject);
        }
        catch { /* ignore */ }
    }
}
