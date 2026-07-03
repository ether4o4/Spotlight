package com.neversoft.spotlight.desktop.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.sql.DriverManager

class StickyNotesProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixtureDb(): Path {
        val db = tmp.newFile("plum.sqlite").toPath()
        DriverManager.getConnection("jdbc:sqlite:$db").use { conn ->
            conn.createStatement().use {
                it.executeUpdate("CREATE TABLE Note (Id TEXT, Text TEXT)")
            }
            conn.prepareStatement("INSERT INTO Note (Id, Text) VALUES (?, ?)").use { insert ->
                insert.setString(1, "id-1")
                insert.setString(2, "\\id=9f8c2d1e-aaaa-bbbb-cccc-000000000001 Groceries\nbuy milk and eggs")
                insert.executeUpdate()
                insert.setString(1, "id-2")
                insert.setString(2, "\\id=9f8c2d1e-aaaa-bbbb-cccc-000000000002 Meeting notes\ncall with dana at noon")
                insert.executeUpdate()
            }
        }
        return db
    }

    @Test
    fun `browses every note on empty query`() {
        val results = StickyNotesProvider(fixtureDb()).search("", 10)
        assertEquals(setOf("Groceries", "Meeting notes"), results.map { it.title }.toSet())
        assertTrue(results.all { it.subtitle == "Sticky Note" })
    }

    @Test
    fun `id markers are stripped from note text`() {
        val notes = StickyNotesProvider(fixtureDb()).loadNotes()
        assertTrue(notes.none { it.text.contains("\\id=") })
        assertTrue(notes.any { it.text.startsWith("Groceries") })
    }

    @Test
    fun `matches by title without a snippet`() {
        val results = StickyNotesProvider(fixtureDb()).search("groc", 10)
        assertEquals(1, results.size)
        assertEquals("Groceries", results[0].title)
        assertNull(results[0].snippet)
    }

    @Test
    fun `matches inside note content with a snippet`() {
        val results = StickyNotesProvider(fixtureDb()).search("dana", 10)
        assertEquals(1, results.size)
        assertEquals("Meeting notes", results[0].title)
        assertTrue(results[0].snippet!!.contains("dana"))
    }

    @Test
    fun `unmatched query returns nothing`() {
        assertTrue(StickyNotesProvider(fixtureDb()).search("zzz-no-match", 10).isEmpty())
    }

    @Test
    fun `missing store yields empty results, not errors`() {
        assertTrue(StickyNotesProvider(null).search("", 10).isEmpty())
        assertTrue(StickyNotesProvider(tmp.root.toPath().resolve("absent.sqlite")).search("", 10).isEmpty())
    }
}
