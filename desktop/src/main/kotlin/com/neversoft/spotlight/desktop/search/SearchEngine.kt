package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.FilterSelection
import com.neversoft.spotlight.desktop.model.PrimaryFilter
import com.neversoft.spotlight.desktop.model.ResultType
import com.neversoft.spotlight.desktop.model.SearchResult
import com.neversoft.spotlight.desktop.model.SubFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Aggregates every provider into one PC-wide search, routed by the active
 * filter selection. Runs on [Dispatchers.IO] and is fully cancellable so each
 * new keystroke can abandon the previous query. Mirrors the Android engine,
 * with one addition: deep matches (query found *inside* a note, not in its
 * title) rank below title matches via [SearchResult.snippet].
 */
class SearchEngine(
    private val apps: AppsSource,
    private val files: FilesSource,
    private val sticky: NotesSource,
    private val notepad: NotesSource,
    private val textNotes: NotesSource,
) {

    suspend fun search(
        query: String,
        selection: FilterSelection,
        limit: Int = DEFAULT_LIMIT,
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // An empty query is a valid "browse" request: every provider treats it as
        // match-all, so the list populates from the active filter before the user
        // types anything, and refreshes as filters change.
        val q = query.trim()

        val results = ArrayList<SearchResult>()

        when (selection.primary) {
            PrimaryFilter.ALL -> {
                results += apps.search(q, 40)
                ensureActive()
                results += sticky.search(q, 40)
                results += notepad.search(q, 20)
                results += textNotes.search(q, 60)
                ensureActive()
                results += files.search(
                    q,
                    FileSearchSpec(includeFiles = true, includeFolders = true),
                    200,
                )
            }

            PrimaryFilter.APPS -> {
                results += apps.search(q, limit)
            }

            PrimaryFilter.FILES -> {
                val spec = when (selection.sub) {
                    SubFilter.DOCUMENTS -> FileSearchSpec(extensions = FileTypes.DOCUMENT)
                    SubFilter.MEDIA -> FileSearchSpec(
                        extensions = FileTypes.IMAGE + FileTypes.VIDEO + FileTypes.AUDIO,
                    )
                    SubFilter.DOWNLOADS -> FileSearchSpec(downloadsOnly = true)
                    SubFilter.ARCHIVES -> FileSearchSpec(extensions = FileTypes.ARCHIVE)
                    SubFilter.FOLDERS -> FileSearchSpec(includeFiles = false, includeFolders = true)
                    else -> FileSearchSpec(includeFiles = true, includeFolders = true)
                }
                results += files.search(q, spec, limit)
            }

            PrimaryFilter.NOTES -> {
                when (selection.sub) {
                    SubFilter.STICKY -> results += sticky.search(q, limit)
                    SubFilter.NOTEPAD -> results += notepad.search(q, limit)
                    SubFilter.TEXT -> results += textNotes.search(q, limit)
                    else -> {
                        results += sticky.search(q, limit)
                        results += notepad.search(q, limit)
                        results += textNotes.search(q, limit)
                    }
                }
            }

            PrimaryFilter.HIDDEN -> {
                val spec = when (selection.sub) {
                    SubFilter.HIDDEN_FOLDERS ->
                        FileSearchSpec(includeFiles = false, includeFolders = true, hiddenOnly = true)
                    SubFilter.HIDDEN_FILES ->
                        FileSearchSpec(includeFiles = true, includeFolders = false, hiddenOnly = true)
                    else ->
                        FileSearchSpec(includeFiles = true, includeFolders = true, hiddenOnly = true)
                }
                results += files.search(q, spec, limit)
            }
        }

        ensureActive()
        rankAndDedupe(results, q, limit)
    }

    private fun rankAndDedupe(list: List<SearchResult>, query: String, limit: Int): List<SearchResult> {
        val lower = query.lowercase(Locale.getDefault())
        return list.distinctBy { it.id }
            .sortedWith(
                compareBy(
                    { matchScore(it, lower) },
                    { typeOrder(it.type) },
                    { -it.dateModified },
                ),
            )
            .take(limit)
    }

    /**
     * Lower is better: exact match, then prefix, then substring — and below all
     * title matches, deep matches where the query was only found inside the
     * note's content (marked by the provider with a non-null snippet).
     */
    private fun matchScore(result: SearchResult, lowerQuery: String): Int {
        val t = result.title.lowercase(Locale.getDefault())
        return when {
            t == lowerQuery -> 0
            t.startsWith(lowerQuery) -> 1
            t.contains(lowerQuery) -> 2
            else -> 3
        }
    }

    /** Surface apps & notes above raw files in mixed (ALL) results. */
    private fun typeOrder(type: ResultType): Int = when (type) {
        ResultType.APP -> 0
        ResultType.NOTE -> 1
        ResultType.IMAGE, ResultType.VIDEO, ResultType.AUDIO -> 2
        ResultType.DOCUMENT, ResultType.ARCHIVE -> 3
        ResultType.FOLDER -> 4
        ResultType.FILE -> 5
    }

    companion object {
        const val DEFAULT_LIMIT = 400
    }
}
