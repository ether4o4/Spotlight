package com.neversoft.spotlight.search

import android.webkit.MimeTypeMap
import com.neversoft.spotlight.model.ResultType
import java.util.Locale

/** Extension-based classification shared by the file/media providers. */
object FileTypes {

    val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "raw", "dng")
    val VIDEO = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts")
    val AUDIO = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "amr", "mid")
    val DOCUMENT = setOf(
        "pdf", "doc", "docx", "txt", "rtf", "odt", "md",
        "xls", "xlsx", "csv", "ods",
        "ppt", "pptx", "odp",
        "epub", "mobi", "json", "xml", "html", "htm", "log",
    )
    val ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz")
    val APK = setOf("apk", "apks", "xapk")

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.US)

    fun typeForExtension(ext: String): ResultType = when (ext) {
        in IMAGE -> ResultType.IMAGE
        in VIDEO -> ResultType.VIDEO
        in AUDIO -> ResultType.AUDIO
        in DOCUMENT -> ResultType.DOCUMENT
        in ARCHIVE -> ResultType.ARCHIVE
        in APK -> ResultType.APK
        else -> ResultType.FILE
    }

    fun typeForName(name: String): ResultType = typeForExtension(extensionOf(name))

    fun mimeFor(name: String): String {
        val ext = extensionOf(name)
        if (ext.isEmpty()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
