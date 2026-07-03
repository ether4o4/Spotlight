package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.LaunchAction
import com.neversoft.spotlight.desktop.model.ResultType
import com.neversoft.spotlight.desktop.model.SearchResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.DriverManager

/**
 * Deep adapter for Windows Sticky Notes: reads the app's own store
 * (plum.sqlite) directly, so every sticky note is searchable by its text.
 * The database is copied to a temp dir first — Sticky Notes keeps it open
 * with a write lock while running.
 */
class StickyNotesProvider(private val dbPath: Path?) : NotesSource {

    data class Note(val id: String, val text: String)

    @Volatile
    private var cache: List<Note>? = null

    fun refresh() {
        cache = null
    }

    override fun search(query: String, limit: Int): List<SearchResult> {
        val notes = cache ?: loadNotes().also { cache = it }
        val results = ArrayList<SearchResult>()
        for (note in notes) {
            if (results.size >= limit) break
            val title = firstLineOf(note.text)
            val titleMatch = Matching.titleMatches(title, query)
            val snippet = if (titleMatch) null else Matching.snippetAround(note.text, query)
            if (!titleMatch && snippet == null) continue
            results += SearchResult(
                id = "sticky:${note.id}",
                title = title,
                subtitle = "Sticky Note",
                type = ResultType.NOTE,
                launch = LaunchAction.OpenStickyNotes,
                snippet = snippet,
            )
        }
        return results
    }

    /** Reads every note's text; empty on any failure (app absent, schema drift). */
    fun loadNotes(): List<Note> {
        val db = dbPath ?: return emptyList()
        if (!Files.isRegularFile(db)) return emptyList()
        var tempDir: Path? = null
        return try {
            // Copy the store (and its WAL side files, which may hold the newest
            // edits) so we read a consistent snapshot without fighting the app's lock.
            val dir = Files.createTempDirectory("spotlight-sticky").also { tempDir = it }
            val copy = dir.resolve(db.fileName.toString())
            Files.copy(db, copy, StandardCopyOption.REPLACE_EXISTING)
            for (suffix in listOf("-wal", "-shm")) {
                val side = db.resolveSibling(db.fileName.toString() + suffix)
                if (Files.isRegularFile(side)) {
                    Files.copy(side, dir.resolve(side.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            DriverManager.getConnection("jdbc:sqlite:$copy").use { conn ->
                conn.createStatement().use { statement ->
                    val rs = statement.executeQuery("SELECT Id, Text FROM Note")
                    buildList {
                        while (rs.next()) {
                            val text = StickyNotesParser.cleanText(rs.getString("Text") ?: continue)
                            if (text.isBlank()) continue
                            add(Note(id = rs.getString("Id") ?: text.hashCode().toString(), text = text))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            tempDir?.let { dir ->
                runCatching {
                    Files.list(dir).use { files -> files.forEach { Files.deleteIfExists(it) } }
                    Files.deleteIfExists(dir)
                }
            }
        }
    }

    private fun firstLineOf(text: String): String =
        text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(MAX_TITLE_LENGTH) ?: "(untitled note)"

    private companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}

/** Strips Sticky Notes' inline markup so only the user's actual text remains. */
object StickyNotesParser {

    /** Notes embed paragraph ids like `\id=9f8c2d1e-...` inside the Text column. */
    private val ID_MARKER = Regex("""\\id=[0-9a-fA-F-]+\s?""")

    fun cleanText(raw: String): String = raw.replace(ID_MARKER, "").trim()
}
