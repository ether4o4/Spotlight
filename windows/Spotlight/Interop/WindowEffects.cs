using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using static NeverSoft.Spotlight.Interop.NativeMethods;

namespace NeverSoft.Spotlight.Interop;

/// <summary>Applies the premium window dressing: a real acrylic blur-behind on Windows 10/11,
/// an immersive dark frame, and rounded corners. Every step is independent and best-effort —
/// if blur is unavailable the window simply falls back to its own translucent rounded card.</summary>
public static class WindowEffects
{
    /// <summary>Enable acrylic blur-behind with a dark tint. <paramref name="tintAbgr"/> is
    /// 0xAABBGGRR.</summary>
    public static void EnableAcrylic(Window window, uint tintAbgr = 0xB00D0B0B)
    {
        try
        {
            var hwnd = new WindowInteropHelper(window).Handle;
            if (hwnd == IntPtr.Zero) return;

            var accent = new AccentPolicy
            {
                AccentState = AccentState.ACCENT_ENABLE_ACRYLICBLURBEHIND,
                AccentFlags = 2, // draw the tint on all edges
                GradientColor = tintAbgr,
            };

            int size = Marshal.SizeOf(accent);
            IntPtr ptr = Marshal.AllocHGlobal(size);
            try
            {
                Marshal.StructureToPtr(accent, ptr, false);
                var data = new WindowCompositionAttributeData
                {
                    Attribute = WCA_ACCENT_POLICY,
                    SizeOfData = size,
                    Data = ptr,
                };
                SetWindowCompositionAttribute(hwnd, ref data);
            }
            finally
            {
                Marshal.FreeHGlobal(ptr);
            }
        }
        catch { /* blur unavailable — translucent card still looks good */ }
    }

    /// <summary>Round the window corners (Win11) — harmless no-op on older builds.</summary>
    public static void RoundCorners(Window window)
    {
        try
        {
            var hwnd = new WindowInteropHelper(window).Handle;
            if (hwnd == IntPtr.Zero) return;
            int pref = DWMWCP_ROUND;
            DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref pref, sizeof(int));
        }
        catch { /* ignore */ }
    }

    /// <summary>Ask DWM for the dark, immersive non-client frame.</summary>
    public static void UseDarkFrame(Window window)
    {
        try
        {
            var hwnd = new WindowInteropHelper(window).Handle;
            if (hwnd == IntPtr.Zero) return;
            int on = 1;
            // Attribute 20 on Win10 20H1+/Win11; older 19041-era builds used 19.
            if (DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref on, sizeof(int)) != 0)
                DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_PRE_20H1, ref on, sizeof(int));
        }
        catch { /* ignore */ }
    }
}
