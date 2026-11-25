package com.github.kr328.clash.util

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AppUpdateTest {
    @Test fun prereleaseUsesItsAssetVersion() {
        assertEquals("12.12.0.3", AppUpdate.releaseVersion("Prerelease-alpha", "cmfa-12.12.0.3-alpha-arm64-v8a-release.apk"))
        assertNull(AppUpdate.releaseVersion("Prerelease-alpha", "cmfa-12.12.0.3-meta-arm64-v8a-release.apk"))
        assertNull(AppUpdate.releaseVersion("unexpected", "glonetx-12.12.2-alpha-arm64-v8a-release.apk"))
        assertEquals("12.12.1", AppUpdate.releaseVersion("v12.12.1", "glonetx-12.12.1-meta-arm64-v8a-release.apk"))
    }
    @Test fun versionComparisonHandlesAlphaAndRejectsMalformedVersions() {
        assertTrue(AppUpdate.isNewer("12.12.0.3.Alpha", "12.12.1"))
        assertTrue(AppUpdate.isNewer("12.12.0.Alpha", "12.12.0.3"))
        assertTrue(AppUpdate.isNewer("12.12.0.Alpha.debug", "12.12.0.3"))
        assertFalse(AppUpdate.isNewer("12.12.1", "12.12.1"))
        assertFalse(AppUpdate.isNewer("12.12.1", "Prerelease-alpha"))
        assertFalse(AppUpdate.isNewer("broken", "12.12.1"))
    }

    @Test fun checkResultDistinguishesFoundNoneAndFailed() {
        val release = AppUpdate.Release(
            tagName = "v12.12.2",
            versionName = "12.12.2",
            apkUrl = "https://example.com/update.apk",
            apkSize = 1,
            notes = "",
            apkSha256 = "0".repeat(64),
        )
        val found = AppUpdate.checkUpdateResult { release }
        assertTrue(found is AppUpdate.CheckResult.Found)
        assertEquals(release, (found as AppUpdate.CheckResult.Found).release)

        assertSame(AppUpdate.CheckResult.None, AppUpdate.checkUpdateResult { null })

        val failure = IOException("offline")
        val failed = AppUpdate.checkUpdateResult { throw failure }
        assertTrue(failed is AppUpdate.CheckResult.Failed)
        assertSame(failure, (failed as AppUpdate.CheckResult.Failed).error)
    }

    @Test fun missingUpdateResourceIsFailureRatherThanNoUpdate() {
        val failed = AppUpdate.checkUpdateResult {
            AppUpdate.requireUpdateResource<String?>(null, "missing assets")
            null
        }

        assertTrue(failed is AppUpdate.CheckResult.Failed)
        assertEquals("missing assets", (failed as AppUpdate.CheckResult.Failed).error.message)
    }
}
