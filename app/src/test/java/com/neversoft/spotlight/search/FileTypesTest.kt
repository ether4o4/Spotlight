package com.neversoft.spotlight.search

import com.neversoft.spotlight.model.ResultType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM tests for extension-based file classification. */
class FileTypesTest {

    @Test
    fun extensionOf_extractsLowercaseExtension() {
        assertEquals("txt", FileTypes.extensionOf("a.b.c.TXT"))
        assertEquals("mp3", FileTypes.extensionOf("Song.Mp3"))
        assertEquals("", FileTypes.extensionOf("noextension"))
        assertEquals("", FileTypes.extensionOf("trailingdot."))
    }

    @Test
    fun typeForName_classifiesByExtension() {
        assertEquals(ResultType.AUDIO, FileTypes.typeForName("song.mp3"))
        assertEquals(ResultType.VIDEO, FileTypes.typeForName("clip.MP4"))
        assertEquals(ResultType.IMAGE, FileTypes.typeForName("photo.jpeg"))
        assertEquals(ResultType.DOCUMENT, FileTypes.typeForName("report.pdf"))
        assertEquals(ResultType.ARCHIVE, FileTypes.typeForName("bundle.zip"))
        assertEquals(ResultType.APK, FileTypes.typeForName("install.apk"))
    }

    @Test
    fun typeForName_unknownOrMissingExtension_isPlainFile() {
        assertEquals(ResultType.FILE, FileTypes.typeForName("mystery.qwerty"))
        assertEquals(ResultType.FILE, FileTypes.typeForName("noextension"))
    }
}
