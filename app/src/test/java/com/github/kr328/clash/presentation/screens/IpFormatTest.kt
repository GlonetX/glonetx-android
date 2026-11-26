package com.github.kr328.clash.presentation.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class IpFormatTest {
    @Test fun compressesIpv6Addresses() {
        assertEquals("2001:db8::1", simplifyIpAddress("2001:0db8:0000:0000:0000:0000:0000:0001"))
        assertEquals("::1", simplifyIpAddress("0:0:0:0:0:0:0:1"))
        assertEquals("2001:db8:0:1::1", simplifyIpAddress("2001:db8:0:1:0:0:0:1"))
    }

    @Test fun keepsIpv4Unchanged() {
        assertEquals("143.246.51.44", simplifyIpAddress("143.246.51.44"))
        assertEquals("143.246.51.44", compactIpAddress("143.246.51.44"))
    }

    @Test fun abbreviatesLongIpv6AddressesWithBrackets() {
        assertEquals("[2602:fbf1:…:1000:9b]", compactIpAddress("2602:fbf1:b003::1000:9b"))
        assertEquals("[2001:db8:…:5:6]", compactIpAddress("2001:db8:1:2:3:4:5:6"))
    }

    @Test fun keepsShortIpv6AddressesReadable() {
        assertEquals("::1", compactIpAddress("::1"))
        assertEquals("2001:db8::1", compactIpAddress("2001:db8::1"))
    }
}
