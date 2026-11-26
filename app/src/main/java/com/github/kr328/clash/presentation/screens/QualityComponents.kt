package com.github.kr328.clash.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.IPQualitySource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RiskGaugeCard(
    title: String,
    value: String,
    caption: String?,
    progress: Float?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    higherIsBetter: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            RiskGauge(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(104.dp),
                higherIsBetter = higherIsBetter,
            )
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!caption.isNullOrBlank()) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun QualityScoreCard(score: IpQualityScore, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RiskGauge(
                progress = score.value / 100f,
                modifier = Modifier.size(118.dp),
                higherIsBetter = true,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text("IP purity", "IP 纯净度"), style = MaterialTheme.typography.titleMedium)
                Text("${score.value}/100", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    text("Combined from ${score.sourceCount} sources", "综合 ${score.sourceCount} 个数据源"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RiskGauge(progress: Float?, modifier: Modifier = Modifier, higherIsBetter: Boolean = false) {
    val target = progress?.coerceIn(0f, 1f) ?: 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 650),
        label = "riskNeedle",
    )
    val track = MaterialTheme.colorScheme.outlineVariant
    val low = Color(0xFF43A047)
    val medium = Color(0xFFF9A825)
    val high = Color(0xFFE53935)
    val startColor = if (higherIsBetter) high else low
    val middleColor = medium
    val endColor = if (higherIsBetter) low else high
    val needleColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier) {
        val stroke = 8.dp.toPx()
        val radius = (minOf(size.width, size.height * 2f) / 2f - stroke).coerceAtLeast(1f)
        val center = Offset(size.width / 2f, size.height - 8.dp.toPx())
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        drawArc(
            color = track,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(low, 180f, 60f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(medium, 240f, 60f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(high, 300f, 60f, false, topLeft, arcSize, style = Stroke(stroke))

        for (index in 0..4) {
            val angle = PI + index * PI / 4
            val inner = radius * 0.9f
            val outer = radius * 0.97f
            drawLine(
                color = track,
                start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        if (progress != null) {
            val angle = PI + animated * PI
            val needleLength = radius * 0.76f
            drawLine(
                color = needleColor,
                start = center,
                end = Offset(
                    center.x + cos(angle).toFloat() * needleLength,
                    center.y + sin(angle).toFloat() * needleLength,
                ),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(needleColor, radius = 4.dp.toPx(), center = center)
        }
    }
}

@Composable
fun QualityStatusPill(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    alertWhenTrue: Boolean = true,
) {
    val normalized = value?.trim()?.lowercase()
    val active = normalized == "true" || normalized == "yes"
    val color = when {
        normalized.isNullOrBlank() || normalized == "unknown" -> MaterialTheme.colorScheme.onSurfaceVariant
        active && alertWhenTrue -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val display = when (normalized) {
        "true", "yes" -> text("Yes", "是")
        "false", "no" -> text("No", "否")
        null, "" -> "—"
        else -> value.orEmpty()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                when {
                    normalized.isNullOrBlank() || normalized == "unknown" -> Icons.Default.HelpOutline
                    active && alertWhenTrue -> Icons.Default.WarningAmber
                    else -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Text("$label: $display", style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun QualitySourceCard(source: IPQualitySource, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(source.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                source.type?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            listOfNotNull(source.country, source.region, source.city).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                QualityDetailRow(text("Location", "位置"), it)
            }
            source.isp?.takeIf { it.isNotBlank() }?.let { QualityDetailRow(text("ISP", "运营商"), it) }
            source.asn?.takeIf { it.isNotBlank() }?.let { QualityDetailRow(text("ASN", "ASN"), it) }
            source.risk?.takeIf { it.isNotBlank() }?.let { QualityDetailRow(text("Risk", "风险"), it) }
            if (source.proxy != null || source.hosting != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    source.proxy?.let { QualityStatusPill(text("Proxy", "代理"), it, alertWhenTrue = true) }
                    source.hosting?.let { QualityStatusPill(text("Hosting", "托管"), it, alertWhenTrue = true) }
                }
            }
        }
    }
}

@Composable
fun QualityIpCard(
    title: String,
    ip: com.github.kr328.clash.core.model.ExternalIP?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (ip == null) {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ip.ip?.let {
                    QualityDetailRow(text("IP", "IP"), compactIpAddress(it))
                    QualityDetailRow(text("Version", "协议"), if (it.contains(':')) "IPv6" else "IPv4")
                }
                listOfNotNull(ip.country, ip.city).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    QualityDetailRow(text("Location", "位置"), it)
                }
                ip.isp?.let { QualityDetailRow(text("ISP", "运营商"), it) }
                ip.asn?.let { QualityDetailRow(text("ASN", "ASN"), it) }
            }
        }
    }
}

@Composable
fun QualityDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

fun riskPercent(value: String?, scale: Float): Float? =
    value?.toFloatOrNull()?.let { (it / scale).coerceIn(0f, 1f) }

fun formatRiskPercent(value: String?, scale: Float): String =
    value?.toFloatOrNull()?.let { "${((it / scale) * 100f).roundToInt()}%" } ?: "—"
