using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Ui;

/// <summary>Simple, self-contained vector geometries (24×24, stroked) for each result type —
/// the fallback shown when a real shell icon isn't available, and the source of the chip/search
/// glyphs. Mirrors the iconography of the Android drawables without depending on any icon font.</summary>
public static class Glyphs
{
    public const string Search =
        "M10,10 m-6,0 a6,6 0 1,0 12,0 a6,6 0 1,0 -12,0 M14.5,14.5 L20,20";

    public static string For(ResultType type) => type switch
    {
        ResultType.App =>
            "M3,3 H10 V10 H3 Z M14,3 H21 V10 H14 Z M3,14 H10 V21 H3 Z M14,14 H21 V21 H14 Z",
        ResultType.Image =>
            "M3,5 H21 V19 H3 Z M8,11 m-2,0 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0 M4,18 L9,12 L13,16 L16,13 L20,18",
        ResultType.Video =>
            "M3,6 H21 V18 H3 Z M10,9 L15,12 L10,15 Z",
        ResultType.Audio =>
            "M8,18 m-2,0 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0 M10,18 V6 L18,4 V15 M18,15 m-2,0 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0",
        ResultType.Document =>
            "M6,3 H14 L19,8 V21 H6 Z M14,3 V8 H19 M9,13 H16 M9,16 H16",
        ResultType.Archive =>
            "M5,4 H19 V20 H5 Z M12,4 V20 M10,8 H14 M10,11 H14 M10,14 H14",
        ResultType.Program =>
            "M3,5 H21 V19 H3 Z M3,9 H21 M5.2,7 H6.4 M8,7 H9.2",
        ResultType.Folder =>
            "M3,6 H9 L11,8 H21 V19 H3 Z",
        ResultType.Contact =>
            "M12,8 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 M5,20 C5,15 19,15 19,20",
        _ /* File */ =>
            "M7,3 H14 L18,7 V21 H7 Z M14,3 V7 H18",
    };

    public static string Label(ResultType type) => type switch
    {
        ResultType.App => "App",
        ResultType.Image => "Image",
        ResultType.Video => "Video",
        ResultType.Audio => "Audio",
        ResultType.Document => "Document",
        ResultType.Archive => "Archive",
        ResultType.Program => "Program",
        ResultType.File => "File",
        ResultType.Folder => "Folder",
        ResultType.Contact => "Contact",
        _ => "File",
    };
}
