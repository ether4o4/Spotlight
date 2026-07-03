package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.ResultType
import org.junit.Assert.assertEquals
import org.junit.Test

class FileTypesTest {

    @Test
    fun `extension is lowercased and empty when absent`() {
        assertEquals("pdf", FileTypes.extensionOf("Report.PDF"))
        assertEquals("", FileTypes.extensionOf("README"))
    }

    @Test
    fun `extensions classify to their result types`() {
        assertEquals(ResultType.IMAGE, FileTypes.typeForName("pic.jpg"))
        assertEquals(ResultType.VIDEO, FileTypes.typeForName("clip.mp4"))
        assertEquals(ResultType.AUDIO, FileTypes.typeForName("song.flac"))
        assertEquals(ResultType.DOCUMENT, FileTypes.typeForName("notes.md"))
        assertEquals(ResultType.ARCHIVE, FileTypes.typeForName("bundle.tar"))
        assertEquals(ResultType.FILE, FileTypes.typeForName("mystery.xyz"))
    }
}
