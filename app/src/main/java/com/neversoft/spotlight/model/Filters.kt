package com.neversoft.spotlight.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.neversoft.spotlight.R

/**
 * The primary (broad) filter row shown under the search bar.
 * Order intentionally goes from "everything" down to the more specific buckets
 * the user called out: Media, Files and Hidden.
 */
enum class PrimaryFilter(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    ALL(R.string.filter_all, R.drawable.ic_apps),
    MEDIA(R.string.filter_media, R.drawable.ic_media),
    FILES(R.string.filter_files, R.drawable.ic_folder),
    HIDDEN(R.string.filter_hidden, R.drawable.ic_hidden);

    /** Sub-filters that narrow this primary filter (empty for ALL). */
    fun subFilters(): List<SubFilter> = SubFilter.entries.filter { it.parent == this }
}

/**
 * The secondary filter row. Visible only when its parent primary filter is
 * selected, e.g. Media -> Photos / Videos / Audio.
 */
enum class SubFilter(
    val parent: PrimaryFilter,
    @StringRes val labelRes: Int,
) {
    // Media
    PHOTOS(PrimaryFilter.MEDIA, R.string.sub_photos),
    VIDEOS(PrimaryFilter.MEDIA, R.string.sub_videos),
    AUDIO(PrimaryFilter.MEDIA, R.string.sub_audio),

    // Files
    DOCUMENTS(PrimaryFilter.FILES, R.string.sub_documents),
    DOWNLOADS(PrimaryFilter.FILES, R.string.sub_downloads),
    ARCHIVES(PrimaryFilter.FILES, R.string.sub_archives),
    APKS(PrimaryFilter.FILES, R.string.sub_apks),

    // Hidden
    HIDDEN_FILES(PrimaryFilter.HIDDEN, R.string.sub_hidden_files),
    HIDDEN_FOLDERS(PrimaryFilter.HIDDEN, R.string.sub_hidden_folders),
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
