package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.LaunchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotepadTabsProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun utf16(s: String): ByteArray = s.toByteArray(Charsets.UTF_16LE)

    /** "NP" magic + header noise + a backing file path + noise + the tab's text. */
    private fun savedTabBytes(): ByteArray =
        byteArrayOf(0x4E, 0x50, 0x00, 0x01, 0x02) +
            utf16("C:\\Users\\me\\todo.txt") +
            byteArrayOf(0x00, 0x00, 0x03) +
            utf16("buy milk\nring mom about the plan")

    private fun unsavedTabBytes(): ByteArray =
        byteArrayOf(0x4E, 0x50, 0x00, 0x05) +
            utf16("standup notes\ndemo the desktop widget")

    @Test
    fun `parser extracts the backing file path and content`() {
        val parsed = NotepadTabParser.parse(savedTabBytes())
        assertEquals("C:\\Users\\me\\todo.txt", parsed.sourcePath)
        assertTrue(parsed.content.contains("ring mom"))
    }

    @Test
    fun `parser handles unsaved tabs with no path`() {
        val parsed = NotepadTabParser.parse(unsavedTabBytes())
        assertNull(parsed.sourcePath)
        assertTrue(parsed.content.contains("standup notes"))
    }

    @Test
    fun `short binary noise is not extracted as text`() {
        val runs = NotepadTabParser.utf16Runs(byteArrayOf(0x41, 0x00, 0x42, 0x00), minLength = 4)
        assertTrue(runs.isEmpty())
    }

    @Test
    fun `provider surfaces unsaved tabs as searchable notes`() {
        tmp.newFile("aaaa-bbbb.bin").writeBytes(unsavedTabBytes())

        val provider = NotepadTabsProvider(tmp.root.toPath())
        val results = provider.search("widget", 10)

        assertEquals(1, results.size)
        assertEquals("standup notes", results[0].title)
        assertEquals("Notepad — unsaved tab", results[0].subtitle)
        assertEquals(LaunchAction.OpenNotepad, results[0].launch)
        assertTrue(results[0].snippet!!.contains("widget"))
    }

    @Test
    fun `saved tabs are titled by their file name`() {
        tmp.newFile("cccc-dddd.bin").writeBytes(savedTabBytes())

        val results = NotepadTabsProvider(tmp.root.toPath()).search("todo", 10)

        assertEquals(1, results.size)
        assertEquals("todo.txt", results[0].title)
        assertTrue(results[0].subtitle.contains("C:\\Users\\me\\todo.txt"))
    }

    @Test
    fun `missing tab-state dir yields empty results`() {
        assertTrue(NotepadTabsProvider(null).search("", 10).isEmpty())
        assertTrue(NotepadTabsProvider(tmp.root.toPath().resolve("absent")).search("", 10).isEmpty())
    }
}
