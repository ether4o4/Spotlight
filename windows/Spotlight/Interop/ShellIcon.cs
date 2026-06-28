using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using static NeverSoft.Spotlight.Interop.NativeMethods;

namespace NeverSoft.Spotlight.Interop;

/// <summary>Loads a real Windows shell icon or thumbnail for a path and returns it as a frozen
/// WPF <see cref="ImageSource"/>. Everything is best-effort: any failure returns null and the
/// caller keeps the vector glyph. Call on the UI (STA) thread to stay COM-safe with shell
/// extensions.</summary>
public static class ShellIcon
{
    public static ImageSource? TryGetImage(string path, int size)
        => TryThumbnail(path, size) ?? TryIcon(path, size);

    private static ImageSource? TryThumbnail(string path, int size)
    {
        IShellItemImageFactory? factory = null;
        IntPtr hbitmap = IntPtr.Zero;
        try
        {
            var iid = IID_IShellItemImageFactory;
            SHCreateItemFromParsingName(path, IntPtr.Zero, ref iid, out factory);
            if (factory == null) return null;

            int hr = factory.GetImage(new SIZE(size, size),
                SIIGBF.ResizeToFit | SIIGBF.BiggerSizeOk, out hbitmap);
            if (hr != 0 || hbitmap == IntPtr.Zero) return null;

            var src = Imaging.CreateBitmapSourceFromHBitmap(
                hbitmap, IntPtr.Zero, Int32Rect.Empty, BitmapSizeOptions.FromEmptyOptions());
            src.Freeze();
            return src;
        }
        catch { return null; }
        finally
        {
            if (hbitmap != IntPtr.Zero) DeleteObject(hbitmap);
            if (factory != null) Marshal.ReleaseComObject(factory);
        }
    }

    private static ImageSource? TryIcon(string path, int size)
    {
        var shfi = new SHFILEINFO();
        try
        {
            uint flags = SHGFI_ICON | (size <= 16 ? SHGFI_SMALLICON : SHGFI_LARGEICON);
            SHGetFileInfo(path, 0, ref shfi, (uint)Marshal.SizeOf<SHFILEINFO>(), flags);
            if (shfi.hIcon == IntPtr.Zero) return null;

            var src = Imaging.CreateBitmapSourceFromHIcon(
                shfi.hIcon, Int32Rect.Empty, BitmapSizeOptions.FromEmptyOptions());
            src.Freeze();
            return src;
        }
        catch { return null; }
        finally
        {
            if (shfi.hIcon != IntPtr.Zero) DestroyIcon(shfi.hIcon);
        }
    }
}
