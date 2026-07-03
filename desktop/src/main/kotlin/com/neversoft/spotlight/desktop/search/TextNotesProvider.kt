package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.LaunchAction
import com.neversoft.spotlight.desktop.model.ResultType
import com.neversoft.spotlight.desktop.model.SearchResult
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

/**
 * Deep adapter for plain-text notes: .md / .txt files under the note roots
 * (Documents, Desktop, ~/Notes — covers Obsidian-style vaults). File contents
 * are held in memory so the query matches *inside* the note, not just its
 * filename; oversized files are indexed by name only.
 */
class TextNotesProvider(
    private val roots: List<Path>,
    private val extensions: Set<String> = FileTypes.NOTE_TEXT,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxBytesPerFile: Long = DEFAULT_MAX_BYTES,
) : NotesSource {

    data class NoteFile(
        val title: String,
        val path: String,
        val content: String,
        val dateModified: Long,
    )

    @Volatile
    private var cache: List<NoteFile>? = null

    fun refresh() {
        cache = null
    }

    override fun search(query: String, limit: Int): List<SearchResult> {
        val notes = cache ?: loadNotes().also { cache = it }
        val results = ArrayList<SearchResult>()
        for (note in notes) {
            if (results.size >= limit) break
            val titleMatch = Matching.titleMatches(note.title, query)
            val snippet = if (titleMatch) null else Matching.snippetAround(note.content, query)
            if (!titleMatch && snippet == null) continue
            results += SearchResult(
                id = "note:${note.path}",
                title = note.title,
                subtitle = note.path,
                type = ResultType.NOTE,
                launch = LaunchAction.OpenPath(note.path),
                dateModified = note.dateModified,
                snippet = snippet,
            )
        }
        return results
    }

    fun loadNotes(): List<NoteFile> {
        val collected = ArrayList<NoteFile>()
        for (root in roots) {
            if (collected.size >= maxFiles || !Files.isDirectory(root)) continue
            runCatching {
                Files.walkFileTree(
                    root,
                    object : SimpleFileVisitor<Path>() {
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val name = dir.fileName?.toString()?.lowercase(Locale.US)
                            return if (name in EXCLUDED_DIRS) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                        }

                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val name = file.fileName.toString()
                            if (FileTypes.extensionOf(name) in extensions) {
                                collected += NoteFile(
                                    title = name.substringBeforeLast('.'),
                                    path = file.toString(),
                                    content = if (attrs.size() <= maxBytesPerFile) readText(file) else "",
                                    dateModified = runCatching { attrs.lastModifiedTime().toMillis() }
                                        .getOrDefault(0L),
                                )
                            }
                            return if (collected.size >= maxFiles) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                        }

                        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                            FileVisitResult.CONTINUE
                    },
                )
            }
        }
        return collected
    }

    private fun readText(file: Path): String =
        runCatching { String(Files.readAllBytes(file), StandardCharsets.UTF_8) }.getOrDefault("")

    private companion object {
        const val DEFAULT_MAX_FILES = 5_000
        const val DEFAULT_MAX_BYTES = 256L * 1024

        val EXCLUDED_DIRS = setOf("node_modules", ".git", ".obsidian", ".trash")
    }
}
