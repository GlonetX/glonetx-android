package com.github.kr328.clash.presentation.screens

import com.github.kr328.clash.core.model.IPQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpQualityScoreTest {
    @Test fun cleanIpScoresHigh() {
        val quality = IPQuality(
            ip = "203.0.113.10",
            scamalyticsScore = "0",
            scamalyticsRisk = "Low Risk",
            proxyCheckRisk = "0",
            ipApiProxy = "false",
            ipApiHosting = "false",
            ipApiMobile = "false",
        )

        assertEquals(100, calculateIpQualityScore(quality)!!.value)
        assertEquals(3, calculateIpQualityScore(quality)!!.sourceCount)
    }

    @Test fun riskyIpScoresLower() {
        val quality = IPQuality(
            ip = "203.0.113.10",
            scamalyticsScore = "80",
            proxyCheckRisk = "80",
            ipApiProxy = "true",
            ipApiHosting = "true",
        )

        val score = calculateIpQualityScore(quality)!!.value
        assertEquals(29, score)
    }

    @Test fun missingDataHasNoScore() {
        assertNull(calculateIpQualityScore(null))
        assertNull(calculateIpQualityScore(IPQuality(ip = "203.0.113.10")))
    }
}
