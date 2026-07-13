package com.neversoft.spotlight.desktop.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSelectionTest {

    @Test
    fun `changing primary resets sub-filter`() {
        val selection = FilterSelection(PrimaryFilter.NOTES, SubFilter.STICKY)
        val next = selection.withPrimary(PrimaryFilter.FILES)
        assertEquals(PrimaryFilter.FILES, next.primary)
        assertNull(next.sub)
    }

    @Test
    fun `tapping the active sub-filter toggles it off`() {
        val selection = FilterSelection(PrimaryFilter.NOTES, SubFilter.STICKY)
        assertNull(selection.withSub(SubFilter.STICKY).sub)
        assertEquals(SubFilter.NOTEPAD, selection.withSub(SubFilter.NOTEPAD).sub)
    }

    @Test
    fun `sub-filters map to their parents`() {
        assertEquals(
            listOf(SubFilter.STICKY, SubFilter.NOTEPAD, SubFilter.TEXT),
            PrimaryFilter.NOTES.subFilters(),
        )
        assertTrue(PrimaryFilter.ALL.subFilters().isEmpty())
        assertTrue(PrimaryFilter.APPS.subFilters().isEmpty())
        assertTrue(SubFilter.entries.all { it in it.parent.subFilters() })
    }
}
