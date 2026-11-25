package com.github.kr328.clash.presentation.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TailscaleAuthUrlTest {
    @Test fun extractsInteractiveLoginUrlFromTsnetLog() {
        val message = "[Tailscale](tailscale) To start this tsnet server, restart with TS_AUTHKEY set, or go to: https://login.tailscale.com/a/123456"
        assertEquals("https://login.tailscale.com/a/123456", extractTailscaleAuthUrl(message))
    }

    @Test fun extractsHeadscaleUrlFromAuthMessage() {
        val message = "To start this tsnet server, restart with TS_AUTHKEY set, or go to: https://headscale.example.com/auth/abc"
        assertEquals("https://headscale.example.com/auth/abc", extractTailscaleAuthUrl(message))
    }

    @Test fun ignoresUnrelatedUrls() {
        assertNull(extractTailscaleAuthUrl("Fetching https://example.com/geoip.dat"))
    }
}
