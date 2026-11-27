package com.roufsyed.cabin.data

import com.roufsyed.cabin.system.BackupDocuments
import com.roufsyed.cabin.system.Opened
import com.roufsyed.cabin.system.Saved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class Backup(val savedAtMs: Long?, val version: String, val values: Map<String, Any>)

sealed interface BackupRead {
    data class Found(val backup: Backup) : BackupRead
    data object TooNew : BackupRead
    data object NotABackup : BackupRead
}

object BackupFile {
    const val MIME = "application/json"
    const val FORMAT = 1
    private const val APP = "Cabin"
    private const val KIND = "backup"

    fun write(backup: Backup, zone: ZoneId = ZoneId.systemDefault()): String {
        val settings = JSONObject()
        for ((name, value) in backup.values.toSortedMap()) {
            settings.put(name, if (value is Set<*>) JSONArray(value.map { it.toString() }.sorted()) else value)
        }
        val saved = backup.savedAtMs?.let {
            Instant.ofEpochMilli(it).atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS).toString()
        }
        return JSONObject()
            .put("app", APP)
            .put("kind", KIND)
            .put("format", FORMAT)
            .put("cabin", backup.version)
            .put("saved", saved)
            .put("settings", settings)
            .toString(2)
            .replace("\\/", "/")
    }

    fun read(text: String): BackupRead {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return BackupRead.NotABackup
        } catch (e: StackOverflowError) {
            return BackupRead.NotABackup
        }
        val format = root.optInt("format", 0)
        val settings = root.optJSONObject("settings")
        if (root.optString("app") != APP || root.optString("kind") != KIND || format < 1 || settings == null) {
            return BackupRead.NotABackup
        }
        if (format > FORMAT) return BackupRead.TooNew
        val values = buildMap<String, Any> {
            for (name in settings.keys()) {
                when (val value = settings.opt(name)) {
                    is Boolean, is Int, is String -> put(name, value)
                    is JSONArray -> put(name, (0 until value.length()).mapNotNull { value.opt(it) as? String }.toSet())
                    else -> Unit
                }
            }
        }
        val saved = try {
            OffsetDateTime.parse(root.optString("saved")).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
        return BackupRead.Found(Backup(saved, root.optString("cabin"), values))
    }
}

enum class BackupNote { None, Saved, SaveFailed, NoPicker, NotABackup, TooNew, ReadFailed, Restored }

data class BackupUi(val note: BackupNote = BackupNote.None, val found: Backup? = null)

object BackupRules {
    fun fileName(date: LocalDate): String = "Cabin-backup-$date.json"

    fun hint(ui: BackupUi, saved: String?): String = when {
        ui.found != null && saved != null -> "From $saved. Restoring replaces every setting here."
        ui.found != null -> "Restoring this backup replaces every setting here"
        else -> when (ui.note) {
            BackupNote.None -> "Every setting in one file, kept wherever you choose"
            BackupNote.Saved -> "Backup saved"
            BackupNote.SaveFailed -> "The backup could not be written there. Try another place."
            BackupNote.NoPicker -> "This unit has no file picker to choose a place with"
            BackupNote.NotABackup -> "That file is not a Cabin backup"
            BackupNote.TooNew -> "That backup is from a newer Cabin. Update Cabin first."
            BackupNote.ReadFailed -> "That file could not be read"
            BackupNote.Restored -> "Settings restored. Permissions are not in a backup: see Setup."
        }
    }
}

class BackupController(
    private val settings: SettingsStore,
    private val documents: BackupDocuments,
    private val scope: CoroutineScope,
    private val version: String,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {
    private val state = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = state

    fun backUp() {
        val at = now()
        val backup = Backup(at.toInstant().toEpochMilli(), version, SettingsCodec.portable(settings.settings.value))
        documents.save(BackupRules.fileName(at.toLocalDate()), BackupFile.write(backup, at.zone)) { saved ->
            state.value = BackupUi(
                when (saved) {
                    Saved.Done -> BackupNote.Saved
                    Saved.Cancelled -> BackupNote.None
                    Saved.Failed -> BackupNote.SaveFailed
                    Saved.NoPicker -> BackupNote.NoPicker
                },
            )
        }
    }

    fun choose() {
        documents.open { opened ->
            state.value = when (opened) {
                Opened.Cancelled -> BackupUi()
                Opened.NoPicker -> BackupUi(BackupNote.NoPicker)
                Opened.Failed -> BackupUi(BackupNote.ReadFailed)
                Opened.TooBig -> BackupUi(BackupNote.NotABackup)
                is Opened.Text -> when (val read = BackupFile.read(opened.text)) {
                    is BackupRead.Found -> BackupUi(found = read.backup)
                    BackupRead.TooNew -> BackupUi(BackupNote.TooNew)
                    BackupRead.NotABackup -> BackupUi(BackupNote.NotABackup)
                }
            }
        }
    }

    fun restore() {
        val found = state.value.found ?: return
        scope.launch {
            settings.update { SettingsCodec.restored(found.values, it) }
            state.value = BackupUi(BackupNote.Restored)
        }
    }

    fun cancel() {
        state.value = BackupUi()
    }
}
