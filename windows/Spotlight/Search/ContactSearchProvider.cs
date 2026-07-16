using System.IO;
using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Search;

/// <summary>
/// Finds Windows contacts. The Android app reads ContactsContract; Windows stores contacts
/// as .contact files under %USERPROFILE%\Contacts, so we match on those. Returns nothing
/// when the user has no Contacts folder (common) — exactly like the Android provider when
/// no contacts permission/entries exist.
/// </summary>
public sealed class ContactSearchProvider
{
    public List<SearchResult> Search(string query, int limit, CancellationToken ct)
    {
        var output = new List<SearchResult>();
        var profile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        if (string.IsNullOrEmpty(profile)) return output;

        var dir = Path.Combine(profile, "Contacts");
        if (!Directory.Exists(dir)) return output;

        var q = query.ToLowerInvariant();
        string[] files;
        try { files = Directory.GetFiles(dir, "*.contact", SearchOption.TopDirectoryOnly); }
        catch { return output; }

        foreach (var f in files)
        {
            if (ct.IsCancellationRequested || output.Count >= limit) break;
            var name = Path.GetFileNameWithoutExtension(f);
            if (!name.ToLowerInvariant().Contains(q)) continue;

            long ticks = 0;
            try { ticks = File.GetLastWriteTimeUtc(f).Ticks; } catch { /* ignore */ }

            output.Add(new SearchResult(
                Id: "contact:" + f,
                Title: name,
                Subtitle: "Contact",
                Type: ResultType.Contact,
                Launch: new LaunchAction.OpenContact(f),
                DateModified: ticks));
        }

        return output;
    }
}
