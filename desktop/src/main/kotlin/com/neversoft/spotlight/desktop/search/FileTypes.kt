package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.ResultType
import java.util.Locale

/** Extension-based classification shared by the file providers (Android parity). */
object FileTypes {

    val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "raw", "dng", "ico")
    val VIDEO = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts")
    val AUDIO = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "amr", "mid")
    val DOCUMENT = setOf(
        "pdf", "doc", "docx", "txt", "rtf", "odt", "md",
        "xls", "xlsx", "csv", "ods",
        "ppt", "pptx", "odp",
        "epub", "mobi", "json", "xml", "html", "htm", "log",
    )
    val ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz")

    /** Extensions treated as plain-text notes for deep (content) search. */
    val NOTE_TEXT = setOf("md", "txt")

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.US)

    fun typeForExtension(ext: String): ResultType = when (ext) {
        in IMAGE -> ResultType.IMAGE
        in VIDEO -> ResultType.VIDEO
        in AUDIO -> ResultType.AUDIO
        in DOCUMENT -> ResultType.DOCUMENT
        in ARCHIVE -> ResultType.ARCHIVE
        else -> ResultType.FILE
    }

    fun typeForName(name: String): ResultType = typeForExtension(extensionOf(name))
}
