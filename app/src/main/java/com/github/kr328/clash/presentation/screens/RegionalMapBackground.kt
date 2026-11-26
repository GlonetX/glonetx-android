package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R

/**
 * Draws the world map as a subdued card background.
 *
 * When a valid location is available, the map is cropped to a state/region
 * sized view around that coordinate and a marker is drawn at the projected
 * position. Without a location it falls back to a centered world view.
 */
@Composable
fun RegionalMapBackground(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier,
) {
    val map = ImageBitmap.imageResource(R.drawable.world_map)
    val accent = MaterialTheme.colorScheme.primary

    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val imageWidth = map.width.toFloat()
        val imageHeight = map.height.toFloat()
        val viewportAspect = size.width / size.height
        val point = WorldMapGeometry.projectOrNull(
            latitude = latitude,
            longitude = longitude,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )
        val crop = if (point != null) {
            WorldMapGeometry.regionalCrop(point, imageWidth, imageHeight, viewportAspect)
        } else {
            WorldMapGeometry.worldCrop(imageWidth, imageHeight, viewportAspect)
        }

        // Both axes use the same scale, so the map is never stretched. Using
        // a float transform also keeps the marker aligned with the crop;
        // integer srcOffset/srcSize rounding can introduce a visible drift.
        val scale = size.width / crop.width
        clipRect {
            withTransform({
                translate(left = -crop.left * scale, top = -crop.top * scale)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                drawImage(image = map, alpha = 0.34f)
            }
        }

        if (point != null) {
            val marker = Offset(
                x = (point.x - crop.left) * scale,
                y = (point.y - crop.top) * scale,
            )
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = 18.dp.toPx(),
                center = marker,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = 7.dp.toPx(),
                center = marker,
            )
            drawCircle(
                color = accent,
                radius = 5.dp.toPx(),
                center = marker,
            )
        }
    }
}
