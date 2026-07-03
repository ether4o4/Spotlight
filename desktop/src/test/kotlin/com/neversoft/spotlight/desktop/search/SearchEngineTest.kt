package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.FilterSelection
import com.neversoft.spotlight.desktop.model.LaunchAction
import com.neversoft.spotlight.desktop.model.PrimaryFilter
import com.neversoft.spotlight.desktop.model.ResultType
import com.neversoft.spotlight.desktop.model.SearchResult
import com.neversoft.spotlight.desktop.model.SubFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private fun result(
        id: String,
        title: String,
        type: ResultType = ResultType.NOTE,
        snippet: String? = null,
        dateModified: Long = 0L,
    ) = SearchResult(
        id = id,
        title = title,
        subtitle = "test",
        type = type,
        launch = LaunchAction.OpenNotepad,
        dateModified = dateModified,
        snippet = snippet,
    )

    private class FakeApps(private val items: List<SearchResult>) : AppsSource {
        var invoked = false
        override fun search(query: String, limit: Int): List<SearchResult> {
            invoked = true
            return items.take(limit)
        }
    }

    private class FakeNotes(private val items: List<SearchResult>) : NotesSource {
        var invoked = false
        override fun search(query: String, limit: Int): List<SearchResult> {
            invoked = true
            return items.take(limit)
        }
    }

    private class FakeFiles(private val items: List<SearchResult>) : FilesSource {
        var lastSpec: FileSearchSpec? = null
        override fun search(query: String, spec: FileSearchSpec, limit: Int): List<SearchResult> {
            lastSpec = spec
            return items.take(limit)
        }
    }

    private fun engine(
        apps: FakeApps = FakeApps(emptyList()),
        files: FakeFiles = FakeFiles(emptyList()),
        sticky: FakeNotes = FakeNotes(emptyList()),
        notepad: FakeNotes = FakeNotes(emptyList()),
        textNotes: FakeNotes = FakeNotes(emptyList()),
    ) = SearchEngine(apps, files, sticky, notepad, textNotes)

    @Test
    fun `empty query browses every source under ALL`() = runBlocking {
        val apps = FakeApps(listOf(result("a1", "Paint", type = ResultType.APP)))
        val sticky = FakeNotes(listOf(result("s1", "Groceries")))
        val notepad = FakeNotes(listOf(result("n1", "todo.txt")))
        val text = FakeNotes(listOf(result("t1", "ideas")))
        val files = FakeFiles(listOf(result("f1", "report.pdf", type = ResultType.DOCUMENT)))

        val results = engine(apps, files, sticky, notepad, text)
            .search("", FilterSelection())

        assertEquals(setOf("a1", "s1", "n1", "t1", "f1"), results.map { it.id }.toSet())
    }

    @Test
    fun `APPS filter only queries the apps source`() = runBlocking {
        val apps = FakeApps(listOf(result("a1", "Paint", type = ResultType.APP)))
        val sticky = FakeNotes(listOf(result("s1", "Groceries")))
        val files = FakeFiles(listOf(result("f1", "report.pdf")))

        val results = engine(apps = apps, files = files, sticky = sticky)
            .search("", FilterSelection(PrimaryFilter.APPS))

        assertEquals(listOf("a1"), results.map { it.id })
        assertTrue(apps.invoked)
        assertFalse(sticky.invoked)
        assertEquals(null, files.lastSpec)
    }

    @Test
    fun `NOTES sub-filter routes to a single source`() = runBlocking {
        val sticky = FakeNotes(listOf(result("s1", "Groceries")))
        val notepad = FakeNotes(listOf(result("n1", "todo")))
        val text = FakeNotes(listOf(result("t1", "ideas")))

        val results = engine(sticky = sticky, notepad = notepad, textNotes = text)
            .search("", FilterSelection(PrimaryFilter.NOTES, SubFilter.STICKY))

        assertEquals(listOf("s1"), results.map { it.id })
        assertTrue(sticky.invoked)
        assertFalse(notepad.invoked)
        assertFalse(text.invoked)
    }

    @Test
    fun `NOTES without sub-filter aggregates all three note sources`() = runBlocking {
        val sticky = FakeNotes(listOf(result("s1", "Groceries")))
        val notepad = FakeNotes(listOf(result("n1", "todo")))
        val text = FakeNotes(listOf(result("t1", "ideas")))

        val results = engine(sticky = sticky, notepad = notepad, textNotes = text)
            .search("", FilterSelection(PrimaryFilter.NOTES))

        assertEquals(setOf("s1", "n1", "t1"), results.map { it.id }.toSet())
    }

    @Test
    fun `HIDDEN folders sub-filter builds a hidden folders-only spec`() = runBlocking {
        val files = FakeFiles(emptyList())

        engine(files = files).search("", FilterSelection(PrimaryFilter.HIDDEN, SubFilter.HIDDEN_FOLDERS))

        val spec = files.lastSpec
        assertNotNull(spec)
        assertTrue(spec!!.hiddenOnly)
        assertTrue(spec.includeFolders)
        assertFalse(spec.includeFiles)
    }

    @Test
    fun `FILES media sub-filter passes the media extensions`() = runBlocking {
        val files = FakeFiles(emptyList())

        engine(files = files).search("", FilterSelection(PrimaryFilter.FILES, SubFilter.MEDIA))

        val extensions = files.lastSpec?.extensions.orEmpty()
        assertTrue("jpg" in extensions)
        assertTrue("mp4" in extensions)
        assertTrue("mp3" in extensions)
        assertFalse("pdf" in extensions)
    }

    @Test
    fun `ranking is exact, prefix, substring, then content-only`() = runBlocking {
        val notes = FakeNotes(
            listOf(
                result("substring", "My plan"),
                result("content", "Groceries", snippet = "…the plan is…"),
                result("exact", "plan"),
                result("prefix", "planner"),
            ),
        )

        val results = engine(sticky = notes).search("plan", FilterSelection(PrimaryFilter.NOTES, SubFilter.STICKY))

        assertEquals(listOf("exact", "prefix", "substring", "content"), results.map { it.id })
    }

    @Test
    fun `results are deduped by id`() = runBlocking {
        val sticky = FakeNotes(listOf(result("dup", "Groceries")))
        val text = FakeNotes(listOf(result("dup", "Groceries")))

        val results = engine(sticky = sticky, textNotes = text)
            .search("", FilterSelection(PrimaryFilter.NOTES))

        assertEquals(1, results.size)
    }

    @Test
    fun `apps and notes float above raw files in mixed results`() = runBlocking {
        val apps = FakeApps(listOf(result("app", "zeta app", type = ResultType.APP)))
        val sticky = FakeNotes(listOf(result("note", "zeta note")))
        val files = FakeFiles(listOf(result("file", "zeta file", type = ResultType.FILE)))

        val results = engine(apps = apps, files = files, sticky = sticky)
            .search("zeta", FilterSelection())

        assertEquals(listOf("app", "note", "file"), results.map { it.id })
    }
}
