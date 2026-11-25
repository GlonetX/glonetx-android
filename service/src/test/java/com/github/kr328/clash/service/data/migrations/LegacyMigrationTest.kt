package com.github.kr328.clash.service.data.migrations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMigrationTest {
    @Test fun successfulSupportedMigrationAllowsLegacyCleanup() {
        listOf(1, 2, 3, 4).forEach { version ->
            assertTrue(canDeleteLegacyData(version, true))
        }
    }

    @Test fun failedMigrationPreservesLegacyData() {
        listOf(1, 2, 3, 4).forEach { version ->
            assertFalse(canDeleteLegacyData(version, false))
        }
    }

    @Test fun unknownOrMissingVersionPreservesLegacyData() {
        listOf(null, 0, 5, Int.MAX_VALUE).forEach { version ->
            assertFalse(canDeleteLegacyData(version, true))
        }
    }
}
