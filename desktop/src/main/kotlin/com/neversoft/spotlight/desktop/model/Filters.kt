package com.neversoft.spotlight.desktop.model

/**
 * The primary (broad) filter row shown under the search bar — the desktop
 * mirror of the Android app's filter model. Order goes from "everything" down
 * to the more specific buckets, with Notes as the desktop-only deep bucket.
 */
enum class PrimaryFilter(val label: String) {
    ALL("All"),
    APPS("Apps"),
    FILES("Files"),
    NOTES("Notes"),
    HIDDEN("Hidden");

    /** Sub-filters that narrow this primary filter (empty for ALL / APPS). */
    fun subFilters(): List<SubFilter> = SubFilter.entries.filter { it.parent == this }
}

/**
 * The secondary filter row. Visible only when its parent primary filter is
 * selected, e.g. Notes -> Sticky Notes / Notepad tabs / Text & Markdown.
 */
enum class SubFilter(
    val parent: PrimaryFilter,
    val label: String,
) {
    // Files
    DOCUMENTS(PrimaryFilter.FILES, "Documents"),
    MEDIA(PrimaryFilter.FILES, "Media"),
    DOWNLOADS(PrimaryFilter.FILES, "Downloads"),
    ARCHIVES(PrimaryFilter.FILES, "Archives"),
    FOLDERS(PrimaryFilter.FILES, "Folders"),

    // Notes
    STICKY(PrimaryFilter.NOTES, "Sticky Notes"),
    NOTEPAD(PrimaryFilter.NOTES, "Notepad tabs"),
    TEXT(PrimaryFilter.NOTES, "Text & Markdown"),

    // Hidden
    HIDDEN_FILES(PrimaryFilter.HIDDEN, "Hidden files"),
    HIDDEN_FOLDERS(PrimaryFilter.HIDDEN, "Hidden folders"),
}

/** Holds the currently active filter selection. */
data class FilterSelection(
    val primary: PrimaryFilter = PrimaryFilter.ALL,
    val sub: SubFilter? = null,
) {
    /** Reset the sub-filter whenever the primary changes. */
    fun withPrimary(p: PrimaryFilter) = FilterSelection(p, null)

    /** Toggle a sub-filter off if it's tapped again. */
    fun withSub(s: SubFilter?) = copy(sub = if (sub == s) null else s)
}
