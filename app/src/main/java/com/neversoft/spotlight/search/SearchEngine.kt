package com.neversoft.spotlight.search

import android.content.Context
import com.neversoft.spotlight.model.FilterSelection
import com.neversoft.spotlight.model.PrimaryFilter
import com.neversoft.spotlight.model.ResultType
import com.neversoft.spotlight.model.SearchResult
import com.neversoft.spotlight.model.StorageScope
import com.neversoft.spotlight.model.SubFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Aggregates every provider into one device-wide search, routed by the active
 * filter selection. Runs on [Dispatchers.IO] and is fully cancellable so each
 * new keystroke can abandon the previous query.
 */
class SearchEngine(context: Context) {

    private val apps = AppSearchProvider(context)
    private val media = MediaSearchProvider(context)
    private val files = FileSearchProvider(context)
    private val contacts = ContactSearchProvider(context)

    fun hasFullFileAccess(): Boolean = files.hasFullAccess()

    suspend fun search(
        query: String,
        selection: FilterSelection,
        scope: StorageScope = StorageScope.ALL,
        limit: Int = DEFAULT_LIMIT,
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()

        val active = { isActive }
        val deadline = System.currentTimeMillis() + WALK_BUDGET_MS
        val results = ArrayList<SearchResult>()

        when (selection.primary) {
            PrimaryFilter.ALL -> {
                results += apps.search(q, 40)
                ensureActive()
                results += contacts.search(q, 30, active)
                ensureActive()
                results += media.search(
                    q, setOf(ResultType.IMAGE, ResultType.VIDEO, ResultType.AUDIO), 90, active, scope,
                )
                ensureActive()
                results += files.search(
                    q,
                    FileSearchSpec(includeFiles = true, includeFolders = true),
                    160, deadline, active, scope,
                )
            }

            PrimaryFilter.MEDIA -> {
                val types = when (selection.sub) {
                    SubFilter.PHOTOS -> setOf(ResultType.IMAGE)
                    SubFilter.VIDEOS -> setOf(ResultType.VIDEO)
                    SubFilter.AUDIO -> setOf(ResultType.AUDIO)
                    else -> setOf(ResultType.IMAGE, ResultType.VIDEO, ResultType.AUDIO)
                }
                results += media.search(q, types, limit, active, scope)
            }

            PrimaryFilter.FILES -> {
                val spec = when (selection.sub) {
                    SubFilter.DOCUMENTS -> FileSearchSpec(extensions = FileTypes.DOCUMENT)
                    SubFilter.DOWNLOADS -> FileSearchSpec(downloadsOnly = true)
                    SubFilter.ARCHIVES -> FileSearchSpec(extensions = FileTypes.ARCHIVE)
                    SubFilter.APKS -> FileSearchSpec(extensions = FileTypes.APK)
                    else -> FileSearchSpec(includeFiles = true, includeFolders = true)
                }
                results += files.search(q, spec, limit, deadline, active, scope)
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
                results += files.search(q, spec, limit, deadline, active, scope)
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
                    { matchScore(it.title, lower) },
                    { typeOrder(it.type) },
                    { -it.dateModified },
                ),
            )
            .take(limit)
    }

    /** Lower is better: exact match first, then prefix, then substring. */
    private fun matchScore(title: String, lowerQuery: String): Int {
        val t = title.lowercase(Locale.getDefault())
        return when {
            t == lowerQuery -> 0
            t.startsWith(lowerQuery) -> 1
            else -> 2
        }
    }

    /** Surface apps & contacts above raw files in mixed (ALL) results. */
    private fun typeOrder(type: ResultType): Int = when (type) {
        ResultType.APP -> 0
        ResultType.CONTACT -> 1
        ResultType.IMAGE, ResultType.VIDEO, ResultType.AUDIO -> 2
        ResultType.DOCUMENT, ResultType.APK, ResultType.ARCHIVE -> 3
        ResultType.FOLDER -> 4
        ResultType.FILE -> 5
    }

    companion object {
        const val DEFAULT_LIMIT = 400
        const val WALK_BUDGET_MS = 2500L
    }
}
