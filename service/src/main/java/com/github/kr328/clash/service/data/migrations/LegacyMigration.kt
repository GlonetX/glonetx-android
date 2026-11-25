@file:Suppress("BlockingMethodInNonBlockingContext")

package com.github.kr328.clash.service.data.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.text.isDigitsOnly
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import java.io.File
import java.util.UUID

internal suspend fun migrationFromLegacy(context: Context) {
    val file = context.getDatabasePath("clash-config")

    if (!file.exists()) {
        return
    }

    Log.i("Migration from legacy database")

    var version: Int? = null
    var migrationSucceeded = false
    val legacyFiles = linkedSetOf<File>()

    try {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            .use { db ->
                val v = db.version
                version = v

                Log.i("Legacy database version = $v")

                when (v) {
                    1 -> migrationFromLegacy1(context, db, legacyFiles)
                    2, 3, 4 -> migrationFromLegacy234(context, db, v)
                    else -> {
                        Log.w("Unsupported legacy database version = $v; preserving legacy data for retry")
                        return
                    }
                }

                migrationSucceeded = true
            }
    } catch (e: Exception) {
        Log.w("Migration legacy database failed; preserving legacy data for retry: $e", e)
        return
    }

    if (!canDeleteLegacyData(version, migrationSucceeded)) {
        return
    }

    if (!deleteLegacyArtifacts(context, version, legacyFiles)) {
        Log.w("Legacy data migrated but source cleanup failed; preserving legacy database for retry")
        return
    }

    if (!context.deleteDatabase("clash-config")) {
        Log.w("Failed to delete migrated legacy database; will retry")
        return
    }

    Log.i("Legacy database migrated")
}

internal fun canDeleteLegacyData(version: Int?, migrationSucceeded: Boolean): Boolean {
    return migrationSucceeded && version != null && version in 1..4
}

private suspend fun migrationFromLegacy234(
    context: Context,
    legacy: SQLiteDatabase,
    version: Int,
) {
    legacy.query(
        "profiles",
        arrayOf("id", "name", "type", "uri", if (version == 2) "update_interval" else "interval"),
        null,
        null,
        null,
        null,
        "id"
    ).use { cursor ->
        val id = cursor.getColumnIndex("id")
        val name = cursor.getColumnIndex("name")
        val type = cursor.getColumnIndex("type")
        val uri = cursor.getColumnIndex("uri")
        val interval = cursor.getColumnIndex(if (version == 2) "update_interval" else "interval")

        if (!cursor.moveToFirst())
            return

        do {
            val idValue = cursor.getInt(id)
            val newType = when (cursor.getInt(type)) {
                1 -> { // TYPE_FILE
                    Profile.Type.File
                }
                2 -> { // TYPE_URL
                    Profile.Type.Url
                }
                3 -> { // TYPE_EXTERNAL
                    Profile.Type.External
                }
                else -> {
                    error("Unsupported legacy profile type ${cursor.getInt(type)} for id $idValue")
                }
            }

            val intervalValue = cursor.getLong(interval)

            val pending = Pending(
                uuid = legacyProfileUUID(version, idValue),
                name = cursor.getString(name),
                type = newType,
                source = if (newType != Profile.Type.File) cursor.getString(uri) else "",
                interval = if (version == 2) intervalValue * 1000 else intervalValue,
                0,0,0,0
            )

            val legacyFile = if (newType == Profile.Type.File) {
                context.filesDir.resolve("profiles/$idValue.yaml")
            } else {
                null
            }

            migrateProfile(context, pending, legacyFile)

            Log.i("${pending.name} migrated")
        } while (cursor.moveToNext())
    }
}

private suspend fun migrationFromLegacy1(
    context: Context,
    legacy: SQLiteDatabase,
    legacyFiles: MutableSet<File>,
) {
    legacy.query(
        "profiles",
        arrayOf("name", "token", "id", "file"),
        null,
        null,
        null,
        null,
        "id",
    ).use { cursor ->
        val name = cursor.getColumnIndex("name")
        val token = cursor.getColumnIndex("token")
        val id = cursor.getColumnIndex("id")
        val file = cursor.getColumnIndex("file")

        if (!cursor.moveToFirst())
            return

        do {
            val idValue = cursor.getInt(id)
            val legacyToken = cursor.getString(token)

            val newType = when {
                legacyToken.startsWith("file|") -> Profile.Type.File
                legacyToken.startsWith("url|") -> Profile.Type.Url
                else -> {
                    error("Unsupported legacy profile token for id $idValue")
                }
            }

            val source = if (newType == Profile.Type.Url) {
                legacyToken.removePrefix("url|")
            } else {
                ""
            }

            val pending = Pending(
                uuid = legacyProfileUUID(1, idValue),
                name = cursor.getString(name),
                type = newType,
                source = source,
                interval = 0,
                0,0,0,0
            )

            val legacyFile = File(cursor.getString(file))

            migrateProfile(context, pending, legacyFile)
            legacyFiles.add(legacyFile)

            Log.i("${pending.name} migrated")
        } while (cursor.moveToNext())
    }
}

private suspend fun migrateProfile(context: Context, pending: Pending, legacyFile: File?) {
    val pendingDao = PendingDao()
    val existing = pendingDao.queryByUUID(pending.uuid)

    if (existing == null) {
        val imported = ImportedDao().queryByUUID(pending.uuid)
        if (imported != null) {
            if (imported.name != pending.name || imported.type != pending.type || imported.source != pending.source) {
                error("Legacy profile ${pending.uuid} conflicts with an existing imported profile")
            }

            Log.i("${pending.name} already migrated")
            return
        }
    } else if (!existing.matchesLegacy(pending)) {
        error("Legacy profile ${pending.uuid} conflicts with an existing pending profile")
    }

    val base = context.pendingDir.resolve(pending.uuid.toString())
    ensurePendingDirectory(base)

    if (pending.type == Profile.Type.File) {
        val source = requireNotNull(legacyFile) { "Missing legacy profile file for ${pending.uuid}" }
        val destination = base.resolve("config.yaml")

        if (source.isFile) {
            source.copyTo(destination, overwrite = true)
        } else if (!destination.isFile) {
            error("Missing legacy profile file: $source")
        }
    }

    if (existing == null) {
        pendingDao.insert(pending)
        context.sendProfileChanged(pending.uuid)
    }
}

private fun ensurePendingDirectory(base: File) {
    if (!base.isDirectory && !base.mkdirs()) {
        error("Unable to create legacy profile directory: $base")
    }

    val config = base.resolve("config.yaml")
    if (!config.isFile && !config.createNewFile()) {
        error("Unable to create legacy profile config: $config")
    }

    val providers = base.resolve("providers")
    if (!providers.isDirectory && !providers.mkdir()) {
        error("Unable to create legacy profile providers directory: $providers")
    }
}

private fun Pending.matchesLegacy(other: Pending): Boolean {
    return name == other.name &&
        type == other.type &&
        source == other.source &&
        interval == other.interval
}

private fun legacyProfileUUID(version: Int, id: Int): UUID {
    return UUID.nameUUIDFromBytes("legacy-profile:$version:$id".toByteArray(Charsets.UTF_8))
}

private fun deleteLegacyArtifacts(context: Context, version: Int?, legacyFiles: Set<File>): Boolean {
    var success = true

    legacyFiles.forEach { legacyFile ->
        if (legacyFile.exists() && !legacyFile.delete()) {
            Log.w("Failed to delete legacy profile file: $legacyFile")
            success = false
        }
    }

    val profiles = context.filesDir.resolve("profiles")
    if (!profiles.deleteRecursively()) {
        Log.w("Failed to delete legacy profiles directory: $profiles")
        success = false
    }

    if (version != null && version in 2..4) {
        context.filesDir.resolve("clash").listFiles()?.forEach { legacyDir ->
            if (legacyDir.name.isDigitsOnly() && !legacyDir.deleteRecursively()) {
                Log.w("Failed to delete legacy clash directory: $legacyDir")
                success = false
            }
        }
    }

    return success
}
