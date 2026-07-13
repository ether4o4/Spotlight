package com.neversoft.spotlight.desktop.model

/** The kind of thing a result represents, mapped to a glyph + readable label. */
enum class ResultType(val glyph: String, val label: String) {
    APP("🚀", "App"),
    IMAGE("🖼️", "Image"),
    VIDEO("🎬", "Video"),
    AUDIO("🎵", "Audio"),
    DOCUMENT("📄", "Document"),
    ARCHIVE("🗜️", "Archive"),
    FILE("📎", "File"),
    FOLDER("📁", "Folder"),
    NOTE("📝", "Note"),
}

/** What happens when a result row is clicked. */
sealed interface LaunchAction {
    /** Open a file / folder / Start Menu shortcut with its default handler. */
    data class OpenPath(val path: String) : LaunchAction

    /** Bring up the Sticky Notes app (individual notes can't be deep-linked). */
    data object OpenStickyNotes : LaunchAction

    /** Bring up Notepad, which restores its tabs (no per-tab deep link). */
    data object OpenNotepad : LaunchAction
}

/** A single row in the results list. */
data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: ResultType,
    val launch: LaunchAction,
    val dateModified: Long = 0L,
    /**
     * For deep (content) matches only: a short excerpt around the matched text.
     * Null when the result matched by title — the engine uses this to rank
     * title matches above content-only matches.
     */
    val snippet: String? = null,
)
