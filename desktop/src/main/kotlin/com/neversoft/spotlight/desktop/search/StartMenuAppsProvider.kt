package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.LaunchAction
import com.neversoft.spotlight.desktop.model.ResultType
import com.neversoft.spotlight.desktop.model.SearchResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.streams.asSequence

/**
 * Installed applications, discovered the way the Windows Start Menu itself
 * does: shortcuts (.lnk / .url / .appref-ms) under the all-users and per-user
 * Start Menu folders. Launching opens the shortcut, which resolves the real
 * target via the shell.
 */
class StartMenuAppsProvider(private val roots: List<Path>) : AppsSource {

    private data class AppEntry(val name: String, val shortcut: Path, val dateModified: Long)

    @Volatile
    private var cache: List<AppEntry>? = null

    fun refresh() {
        cache = null
    }

    override fun search(query: String, limit: Int): List<SearchResult> {
        val entries = cache ?: loadEntries().also { cache = it }
        return entries.asSequence()
            .filter { Matching.titleMatches(it.name, query) }
            .take(limit)
            .map {
                SearchResult(
                    id = "app:${it.shortcut}",
                    title = it.name,
                    subtitle = "App",
                    type = ResultType.APP,
                    launch = LaunchAction.OpenPath(it.shortcut.toString()),
                    dateModified = it.dateModified,
                )
            }
            .toList()
    }

    private fun loadEntries(): List<AppEntry> {
        val seen = HashMap<String, AppEntry>()
        for (root in roots) {
            if (!Files.isDirectory(root)) continue
            runCatching {
                Files.walk(root).use { stream ->
                    stream.asSequence()
                        .filter { Files.isRegularFile(it) }
                        .filter { it.extension.lowercase(Locale.US) in SHORTCUT_EXTENSIONS }
                        .forEach { path ->
                            val name = path.nameWithoutExtension
                            // Later roots (per-user) win over all-users duplicates.
                            seen[name.lowercase(Locale.US)] = AppEntry(
                                name = name,
                                shortcut = path,
                                dateModified = runCatching {
                                    Files.getLastModifiedTime(path).toMillis()
                                }.getOrDefault(0L),
                            )
                        }
                }
            }
        }
        return seen.values.sortedBy { it.name.lowercase(Locale.US) }
    }

    private companion object {
        val SHORTCUT_EXTENSIONS = setOf("lnk", "url", "appref-ms")
    }
}
