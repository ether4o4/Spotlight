package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.LaunchAction
import com.neversoft.spotlight.desktop.model.ResultType
import com.neversoft.spotlight.desktop.model.SearchResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.streams.asSequence

/**
 * Deep adapter for Windows 11 Notepad: reads the TabState folder where Notepad
 * persists every open tab — including tabs that were never saved to a file.
 * The .bin format is undocumented, so extraction is deliberately best-effort:
 * we pull printable UTF-16LE runs out of the buffer and classify them (see
 * [NotepadTabParser]). Good enough to find a note by its text; never fatal.
 */
class NotepadTabsProvider(private val tabStateDir: Path?) : NotesSource {

    @Volatile
    private var cache: List<NotepadTabParser.ParsedTab>? = null

    fun refresh() {
        cache = null
    }

    override fun search(query: String, limit: Int): List<SearchResult> {
        val tabs = cache ?: loadTabs().also { cache = it }
        val results = ArrayList<SearchResult>()
        for ((index, tab) in tabs.withIndex()) {
            if (results.size >= limit) break
            val title = titleOf(tab)
            val titleMatch = Matching.titleMatches(title, query)
            val snippet = if (titleMatch) null else Matching.snippetAround(tab.content, query)
            if (!titleMatch && snippet == null) continue
            results += SearchResult(
                id = "notepad:$index:${title.hashCode()}",
                title = title,
                subtitle = if (tab.sourcePath != null) "Notepad — ${tab.sourcePath}" else "Notepad — unsaved tab",
                type = ResultType.NOTE,
                launch = LaunchAction.OpenNotepad,
                snippet = snippet,
            )
        }
        return results
    }

    fun loadTabs(): List<NotepadTabParser.ParsedTab> {
        val dir = tabStateDir ?: return emptyList()
        if (!Files.isDirectory(dir)) return emptyList()
        return runCatching {
            Files.list(dir).use { stream ->
                stream.asSequence()
                    .filter { it.extension.equals("bin", ignoreCase = true) }
                    .mapNotNull { path ->
                        runCatching {
                            val parsed = NotepadTabParser.parse(Files.readAllBytes(path))
                            parsed.takeIf { it.content.isNotBlank() || it.sourcePath != null }
                        }.getOrNull()
                    }
                    .distinctBy { it.sourcePath ?: it.content }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun titleOf(tab: NotepadTabParser.ParsedTab): String {
        tab.sourcePath?.let { return it.substringAfterLast('\\').substringAfterLast('/') }
        return tab.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(MAX_TITLE_LENGTH)
            ?: "(empty tab)"
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}

/**
 * Best-effort extraction of text from Notepad's undocumented TabState binary:
 * decodes printable UTF-16LE runs at both byte alignments, treats a run that
 * looks like an absolute Windows path as the tab's backing file, and the
 * longest remaining run as the tab's content.
 */
object NotepadTabParser {

    data class ParsedTab(val sourcePath: String?, val content: String)

    /** Found anywhere in a run — header noise can glue itself onto a path. */
    private val PATH_IN_RUN = Regex("""[A-Za-z]:\\[^<>:"|?*\n\r\t]+""")

    fun parse(bytes: ByteArray): ParsedTab {
        val runs = utf16Runs(bytes)
        val path = runs.firstNotNullOfOrNull { PATH_IN_RUN.find(it)?.value }?.trim()
        val content = runs.asSequence()
            // The run carrying the path is metadata, not the tab's text.
            .map { run -> if (path != null) run.replace(path, " ") else run }
            .map { it.trim() }
            .filter { it.length >= 4 }
            .maxByOrNull { it.length }
            .orEmpty()
        return ParsedTab(sourcePath = path, content = content)
    }

    /** Printable UTF-16LE runs of at least [minLength] chars, both alignments. */
    internal fun utf16Runs(bytes: ByteArray, minLength: Int = 4): List<String> {
        val runs = LinkedHashSet<String>()
        for (offset in 0..1) {
            val current = StringBuilder()
            var i = offset
            while (i + 1 < bytes.size) {
                val code = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                val ch = code.toChar()
                if (isPrintable(ch)) {
                    current.append(ch)
                } else {
                    if (current.length >= minLength) runs += current.toString()
                    current.setLength(0)
                }
                i += 2
            }
            if (current.length >= minLength) runs += current.toString()
        }
        return runs.toList()
    }

    /**
     * Deliberately Latin-biased: arbitrary binary bytes decode as "valid" CJK /
     * exotic-plane code points, which would glue header noise onto real text.
     * Restricting runs to common text ranges keeps extraction clean; non-Latin
     * note content is a documented limit of this best-effort parser.
     */
    private fun isPrintable(ch: Char): Boolean = when {
        ch == '\n' || ch == '\r' || ch == '\t' -> true
        ch.code in 0x20..0x7E -> true // ASCII
        ch.code in 0xA0..0x036F -> true // Latin-1 supplement … combining marks
        ch.code in 0x2010..0x2027 -> true // common punctuation (– — ‘ ’ “ ” …)
        ch == '€' -> true // €
        else -> false
    }
}
