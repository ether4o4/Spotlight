using System.IO;
using System.Text.Json;

namespace NeverSoft.Spotlight.Services;

/// <summary>User-tunable settings, persisted as JSON. All fields have sensible defaults so a
/// missing or corrupt file just yields the stock experience.</summary>
public sealed class Settings
{
    /// <summary>Accent color (NeverSoft red by default), as a #AARRGGBB hex string.</summary>
    public string AccentColor { get; set; } = "#FFE5202A";

    /// <summary>Global show/hide hotkey modifiers (Alt by default).</summary>
    public bool HotkeyAlt { get; set; } = true;
    public bool HotkeyCtrl { get; set; } = false;
    public bool HotkeyShift { get; set; } = false;
    public bool HotkeyWin { get; set; } = false;

    /// <summary>Virtual-key code for the hotkey (VK_SPACE = 0x20 by default).</summary>
    public int HotkeyVirtualKey { get; set; } = 0x20;

    /// <summary>Hide the window automatically when it loses focus (true Spotlight feel).</summary>
    public bool HideOnFocusLost { get; set; } = true;

    /// <summary>Preferred window backdrop: "Mica", "Acrylic", or "Solid".</summary>
    public string Backdrop { get; set; } = "Mica";

    /// <summary>Launch Spotlight when you sign in to Windows.</summary>
    public bool LaunchAtStartup { get; set; } = false;

    // --- persistence -------------------------------------------------------

    private static string FilePath => Path.Combine(AppPaths.DataDir, "settings.json");

    public static Settings Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                var json = File.ReadAllText(FilePath);
                var s = JsonSerializer.Deserialize<Settings>(json);
                if (s != null) return s;
            }
        }
        catch { /* ignore — fall back to defaults */ }
        return new Settings();
    }

    public void Save()
    {
        try
        {
            AppPaths.EnsureDataDir();
            var json = JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(FilePath, json);
        }
        catch { /* ignore */ }
    }
}
