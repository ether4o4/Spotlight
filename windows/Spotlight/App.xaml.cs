using System.Threading;
using System.Windows;
using NeverSoft.Spotlight.Search;
using NeverSoft.Spotlight.Services;
using Forms = System.Windows.Forms;
using Drawing = System.Drawing;

namespace NeverSoft.Spotlight;

/// <summary>
/// Application entry point. Spotlight is a background app: it lives in the tray, builds its file
/// index off the UI thread, and is summoned by a global hotkey (Alt+Space by default). A second
/// launch just re-shows the running instance.
/// </summary>
public partial class App : Application
{
    private const string MutexName = "NeverSoft.Spotlight.SingleInstance";
    private const string ShowEventName = "NeverSoft.Spotlight.Show";

    private Mutex? _mutex;
    private EventWaitHandle? _showEvent;
    private Forms.NotifyIcon? _tray;
    private MainWindow? _window;

    private Settings _settings = new();
    private UsageStore? _usage;
    private FileIndex? _index;
    private SearchEngine? _engine;

    protected override void OnStartup(StartupEventArgs e)
    {
        _mutex = new Mutex(initiallyOwned: true, MutexName, out bool createdNew);
        _showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);

        if (!createdNew)
        {
            // Another instance owns the tray — wake it and bow out.
            _showEvent.Set();
            Shutdown();
            return;
        }

        base.OnStartup(e);
        ShutdownMode = ShutdownMode.OnExplicitShutdown;

        _settings = Settings.Load();
        _usage = new UsageStore();
        _index = new FileIndex();
        _index.BuildInBackground();
        _engine = new SearchEngine(_usage, _index);

        _window = new MainWindow(_engine, _usage, _index, _settings);

        SetupTray();
        StartShowListener();

        _window.ShowSpotlight();
    }

    private void SetupTray()
    {
        _tray = new Forms.NotifyIcon
        {
            Icon = Drawing.SystemIcons.Application,
            Visible = true,
            Text = "Spotlight — NeverSoft Services",
        };

        var menu = new Forms.ContextMenuStrip();

        var open = new Forms.ToolStripMenuItem("Open Spotlight  (Alt+Space)");
        open.Click += (_, _) => _window?.ShowSpotlight();

        var startup = new Forms.ToolStripMenuItem("Launch at startup")
        {
            CheckOnClick = true,
            Checked = StartupRegistration.IsEnabled(),
        };
        startup.CheckedChanged += (_, _) =>
        {
            StartupRegistration.Set(startup.Checked);
            _settings.LaunchAtStartup = startup.Checked;
            _settings.Save();
        };

        var exit = new Forms.ToolStripMenuItem("Exit");
        exit.Click += (_, _) => ExitApp();

        menu.Items.Add(open);
        menu.Items.Add(startup);
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add(exit);

        _tray.ContextMenuStrip = menu;
        _tray.DoubleClick += (_, _) => _window?.ShowSpotlight();
    }

    /// <summary>Background listener: a second launch sets the named event; we re-show the window.</summary>
    private void StartShowListener()
    {
        var thread = new Thread(() =>
        {
            while (true)
            {
                try
                {
                    if (_showEvent!.WaitOne())
                        Dispatcher.Invoke(() => _window?.ShowSpotlight());
                }
                catch { break; } // handle disposed on shutdown
            }
        })
        {
            IsBackground = true,
            Name = "Spotlight.ShowListener",
        };
        thread.Start();
    }

    private void ExitApp()
    {
        if (_tray != null)
        {
            _tray.Visible = false;
            _tray.Dispose();
            _tray = null;
        }
        _window?.AllowCloseAndClose();
        Shutdown();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _tray?.Dispose();
        try { _showEvent?.Dispose(); } catch { /* ignore */ }
        try { _mutex?.Dispose(); } catch { /* ignore */ }
        base.OnExit(e);
    }
}
