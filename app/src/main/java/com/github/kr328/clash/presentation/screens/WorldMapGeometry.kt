package com.github.kr328.clash.presentation.screens

import kotlin.math.min

internal data class MapPoint(val x: Float, val y: Float)

internal data class MapBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal data class MapCrop(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * Projection and crop math for `R.drawable.world_map`.
 *
 * The PNG is rendered from Wikimedia's `BlankMap-World-Equirectangular.svg`.
 * Its ocean border is not a -180..180 / -90..90 frame: the source map is
 * centered on 10 degrees east, so the visible frame spans -170..190 degrees
 * longitude. Treating the border as -180..180 shifts every marker about
 * 10 degrees east, which is why West African / North American locations
 * appeared over the ocean.
 *
 * The frame fractions below are the SVG ocean-path centerlines after
 * librsvg's aspect-preserving render to 1406x714. They deliberately exclude
 * the transparent PNG margins and the outer half of the ocean stroke.
 */
internal object WorldMapGeometry {
    const val WEST_LONGITUDE = -170.0
    const val EAST_LONGITUDE = 190.0
    const val NORTH_LATITUDE = 90.0
    const val SOUTH_LATITUDE = -90.0

    // Country/region-sized view: 24 degrees of latitude, capped at 60 degrees
    // of longitude for very wide cards.
    private const val REGIONAL_LATITUDE_SPAN = 24f
    private const val REGIONAL_MAX_LONGITUDE_SPAN = 60f

    private const val FRAME_LEFT_FRACTION = 0.03568796113159217f
    private const val FRAME_TOP_FRACTION = 0.04031526419537588f
    private const val FRAME_RIGHT_FRACTION = 0.9449037657218224f
    private const val FRAME_BOTTOM_FRACTION = 0.9479257377095024f

    fun isValidLocation(latitude: Double?, longitude: Double?): Boolean =
        latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            latitude in SOUTH_LATITUDE..NORTH_LATITUDE &&
            longitude in -180.0..180.0

    fun contentBounds(imageWidth: Float, imageHeight: Float): MapBounds {
        require(imageWidth > 0f && imageHeight > 0f)
        return MapBounds(
            left = imageWidth * FRAME_LEFT_FRACTION,
            top = imageHeight * FRAME_TOP_FRACTION,
            right = imageWidth * FRAME_RIGHT_FRACTION,
            bottom = imageHeight * FRAME_BOTTOM_FRACTION,
        )
    }

    fun projectOrNull(
        latitude: Double?,
        longitude: Double?,
        imageWidth: Float,
        imageHeight: Float,
    ): MapPoint? {
        if (!isValidLocation(latitude, longitude)) return null
        val lat = latitude!!
        val lon = wrapLongitude(longitude!!)
        val bounds = contentBounds(imageWidth, imageHeight)
        val xFraction = ((lon - WEST_LONGITUDE) / (EAST_LONGITUDE - WEST_LONGITUDE)).toFloat()
        val yFraction = ((NORTH_LATITUDE - lat) / (NORTH_LATITUDE - SOUTH_LATITUDE)).toFloat()
        return MapPoint(
            x = bounds.left + xFraction * bounds.width,
            y = bounds.top + yFraction * bounds.height,
        )
    }

    fun regionalCrop(
        center: MapPoint,
        imageWidth: Float,
        imageHeight: Float,
        viewportAspect: Float,
    ): MapCrop {
        val bounds = contentBounds(imageWidth, imageHeight)
        val aspect = safeAspect(viewportAspect, bounds)
        val pixelsPerDegreeX = bounds.width / (EAST_LONGITUDE - WEST_LONGITUDE).toFloat()
        val pixelsPerDegreeY = bounds.height / (NORTH_LATITUDE - SOUTH_LATITUDE).toFloat()
        val targetHeight = min(
            REGIONAL_LATITUDE_SPAN * pixelsPerDegreeY,
            REGIONAL_MAX_LONGITUDE_SPAN * pixelsPerDegreeX / aspect,
        )
        return cropAround(center, bounds, aspect, targetHeight * aspect, targetHeight)
    }

    fun worldCrop(
        imageWidth: Float,
        imageHeight: Float,
    ): MapCrop {
        val bounds = contentBounds(imageWidth, imageHeight)
        return MapCrop(
            left = bounds.left,
            top = bounds.top,
            width = bounds.width,
            height = bounds.height,
        )
    }

    private fun cropAround(
        center: MapPoint,
        bounds: MapBounds,
        aspect: Float,
        desiredWidth: Float,
        desiredHeight: Float,
    ): MapCrop {
        var width = desiredWidth.coerceIn(1f, bounds.width)
        var height = desiredHeight.coerceIn(1f, bounds.height)

        if (width / height > aspect) {
            width = height * aspect
        } else {
            height = width / aspect
        }

        // Guard against float rounding at the frame edges.
        if (width > bounds.width) {
            width = bounds.width
            height = width / aspect
        }
        if (height > bounds.height) {
            height = bounds.height
            width = height * aspect
        }

        val maxLeft = (bounds.right - width).coerceAtLeast(bounds.left)
        val maxTop = (bounds.bottom - height).coerceAtLeast(bounds.top)
        return MapCrop(
            left = (center.x - width / 2f).coerceIn(bounds.left, maxLeft),
            top = (center.y - height / 2f).coerceIn(bounds.top, maxTop),
            width = width,
            height = height,
        )
    }

    private fun safeAspect(aspect: Float, bounds: MapBounds): Float =
        if (aspect.isFinite() && aspect > 0f) aspect else bounds.width / bounds.height

    private fun wrapLongitude(longitude: Double): Double {
        val span = EAST_LONGITUDE - WEST_LONGITUDE
        var shifted = (longitude - WEST_LONGITUDE) % span
        if (shifted < 0.0) shifted += span
        return WEST_LONGITUDE + shifted
    }
}
