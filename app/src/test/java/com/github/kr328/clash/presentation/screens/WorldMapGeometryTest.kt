package com.github.kr328.clash.presentation.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldMapGeometryTest {
    private val imageWidth = 1406f
    private val imageHeight = 714f

    @Test
    fun kansasProjectsToCentralUnitedStates() {
        val point = WorldMapGeometry.projectOrNull(37.751, -97.822, imageWidth, imageHeight)

        assertNotNull(point)
        assertEquals(306.481f, point!!.x, 0.02f)
        assertEquals(216.891f, point.y, 0.02f)
    }

    @Test
    fun hongKongProjectsToTheChinaCoast() {
        val point = WorldMapGeometry.projectOrNull(22.3193, 114.1694, imageWidth, imageHeight)

        assertNotNull(point)
        assertEquals(1059.261f, point!!.x, 0.02f)
        assertEquals(272.448f, point.y, 0.02f)
    }

    @Test
    fun westernLongitudesWrapIntoTheMapsPacificCut() {
        val point = WorldMapGeometry.projectOrNull(0.0, -175.0, imageWidth, imageHeight)

        assertNotNull(point)
        // -175 is equivalent to 185 on this -170..190 frame.
        assertTrue(point!!.x > 1300f)
        assertEquals(352.802f, point.y, 0.02f)
    }

    @Test
    fun invalidLocationsFallBackToTheWorldView() {
        assertNull(WorldMapGeometry.projectOrNull(null, 0.0, imageWidth, imageHeight))
        assertNull(WorldMapGeometry.projectOrNull(0.0, null, imageWidth, imageHeight))
        assertNull(WorldMapGeometry.projectOrNull(91.0, 0.0, imageWidth, imageHeight))
        assertNull(WorldMapGeometry.projectOrNull(0.0, 181.0, imageWidth, imageHeight))
        assertNull(WorldMapGeometry.projectOrNull(Double.NaN, 0.0, imageWidth, imageHeight))
    }

    @Test
    fun regionalCropPreservesAspectRatioAndCentersInteriorLocations() {
        val point = WorldMapGeometry.projectOrNull(37.751, -97.822, imageWidth, imageHeight)!!
        val crop = WorldMapGeometry.regionalCrop(point, imageWidth, imageHeight, viewportAspect = 2.3f)
        val bounds = WorldMapGeometry.contentBounds(imageWidth, imageHeight)

        assertEquals(2.3f, crop.width / crop.height, 0.001f)
        assertEquals(point.x, crop.left + crop.width / 2f, 0.02f)
        assertEquals(point.y, crop.top + crop.height / 2f, 0.02f)
        assertTrue(crop.width < bounds.width * 0.25f)
        assertTrue(crop.left >= bounds.left && crop.right <= bounds.right)
        assertTrue(crop.top >= bounds.top && crop.bottom <= bounds.bottom)
    }

    @Test
    fun regionalCropClampsAtMapEdges() {
        val point = WorldMapGeometry.projectOrNull(85.0, -175.0, imageWidth, imageHeight)!!
        val crop = WorldMapGeometry.regionalCrop(point, imageWidth, imageHeight, viewportAspect = 2.3f)
        val bounds = WorldMapGeometry.contentBounds(imageWidth, imageHeight)

        assertEquals(bounds.right, crop.right, 0.02f)
        assertEquals(bounds.top, crop.top, 0.02f)
    }

    @Test
    fun regionalCropCapsLongitudeSpanForVeryWideViewports() {
        val point = WorldMapGeometry.projectOrNull(22.3193, 114.1694, imageWidth, imageHeight)!!
        val crop = WorldMapGeometry.regionalCrop(point, imageWidth, imageHeight, viewportAspect = 10f)
        val bounds = WorldMapGeometry.contentBounds(imageWidth, imageHeight)
        val pixelsPerDegree = bounds.width / 360f

        assertEquals(10f, crop.width / crop.height, 0.001f)
        assertTrue(crop.width / pixelsPerDegree <= 60.02f)
    }

    @Test
    fun worldCropContainsTheEntireMap() {
        val crop = WorldMapGeometry.worldCrop(imageWidth, imageHeight)
        val bounds = WorldMapGeometry.contentBounds(imageWidth, imageHeight)

        assertEquals(bounds.left, crop.left, 0.001f)
        assertEquals(bounds.top, crop.top, 0.001f)
        assertEquals(bounds.width, crop.width, 0.001f)
        assertEquals(bounds.height, crop.height, 0.001f)
    }
}
