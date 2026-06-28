using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using NeverSoft.Spotlight.Interop;
using NeverSoft.Spotlight.Models;
using NeverSoft.Spotlight.Search;
using NeverSoft.Spotlight.Services;
using NeverSoft.Spotlight.Ui;
using static NeverSoft.Spotlight.Interop.NativeMethods;

namespace NeverSoft.Spotlight;

/// <summary>The Spotlight overlay: black/red search card with two-tier filter chips and a live,
/// instantly-narrowing results list. Summoned by the global hotkey, hidden on focus loss.</summary>
public partial class MainWindow : Window
{
    private const int HotkeyId = 0x4D31;

    private readonly SearchEngine _engine;
    private readonly UsageStore _usage;
    private readonly FileIndex _index;
    private readonly Settings _settings;

    private readonly ObservableCollection<ResultRowViewModel> _rows = new();
    private readonly Dictionary<PrimaryFilter, Button> _primaryButtons = new();
    private readonly Dictionary<SubFilter, Button> _subButtons = new();
    private readonly DispatcherTimer _debounce;

    private FilterSelection _selection = new();
    private CancellationTokenSource? _cts;
    private int _iconGeneration;

    private HwndSource? _source;
    private IntPtr _hwnd;
    private bool _allowClose;
    private long _suppressDeactivateUntil;

    public MainWindow(SearchEngine engine, UsageStore usage, FileIndex index, Settings settings)
    {
        InitializeComponent();

        _engine = engine;
        _usage = usage;
        _index = index;
        _settings = settings;

        ResultsList.ItemsSource = _rows;
        SearchGlyph.Data = Geometry.Parse(Glyphs.Search);

        _debounce = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(110) };
        _debounce.Tick += (_, _) => { _debounce.Stop(); RunSearch(); };

        SearchBox.TextChanged += OnTextChanged;

        BuildPrimaryChips();
        UpdateSecondaryChips();
        UpdateChipStyles();
    }

    // ===== Window / hotkey lifecycle =====================================

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);

        _hwnd = new WindowInteropHelper(this).Handle;
        _source = HwndSource.FromHwnd(_hwnd);
        _source?.AddHook(WndProc);

        RegisterHotkey();

        WindowEffects.UseDarkFrame(this);
        WindowEffects.RoundCorners(this);
        WindowEffects.EnableAcrylic(this);
    }

    private void RegisterHotkey()
    {
        try
        {
            uint mods = MOD_NOREPEAT;
            if (_settings.HotkeyAlt) mods |= MOD_ALT;
            if (_settings.HotkeyCtrl) mods |= MOD_CONTROL;
            if (_settings.HotkeyShift) mods |= MOD_SHIFT;
            if (_settings.HotkeyWin) mods |= MOD_WIN;
            RegisterHotKey(_hwnd, HotkeyId, mods, (uint)_settings.HotkeyVirtualKey);
        }
        catch { /* hotkey unavailable — tray still opens the window */ }
    }

    private IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WM_HOTKEY && wParam.ToInt32() == HotkeyId)
        {
            ToggleSpotlight();
            handled = true;
        }
        return IntPtr.Zero;
    }

    public void ToggleSpotlight()
    {
        if (IsVisible && IsActive) HideSpotlight();
        else ShowSpotlight();
    }

    public void ShowSpotlight()
    {
        _index.EnsureFresh();
        PositionWindow();

        _suppressDeactivateUntil = Environment.TickCount64 + 300;

        if (!IsVisible) Show();
        Topmost = true;
        Activate();

        SearchBox.Focus();
        SearchBox.SelectAll();

        PlayShowAnimation();
        RunSearch();
    }

    public void HideSpotlight()
    {
        _cts?.Cancel();
        Hide();
    }

    /// <summary>Used by the tray "Exit" command to actually close (OnClosing otherwise hides).</summary>
    public void AllowCloseAndClose()
    {
        _allowClose = true;
        try { UnregisterHotKey(_hwnd, HotkeyId); } catch { /* ignore */ }
        _source?.RemoveHook(WndProc);
        Close();
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!_allowClose)
        {
            e.Cancel = true;
            HideSpotlight();
            return;
        }
        base.OnClosing(e);
    }

    protected override void OnDeactivated(EventArgs e)
    {
        base.OnDeactivated(e);
        if (_settings.HideOnFocusLost && IsVisible && !_allowClose &&
            Environment.TickCount64 >= _suppressDeactivateUntil)
        {
            HideSpotlight();
        }
    }

    private void PositionWindow()
    {
        var wa = SystemParameters.WorkArea;
        Left = wa.Left + (wa.Width - Width) / 2;
        Top = wa.Top + wa.Height * 0.12;
    }

    private void PlayShowAnimation()
    {
        var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(140))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
        };
        BeginAnimation(OpacityProperty, fade);

        if (Content is FrameworkElement fe)
        {
            var translate = new TranslateTransform(0, 12);
            fe.RenderTransform = translate;
            var slide = new DoubleAnimation(12, 0, TimeSpan.FromMilliseconds(190))
            {
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
            };
            translate.BeginAnimation(TranslateTransform.YProperty, slide);
        }
    }

    // ===== Search =========================================================

    private void OnTextChanged(object sender, TextChangedEventArgs e)
    {
        bool empty = string.IsNullOrEmpty(SearchBox.Text);
        Placeholder.Visibility = empty ? Visibility.Visible : Visibility.Collapsed;
        ClearButton.Visibility = empty ? Visibility.Collapsed : Visibility.Visible;

        _debounce.Stop();
        _debounce.Start();
    }

    private async void RunSearch()
    {
        var query = SearchBox.Text ?? string.Empty;
        var selection = _selection;

        _cts?.Cancel();
        var cts = new CancellationTokenSource();
        _cts = cts;

        StatusText.Text = "Searching…";

        try
        {
            var results = await _engine.SearchAsync(query, selection, ct: cts.Token);
            if (cts.Token.IsCancellationRequested) return;
            Populate(results, query);
        }
        catch (OperationCanceledException) { /* superseded by a newer keystroke */ }
        catch (Exception)
        {
            if (ReferenceEquals(_cts, cts)) StatusText.Text = string.Empty;
        }
    }

    private void Populate(IReadOnlyList<SearchResult> results, string query)
    {
        _rows.Clear();
        foreach (var r in results) _rows.Add(new ResultRowViewModel(r));

        if (_rows.Count > 0) ResultsList.SelectedIndex = 0;

        StatusText.Text = _rows.Count == 0
            ? (string.IsNullOrWhiteSpace(query)
                ? "No items to show here yet"
                : $"No results for “{query.Trim()}”")
            : $"{_rows.Count} result{(_rows.Count == 1 ? "" : "s")}";

        _iconGeneration++;
        ScheduleIconLoads(_iconGeneration);
    }

    /// <summary>Lazily load real shell icons for the first rows, on the UI thread at background
    /// priority, abandoned when a newer result set arrives.</summary>
    private void ScheduleIconLoads(int generation)
    {
        int max = Math.Min(_rows.Count, 80);
        for (int i = 0; i < max; i++)
        {
            var row = _rows[i];
            Dispatcher.BeginInvoke(
                new Action(() => { if (generation == _iconGeneration) row.EnsureIcon(); }),
                DispatcherPriority.Background);
        }
    }

    // ===== Filter chips ===================================================

    private void BuildPrimaryChips()
    {
        foreach (var p in Enum.GetValues<PrimaryFilter>())
        {
            var btn = MakeChip(FilterMeta.Label(p));
            btn.Tag = p;
            btn.Click += (s, _) => SelectPrimary((PrimaryFilter)((Button)s).Tag);
            PrimaryChips.Children.Add(btn);
            _primaryButtons[p] = btn;
        }
    }

    private void UpdateSecondaryChips()
    {
        SecondaryChips.Children.Clear();
        _subButtons.Clear();

        var subs = FilterMeta.SubFilters(_selection.Primary);
        if (subs.Count == 0)
        {
            SecondaryChips.Visibility = Visibility.Collapsed;
            return;
        }

        SecondaryChips.Visibility = Visibility.Visible;
        foreach (var s in subs)
        {
            var btn = MakeChip(FilterMeta.Label(s));
            btn.Tag = s;
            btn.Click += (sender, _) => OnSubClick((SubFilter)((Button)sender).Tag);
            SecondaryChips.Children.Add(btn);
            _subButtons[s] = btn;
        }
    }

    private void UpdateChipStyles()
    {
        var selected = (Style)FindResource("ChipButtonSelected");
        var normal = (Style)FindResource("ChipButton");

        foreach (var (filter, btn) in _primaryButtons)
            btn.Style = filter == _selection.Primary ? selected : normal;

        foreach (var (sub, btn) in _subButtons)
            btn.Style = _selection.Sub == sub ? selected : normal;
    }

    private void SelectPrimary(PrimaryFilter p)
    {
        _selection = _selection.WithPrimary(p);
        UpdateSecondaryChips();
        UpdateChipStyles();
        RunSearch();
    }

    private void OnSubClick(SubFilter s)
    {
        _selection = _selection.WithSub(s);
        UpdateChipStyles();
        RunSearch();
    }

    private Button MakeChip(string text) => new()
    {
        Content = text,
        Style = (Style)FindResource("ChipButton"),
        Focusable = false,
    };

    // ===== Activation =====================================================

    private void LaunchSelected()
    {
        var row = ResultsList.SelectedItem as ResultRowViewModel
                  ?? (_rows.Count > 0 ? _rows[0] : null);
        if (row == null) return;

        try
        {
            _usage.RecordLaunch(row.Result.Id);
            Launcher.Launch(row.Result.Launch);
        }
        catch { /* ignore launch failure */ }

        HideSpotlight();
    }

    private void RevealSelected()
    {
        if (ResultsList.SelectedItem is not ResultRowViewModel row) return;
        Launcher.RevealInExplorer(row.Result.Launch);
        HideSpotlight();
    }

    private void CopySelectedPath()
    {
        if (ResultsList.SelectedItem is not ResultRowViewModel row) return;
        var path = Launcher.PathOf(row.Result.Launch);
        if (string.IsNullOrEmpty(path)) return;
        try { Clipboard.SetText(path); StatusText.Text = "Copied path"; } catch { /* ignore */ }
    }

    private void MoveSelection(int delta)
    {
        if (_rows.Count == 0) return;
        int i = ResultsList.SelectedIndex + delta;
        i = Math.Max(0, Math.Min(_rows.Count - 1, i));
        ResultsList.SelectedIndex = i;
        if (ResultsList.SelectedItem != null)
            ResultsList.ScrollIntoView(ResultsList.SelectedItem);
    }

    // ===== Input handlers =================================================

    protected override void OnPreviewKeyDown(KeyEventArgs e)
    {
        var mods = Keyboard.Modifiers;

        switch (e.Key)
        {
            case Key.Escape:
                HideSpotlight();
                e.Handled = true;
                return;

            case Key.Enter:
                if (mods.HasFlag(ModifierKeys.Control)) RevealSelected();
                else LaunchSelected();
                e.Handled = true;
                return;

            case Key.Down:
                MoveSelection(1);
                e.Handled = true;
                return;

            case Key.Up:
                MoveSelection(-1);
                e.Handled = true;
                return;

            case Key.C when mods.HasFlag(ModifierKeys.Control) && mods.HasFlag(ModifierKeys.Shift):
                CopySelectedPath();
                e.Handled = true;
                return;

            case Key.System when mods.HasFlag(ModifierKeys.Alt):
                PrimaryFilter? pf = e.SystemKey switch
                {
                    Key.D1 => PrimaryFilter.All,
                    Key.D2 => PrimaryFilter.Media,
                    Key.D3 => PrimaryFilter.Files,
                    Key.D4 => PrimaryFilter.Hidden,
                    _ => null,
                };
                if (pf.HasValue)
                {
                    SelectPrimary(pf.Value);
                    e.Handled = true;
                }
                return;
        }

        base.OnPreviewKeyDown(e);
    }

    private void ResultsList_MouseDoubleClick(object sender, MouseButtonEventArgs e) => LaunchSelected();

    private void ClearButton_Click(object sender, RoutedEventArgs e)
    {
        SearchBox.Clear();
        SearchBox.Focus();
    }
}
