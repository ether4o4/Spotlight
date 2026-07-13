package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.SearchResult

/**
 * Provider contracts the engine routes queries through. Kept as small
 * interfaces so the engine is unit-testable with fakes on any OS.
 */
interface AppsSource {
    fun search(query: String, limit: Int): List<SearchResult>
}

/** A source of notes (Sticky Notes, Notepad tabs, text/markdown files, ...). */
interface NotesSource {
    fun search(query: String, limit: Int): List<SearchResult>
}

interface FilesSource {
    fun search(query: String, spec: FileSearchSpec, limit: Int): List<SearchResult>
}

/** Narrowing spec for file searches (Android parity). */
data class FileSearchSpec(
    val includeFiles: Boolean = true,
    val includeFolders: Boolean = false,
    /** When set, only files with one of these (lowercase) extensions match. */
    val extensions: Set<String>? = null,
    val downloadsOnly: Boolean = false,
    val hiddenOnly: Boolean = false,
)

/** Shared text-matching helpers for title + deep (content) matches. */
object Matching {

    /** An empty query is a valid "browse" request: everything matches. */
    fun titleMatches(title: String, query: String): Boolean =
        query.isEmpty() || title.contains(query, ignoreCase = true)

    /**
     * A short, single-line excerpt around the first occurrence of [query] in
     * [content], or null when the content doesn't match (or the query is blank
     * — browsing shouldn't decorate every row with a snippet).
     */
    fun snippetAround(content: String, query: String, radius: Int = 44): String? {
        if (query.isEmpty()) return null
        val idx = content.indexOf(query, ignoreCase = true)
        if (idx < 0) return null
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + query.length + radius).coerceAtMost(content.length)
        val core = content.substring(start, end).replace(WHITESPACE, " ").trim()
        return buildString {
            if (start > 0) append("…")
            append(core)
            if (end < content.length) append("…")
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
