using System.IO;
using System.Text.Json;

namespace NeverSoft.Spotlight.Services;

/// <summary>
/// Lightweight "frecency" store: remembers how often and how recently each result was
/// launched and turns that into a score used as a ranking tiebreaker, so the things you
/// open most float to the top. Persisted as JSON under %AppData%\NeverSoft\Spotlight.
/// All access is best-effort — a corrupt or unwritable store never breaks search.
/// </summary>
public sealed class UsageStore
{
    private sealed class Entry
    {
        public int Count { get; set; }
        public long LastTicks { get; set; }
    }

    private readonly string _path;
    private readonly object _gate = new();
    private Dictionary<string, Entry> _entries = new();

    public UsageStore()
    {
        _path = Path.Combine(AppPaths.DataDir, "usage.json");
        Load();
    }

    /// <summary>A non-negative score; higher = launched more often and more recently.</summary>
    public double Score(string id)
    {
        lock (_gate)
        {
            if (!_entries.TryGetValue(id, out var e)) return 0;
            // Frequency, decayed by age. Half-life ~14 days.
            double ageDays = (DateTime.UtcNow.Ticks - e.LastTicks) / (double)TimeSpan.TicksPerDay;
            double recency = Math.Pow(0.5, Math.Max(0, ageDays) / 14.0);
            return e.Count * (0.4 + 0.6 * recency);
        }
    }

    public void RecordLaunch(string id)
    {
        lock (_gate)
        {
            if (!_entries.TryGetValue(id, out var e))
            {
                e = new Entry();
                _entries[id] = e;
            }
            e.Count++;
            e.LastTicks = DateTime.UtcNow.Ticks;
            Save();
        }
    }

    private void Load()
    {
        try
        {
            if (!File.Exists(_path)) return;
            var json = File.ReadAllText(_path);
            var data = JsonSerializer.Deserialize<Dictionary<string, Entry>>(json);
            if (data != null) _entries = data;
        }
        catch { /* ignore — start fresh */ }
    }

    private void Save()
    {
        try
        {
            AppPaths.EnsureDataDir();
            var json = JsonSerializer.Serialize(_entries);
            File.WriteAllText(_path, json);
        }
        catch { /* ignore */ }
    }
}
