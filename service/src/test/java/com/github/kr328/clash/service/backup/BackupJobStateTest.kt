package com.github.kr328.clash.service.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJobStateTest {
    @Test fun readyPreviewExpiresAfterThirtyMinutes() {
        val now = 1_000_000L
        val job = BackupJob(id = "job", operation = "Restore", phase = "Ready", readyAt = now)

        assertFalse(BackupJobState.isReadyExpired(job, now + BackupJobState.READY_TTL_MILLIS - 1))
        assertTrue(BackupJobState.isReadyExpired(job, now + BackupJobState.READY_TTL_MILLIS))
    }

    @Test fun legacyReadyPreviewUsesCreationTime() {
        val now = 1_000_000L
        val job = BackupJob(id = "job", operation = "Restore", phase = "Ready", createdAt = now)

        assertFalse(BackupJobState.isReadyExpired(job, now + BackupJobState.READY_TTL_MILLIS - 1))
        assertTrue(BackupJobState.isReadyExpired(job, now + BackupJobState.READY_TTL_MILLIS))
    }

    @Test fun terminalJobsAreNotReadyPreviewExpirations() {
        val job = BackupJob(id = "job", operation = "Restore", phase = "Completed", readyAt = 0)

        assertFalse(BackupJobState.isReadyExpired(job, BackupJobState.READY_TTL_MILLIS))
    }
}
