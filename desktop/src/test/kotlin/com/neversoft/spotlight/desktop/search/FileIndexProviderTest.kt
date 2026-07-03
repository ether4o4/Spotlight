package com.neversoft.spotlight.desktop.search

import com.neversoft.spotlight.desktop.model.ResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class FileIndexProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixtureTree(): Path {
        val root = tmp.root.toPath()
        Files.createDirectories(root.resolve("Documents"))
        Files.writeString(root.resolve("Documents/report.pdf"), "pdf")
        Files.writeString(root.resolve("Documents/notes.md"), "md")
        Files.createDirectories(root.resolve("Downloads"))
        Files.writeString(root.resolve("Downloads/setup.zip"), "zip")
        Files.createDirectories(root.resolve("Photos"))
        Files.writeString(root.resolve("Photos/pic.jpg"), "jpg")
        Files.writeString(root.resolve(".hiddenrc"), "secret")
        Files.createDirectories(root.resolve("node_modules"))
        Files.writeString(root.resolve("node_modules/junk.js"), "junk")
        return root
    }

    private fun indexed(root: Path, maxEntries: Int = FileIndexProvider.DEFAULT_MAX_ENTRIES): FileIndexProvider =
        FileIndexProvider(listOf(root), downloadsDir = root.resolve("Downloads"), maxEntries = maxEntries)
            .also { it.rebuildIndex() }

    @Test
    fun `browse returns files and folders but not excluded dirs`() {
        val provider = indexed(fixtureTree())
        val titles = provider
            .search("", FileSearchSpec(includeFiles = true, includeFolders = true), 100)
            .map { it.title }

        assertTrue("report.pdf" in titles)
        assertTrue("Documents" in titles)
        assertFalse("junk.js" in titles)
        assertFalse("node_modules" in titles)
    }

    @Test
    fun `query narrows by name`() {
        val provider = indexed(fixtureTree())
        val results = provider.search("rep", FileSearchSpec(includeFiles = true, includeFolders = true), 100)
        assertEquals(listOf("report.pdf"), results.map { it.title })
        assertEquals(ResultType.DOCUMENT, results[0].type)
    }

    @Test
    fun `extension spec keeps only matching files`() {
        val provider = indexed(fixtureTree())
        val results = provider.search("", FileSearchSpec(extensions = FileTypes.ARCHIVE), 100)
        assertEquals(listOf("setup.zip"), results.map { it.title })
    }

    @Test
    fun `hiddenOnly returns dot-prefixed entries`() {
        val provider = indexed(fixtureTree())
        val results = provider.search("", FileSearchSpec(hiddenOnly = true), 100)
        assertEquals(listOf(".hiddenrc"), results.map { it.title })
    }

    @Test
    fun `downloadsOnly keeps entries under the downloads dir`() {
        val provider = indexed(fixtureTree())
        val results = provider.search("", FileSearchSpec(downloadsOnly = true), 100)
        assertEquals(listOf("setup.zip"), results.map { it.title })
    }

    @Test
    fun `folders-only spec returns directories`() {
        val provider = indexed(fixtureTree())
        val results = provider.search("", FileSearchSpec(includeFiles = false, includeFolders = true), 100)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.type == ResultType.FOLDER })
    }

    @Test
    fun `index is capped and reports truncation`() {
        val provider = indexed(fixtureTree(), maxEntries = 2)
        assertTrue(provider.status.ready)
        assertTrue(provider.status.truncated)
        assertEquals(2, provider.status.indexed)
    }

    @Test
    fun `status is not ready before the first rebuild`() {
        val provider = FileIndexProvider(listOf(fixtureTree()))
        assertFalse(provider.status.ready)
        assertTrue(provider.search("", FileSearchSpec(), 10).isEmpty())
    }
}
