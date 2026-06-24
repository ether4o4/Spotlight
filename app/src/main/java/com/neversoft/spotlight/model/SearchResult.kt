package com.neversoft.spotlight.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.neversoft.spotlight.R

/** The kind of thing a result represents, mapped to an icon + readable label. */
enum class ResultType(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
) {
    APP(R.drawable.ic_apps, R.string.type_app),
    IMAGE(R.drawable.ic_image, R.string.type_image),
    VIDEO(R.drawable.ic_video, R.string.type_video),
    AUDIO(R.drawable.ic_audio, R.string.type_audio),
    DOCUMENT(R.drawable.ic_document, R.string.type_document),
    ARCHIVE(R.drawable.ic_archive, R.string.type_archive),
    APK(R.drawable.ic_apk, R.string.type_apk),
    FILE(R.drawable.ic_file, R.string.type_file),
    FOLDER(R.drawable.ic_folder, R.string.type_folder),
    CONTACT(R.drawable.ic_contact, R.string.type_contact),
}

/** What happens when a result row is tapped. */
sealed interface LaunchAction {
    data class LaunchApp(val packageName: String) : LaunchAction
    data class OpenFile(val path: String, val mimeType: String) : LaunchAction
    data class OpenUri(val uri: String, val mimeType: String) : LaunchAction
    data class ViewContact(val lookupUri: String) : LaunchAction
}

/** A single row in the results list. */
data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: ResultType,
    val launch: LaunchAction,
    val sizeBytes: Long = 0L,
    val dateModified: Long = 0L,
    /** For APP results: package name used to load the real launcher icon. */
    val appPackage: String? = null,
    /** For IMAGE/VIDEO results: content uri used to load a thumbnail. */
    val thumbnailUri: String? = null,
)
