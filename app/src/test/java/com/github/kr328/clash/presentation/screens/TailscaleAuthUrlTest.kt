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

    @Test fun parsesTailscaleStatusTransitions() {
        assertEquals(TailscaleStatus.NeedsLogin, parseTailscaleStatus("[Tailscale] Switching ipn state NoState -> NeedsLogin"))
        assertEquals(TailscaleStatus.Starting, parseTailscaleStatus("[Tailscale] Switching ipn state NeedsLogin -> Starting"))
        assertEquals(TailscaleStatus.Running, parseTailscaleStatus("[Tailscale] Switching ipn state Starting -> Running"))
        assertEquals(TailscaleStatus.LoggedOut, parseTailscaleStatus("[Tailscale] You are logged out"))
    }

    @Test fun extractsTailscaleAccount() {
        assertEquals("user@example.com", extractTailscaleAccount("[Tailscale] active login: user@example.com"))
        assertNull(extractTailscaleAccount("[Tailscale] netcheck: ok"))
    }

    @Test fun generatesReadableHostname() {
        assertEquals("glonetx-xiaomi-23127pn0cc", generateTailscaleHostname("tailscale", "Xiaomi 23127PN0CC"))
        assertEquals("glonetx-tailscale", generateTailscaleHostname("tailscale", ""))
        assertEquals("glonetx-node", generateTailscaleHostname("", ""))
    }
}
