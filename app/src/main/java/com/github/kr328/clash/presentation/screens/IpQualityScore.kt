package com.github.kr328.clash.presentation.screens

import com.github.kr328.clash.core.model.IPQuality
import kotlin.math.roundToInt

data class IpQualityScore(
    val value: Int,
    val sourceCount: Int,
)

fun calculateIpQualityScore(quality: IPQuality?): IpQualityScore? {
    if (quality == null) return null
    var sourceCount = 0
    var score = 100f

    quality.scamalyticsScore?.toFloatOrNull()?.let { fraudScore ->
        sourceCount++
        score -= fraudScore.coerceIn(0f, 100f) * 0.45f
    } ?: quality.scamalyticsRisk?.let { risk ->
        sourceCount++
        score -= when (risk.lowercase()) {
            "very high risk", "very high" -> 35f
            "high risk", "high" -> 25f
            "medium risk", "medium" -> 12f
            else -> 0f
        }
    }

    quality.proxyCheckRisk?.toFloatOrNull()?.let { risk ->
        sourceCount++
        score -= risk.coerceIn(0f, 100f) * 0.25f
    }

    val proxy = quality.ipApiProxy?.equals("true", ignoreCase = true) == true
    val hosting = quality.ipApiHosting?.equals("true", ignoreCase = true) == true
    if (quality.ipApiProxy != null || quality.ipApiHosting != null || quality.ipApiMobile != null) {
        sourceCount++
    }
    if (proxy) score -= 10f
    if (hosting) score -= 5f

    if (sourceCount == 0) return null
    return IpQualityScore(score.coerceIn(0f, 100f).roundToInt(), sourceCount)
}
