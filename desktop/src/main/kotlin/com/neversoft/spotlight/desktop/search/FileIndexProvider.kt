package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.LaunchAction
import com.neversoft.spotlight.desktop.model.ResultType
import com.neversoft.spotlight.desktop.model.SearchResult
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.util.Locale

/**
 * In-memory index of every file and folder under the given roots (the user
 * profile by default). Built once in the background at startup — searching is
 * then instant per keystroke. AppData and other noise dirs are excluded from
 * the general index (the note adapters read the interesting parts of AppData
 * directly), and the walk is capped so a pathological disk can't eat the heap.
 */
class FileIndexProvider(
    private val roots: List<Path>,
    private val downloadsDir: Path? = null,
    private val excludedDirNames: Set<String> = DEFAULT_EXCLUDES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : FilesSource {

    data class Entry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val isHidden: Boolean,
        val extension: String,
        val dateModified: Long,
    )

    data class IndexStatus(
        val ready: Boolean = false,
        val indexed: Int = 0,
        val truncated: Boolean = false,
    )

    @Volatile
    private var entries: List<Entry> = emptyList()

    @Volatile
    var status: IndexStatus = IndexStatus()
        private set

    /** Walks the roots and replaces the index. Call from a background coroutine. */
    fun rebuildIndex() {
        val collected = ArrayList<Entry>(INITIAL_CAPACITY)
        var truncated = false

        for (root in roots) {
            if (truncated || !Files.isDirectory(root)) continue
            try {
                Files.walkFileTree(
                    root,
                    object : SimpleFileVisitor<Path>() {
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                            if (name.lowercase(Locale.US) in excludedDirNames) return FileVisitResult.SKIP_SUBTREE
                            if (dir != root) {
                                collected += entryFor(dir, attrs, isDirectory = true)
                                if (collected.size >= maxEntries) {
                                    truncated = true
                                    return FileVisitResult.TERMINATE
                                }
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            collected += entryFor(file, attrs, isDirectory = false)
                            if (collected.size >= maxEntries) {
                                truncated = true
                                return FileVisitResult.TERMINATE
                            }
                            return FileVisitResult.CONTINUE
                        }

                        /** Unreadable dirs (ACL-denied junctions etc.) are skipped, not fatal. */
                        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                            FileVisitResult.CONTINUE
                    },
                )
            } catch (_: IOException) {
                // Root disappeared mid-walk; keep whatever we collected.
            }
        }

        entries = collected
        status = IndexStatus(ready = true, indexed = collected.size, truncated = truncated)
    }

    override fun search(query: String, spec: FileSearchSpec, limit: Int): List<SearchResult> {
        val downloadsPrefix = downloadsDir?.toString()
        return entries.asSequence()
            .filter { entry ->
                when {
                    entry.isDirectory && !spec.includeFolders -> false
                    !entry.isDirectory && !spec.includeFiles -> false
                    spec.hiddenOnly && !entry.isHidden -> false
                    spec.extensions != null && (entry.isDirectory || entry.extension !in spec.extensions) -> false
                    spec.downloadsOnly &&
                        (downloadsPrefix == null || !entry.path.startsWith(downloadsPrefix)) -> false
                    else -> Matching.titleMatches(entry.name, query)
                }
            }
            .take(limit)
            .map { entry ->
                SearchResult(
                    id = entry.path,
                    title = entry.name,
                    subtitle = parentOf(entry.path),
                    type = if (entry.isDirectory) ResultType.FOLDER else FileTypes.typeForName(entry.name),
                    launch = LaunchAction.OpenPath(entry.path),
                    dateModified = entry.dateModified,
                )
            }
            .toList()
    }

    private fun entryFor(path: Path, attrs: BasicFileAttributes, isDirectory: Boolean): Entry {
        val name = path.fileName?.toString() ?: path.toString()
        return Entry(
            name = name,
            path = path.toString(),
            isDirectory = isDirectory,
            isHidden = isHiddenEntry(path, name),
            extension = if (isDirectory) "" else FileTypes.extensionOf(name),
            dateModified = runCatching { attrs.lastModifiedTime().toMillis() }.getOrDefault(0L),
        )
    }

    /** Hidden = dot-prefixed (unix convention) or the DOS hidden attribute. */
    private fun isHiddenEntry(path: Path, name: String): Boolean {
        if (name.startsWith(".")) return true
        return runCatching {
            Files.readAttributes(path, DosFileAttributes::class.java).isHidden
        }.getOrDefault(false)
    }

    private fun parentOf(path: String): String =
        path.substringBeforeLast('\\', "").ifEmpty { path.substringBeforeLast('/', "") }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 250_000
        private const val INITIAL_CAPACITY = 4_096

        /** Compared lowercase. AppData is huge and covered by the note adapters. */
        val DEFAULT_EXCLUDES = setOf(
            "appdata",
            "node_modules",
            ".git",
            ".gradle",
            "__pycache__",
            "\$recycle.bin",
        )
    }
}
