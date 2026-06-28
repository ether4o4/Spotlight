namespace NeverSoft.Spotlight.Models;

/// <summary>
/// The primary (broad) filter row shown under the search bar. Order intentionally
/// goes from "everything" down to the more specific buckets: Media, Files, Hidden.
/// Ported 1:1 from the Android PrimaryFilter enum.
/// </summary>
public enum PrimaryFilter
{
    All,
    Media,
    Files,
    Hidden,
}

/// <summary>
/// The secondary filter row. Visible only when its parent primary filter is
/// selected, e.g. Media -> Photos / Videos / Audio.
///
/// Windows localization note: the Android "APKs" sub-filter is repurposed here as
/// "Programs" (.exe/.msi/.bat/...) since APKs are an Android concept.
/// </summary>
public enum SubFilter
{
    // Media
    Photos,
    Videos,
    Audio,

    // Files
    Documents,
    Downloads,
    Archives,
    Programs,

    // Hidden
    HiddenFiles,
    HiddenFolders,
}

/// <summary>Labels and parent/child relationships for the two filter tiers.</summary>
public static class FilterMeta
{
    public static string Label(PrimaryFilter p) => p switch
    {
        PrimaryFilter.All => "All",
        PrimaryFilter.Media => "Media",
        PrimaryFilter.Files => "Files",
        PrimaryFilter.Hidden => "Hidden",
        _ => p.ToString(),
    };

    public static string Label(SubFilter s) => s switch
    {
        SubFilter.Photos => "Photos",
        SubFilter.Videos => "Videos",
        SubFilter.Audio => "Audio",
        SubFilter.Documents => "Documents",
        SubFilter.Downloads => "Downloads",
        SubFilter.Archives => "Archives",
        SubFilter.Programs => "Programs",
        SubFilter.HiddenFiles => "Hidden files",
        SubFilter.HiddenFolders => "Hidden folders",
        _ => s.ToString(),
    };

    public static PrimaryFilter Parent(SubFilter s) => s switch
    {
        SubFilter.Photos or SubFilter.Videos or SubFilter.Audio => PrimaryFilter.Media,
        SubFilter.Documents or SubFilter.Downloads or SubFilter.Archives or SubFilter.Programs => PrimaryFilter.Files,
        SubFilter.HiddenFiles or SubFilter.HiddenFolders => PrimaryFilter.Hidden,
        _ => PrimaryFilter.All,
    };

    /// <summary>Sub-filters that narrow this primary filter (empty for All).</summary>
    public static IReadOnlyList<SubFilter> SubFilters(PrimaryFilter p) =>
        Enum.GetValues<SubFilter>().Where(s => Parent(s) == p).ToList();
}

/// <summary>Holds the currently active filter selection. Mirrors the Android FilterSelection.</summary>
public sealed record FilterSelection(PrimaryFilter Primary = PrimaryFilter.All, SubFilter? Sub = null)
{
    /// <summary>Reset the sub-filter whenever the primary changes.</summary>
    public FilterSelection WithPrimary(PrimaryFilter p) => new(p, null);

    /// <summary>Toggle a sub-filter off if it's tapped again.</summary>
    public FilterSelection WithSub(SubFilter? s) => this with { Sub = Sub == s ? null : s };
}
