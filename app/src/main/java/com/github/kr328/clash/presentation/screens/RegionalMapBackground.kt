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
 * The default view fits the entire world inside the card without cropping or
 * stretching. Regional mode is retained for future use and crops around a
 * valid coordinate when enabled explicitly.
 */
@Composable
fun RegionalMapBackground(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier,
    regional: Boolean = false,
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
        val crop = if (point != null && regional) {
            WorldMapGeometry.regionalCrop(point, imageWidth, imageHeight, viewportAspect)
        } else {
            WorldMapGeometry.worldCrop(imageWidth, imageHeight)
        }

        // Fit the complete crop inside the card. Global mode therefore shows
        // the whole world with letterboxing instead of cropping the poles.
        val scale = minOf(size.width / crop.width, size.height / crop.height)
        val drawLeft = (size.width - crop.width * scale) / 2f
        val drawTop = (size.height - crop.height * scale) / 2f
        val offset = Offset(
            x = drawLeft - crop.left * scale,
            y = drawTop - crop.top * scale,
        )
        clipRect {
            withTransform({
                translate(left = offset.x, top = offset.y)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                drawImage(image = map, alpha = 0.34f)
            }
        }

        if (point != null) {
            val marker = Offset(
                x = point.x * scale + offset.x,
                y = point.y * scale + offset.y,
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
