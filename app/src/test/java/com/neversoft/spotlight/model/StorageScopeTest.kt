package com.neversoft.spotlight.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for storage-scope path classification. */
class StorageScopeTest {

    private val primary = "/storage/emulated/0"
    private val sdCardPath = "/storage/1A2B-3C4D/DCIM/photo.jpg"
    private val internalPath = "/storage/emulated/0/Download/report.pdf"

    @Test
    fun all_matchesEverything() {
        assertTrue(StorageScope.matches(StorageScope.ALL, internalPath, primary))
        assertTrue(StorageScope.matches(StorageScope.ALL, sdCardPath, primary))
    }

    @Test
    fun internal_matchesOnlyPrimaryVolume() {
        assertTrue(StorageScope.matches(StorageScope.INTERNAL, internalPath, primary))
        assertTrue(StorageScope.matches(StorageScope.INTERNAL, primary, primary))
        assertFalse(StorageScope.matches(StorageScope.INTERNAL, sdCardPath, primary))
    }

    @Test
    fun removable_matchesEverythingOutsidePrimary() {
        assertTrue(StorageScope.matches(StorageScope.REMOVABLE, sdCardPath, primary))
        assertFalse(StorageScope.matches(StorageScope.REMOVABLE, internalPath, primary))
    }

    @Test
    fun isInternal_doesNotMatchPrefixCollision() {
        // "/storage/emulated/0extra" must NOT be treated as inside "/storage/emulated/0".
        assertFalse(StorageScope.isInternal("/storage/emulated/0extra/file", primary))
    }

    @Test
    fun isInternal_nullPrimaryRoot_isNeverInternal() {
        assertFalse(StorageScope.isInternal(internalPath, null))
    }
}
