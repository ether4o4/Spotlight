package com.neversoft.spotlight.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-JVM tests for the filter selection state machine. */
class FilterSelectionTest {

    @Test
    fun default_isAllWithNoSub() {
        val s = FilterSelection()
        assertEquals(PrimaryFilter.ALL, s.primary)
        assertNull(s.sub)
    }

    @Test
    fun withPrimary_resetsSub() {
        val s = FilterSelection(PrimaryFilter.MEDIA, SubFilter.PHOTOS)
        val next = s.withPrimary(PrimaryFilter.FILES)
        assertEquals(PrimaryFilter.FILES, next.primary)
        assertNull(next.sub)
    }

    @Test
    fun withSub_togglesSameSelectionOff() {
        val withPhotos = FilterSelection(PrimaryFilter.MEDIA).withSub(SubFilter.PHOTOS)
        assertEquals(SubFilter.PHOTOS, withPhotos.sub)
        val toggledOff = withPhotos.withSub(SubFilter.PHOTOS)
        assertNull(toggledOff.sub)
    }

    @Test
    fun withSub_switchesToDifferentSelection() {
        val withVideos = FilterSelection(PrimaryFilter.MEDIA, SubFilter.PHOTOS)
            .withSub(SubFilter.VIDEOS)
        assertEquals(SubFilter.VIDEOS, withVideos.sub)
    }
}
