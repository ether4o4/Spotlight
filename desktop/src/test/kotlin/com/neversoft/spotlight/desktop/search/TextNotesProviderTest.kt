package com.neversoft.spotlight.desktop.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class TextNotesProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixtureNotes(): Path {
        val root = tmp.root.toPath()
        Files.writeString(root.resolve("Groceries.md"), "# Groceries\nbuy milk and eggs")
        Files.writeString(root.resolve("ideas.txt"), "widget for the desktop\nspotlight everywhere")
        Files.writeString(root.resolve("ignore.pdf"), "not a note")
        Files.createDirectories(root.resolve(".obsidian"))
        Files.writeString(root.resolve(".obsidian/config.md"), "app config, not a note")
        return root
    }

    @Test
    fun `browses every note on empty query, skipping non-notes and app dirs`() {
        val results = TextNotesProvider(listOf(fixtureNotes())).search("", 10)
        assertEquals(setOf("Groceries", "ideas"), results.map { it.title }.toSet())
    }

    @Test
    fun `matches by file name without a snippet`() {
        val results = TextNotesProvider(listOf(fixtureNotes())).search("groc", 10)
        assertEquals(1, results.size)
        assertEquals("Groceries", results[0].title)
        assertNull(results[0].snippet)
    }

    @Test
    fun `matches inside note content with a snippet`() {
        val results = TextNotesProvider(listOf(fixtureNotes())).search("milk", 10)
        assertEquals(1, results.size)
        assertEquals("Groceries", results[0].title)
        assertTrue(results[0].snippet!!.contains("milk"))
    }

    @Test
    fun `oversized notes are searchable by name only`() {
        val provider = TextNotesProvider(listOf(fixtureNotes()), maxBytesPerFile = 4)
        assertTrue(provider.search("milk", 10).isEmpty())
        assertEquals(1, provider.search("groceries", 10).size)
    }

    @Test
    fun `missing roots yield empty results`() {
        val provider = TextNotesProvider(listOf(tmp.root.toPath().resolve("absent")))
        assertTrue(provider.search("", 10).isEmpty())
    }
}
