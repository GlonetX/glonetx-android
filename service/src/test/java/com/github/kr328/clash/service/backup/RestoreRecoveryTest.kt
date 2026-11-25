package com.github.kr328.clash.service.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreRecoveryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun orphanedRollbackWithoutJournalIsRemoved() {
        val root = temporaryFolder.newFolder("backup-maintenance")
        val rollback = File(root, "rollback").apply { mkdirs() }
        File(rollback, "snapshot.json").writeText("stale")
        File(rollback, "imported/profile").apply { parentFile?.mkdirs(); writeText("stale") }
        val jobs = File(root, "jobs").apply { mkdirs() }

        BackupArchive.cleanupOrphanedRollback(root)

        assertFalse(rollback.exists())
        assertTrue(jobs.isDirectory)
    }

    @Test fun missingOrphanedRollbackIsAlreadyClean() {
        val root = temporaryFolder.newFolder("backup-maintenance")

        BackupArchive.cleanupOrphanedRollback(root)

        assertFalse(File(root, "rollback").exists())
    }
}
