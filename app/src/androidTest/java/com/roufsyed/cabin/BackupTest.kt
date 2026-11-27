package com.roufsyed.cabin

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.data.Backup
import com.roufsyed.cabin.data.BackupFile
import com.roufsyed.cabin.data.BackupNote
import com.roufsyed.cabin.data.BackupRead
import com.roufsyed.cabin.data.BackupRules
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.SettingsCodec
import com.roufsyed.cabin.data.SideSwipe
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.ThemeId
import com.roufsyed.cabin.system.AndroidBackupDocuments
import com.roufsyed.cabin.system.BackupChooser
import com.roufsyed.cabin.system.FixedBackupDocuments
import com.roufsyed.cabin.system.Opened
import com.roufsyed.cabin.system.Saved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackupFileTest {

    private val saved = ZonedDateTime.of(2026, 11, 27, 16, 40, 12, 0, ZoneId.of("Asia/Kolkata"))

    private val values = mapOf<String, Any>(
        "animations" to true,
        "day_colours" to false,
        "brightness_day" to 9,
        "theme" to "Teal",
        "app_order" to "com.a/.Main\ncom.b/.Main",
        "app_hidden" to setOf("com.d/.Main", "com.c/.Main"),
        "source_hidden" to emptySet<String>(),
        "recent_places" to "Café \"Zoë\"\tदिल्ली \\ / end\nSecond place",
        "bind_music" to "com.player/.ui.Main",
        "bind_maps" to "a\\/b\\\\/c//d\\",
    )

    private fun written(values: Map<String, Any> = this.values, version: String = "0.2.0"): String =
        BackupFile.write(Backup(saved.toInstant().toEpochMilli(), version, values), saved.zone)

    private fun found(text: String): Backup = (BackupFile.read(text) as BackupRead.Found).backup

    @Test
    fun whatIsWrittenIsReadBackTheSame() {
        val text = written()
        val read = found(text)

        assertEquals(values, read.values)
        assertEquals("0.2.0", read.version)
        assertEquals(saved.toInstant().toEpochMilli(), read.savedAtMs)
        assertTrue(text, text.contains("\"saved\": \"2026-11-27T16:40:12+05:30\""))
        assertTrue(text, text.contains("\"app\": \"Cabin\""))
        assertTrue(text, text.contains("\"format\": 1"))
        assertTrue(text, text.contains("\"bind_music\": \"com.player/.ui.Main\""))
        assertTrue(text, text.indexOf("\"animations\"") < text.indexOf("\"theme\""))
    }

    @Test
    fun everySettingSurvivesTheFile() {
        val chosen = CabinSettings(
            loaded = true,
            appOrder = listOf("com.a/.Main", "com.b/.Main"),
            appHidden = setOf("com.c/.Main"),
            bindings = mapOf(Slot.Music to "com.a/.Main"),
            dayLevel = 9,
            nightHours = true,
            nightStart = 21 * 60,
            sideSwipe = SideSwipe.Tracks,
            theme = ThemeId.Teal,
            animations = true,
            recentPlaces = listOf("Airport", "City Mall"),
        )
        val read = found(written(SettingsCodec.portable(chosen)))

        assertEquals(SettingsCodec.portable(chosen), read.values)
        assertEquals(chosen, SettingsCodec.restored(read.values, CabinSettings(loaded = true, theme = ThemeId.Ruby)))
    }

    @Test
    fun otherFilesAreNotBackups() {
        val others = listOf(
            "",
            "hello",
            "[1, 2]",
            "{}",
            "{\"app\": \"Cabin\"}",
            "{\"app\": \"Other\", \"kind\": \"backup\", \"format\": 1, \"settings\": {}}",
            "{\"app\": \"Cabin\", \"kind\": \"notes\", \"format\": 1, \"settings\": {}}",
            "{\"app\": \"Cabin\", \"kind\": \"backup\", \"format\": 1}",
            "{\"app\": \"Cabin\", \"kind\": \"backup\", \"format\": 0, \"settings\": {}}",
            "{\"app\": \"Cabin\", \"kind\": \"backup\", \"format\": 1, \"settings\": [1]}",
            "{\"app\": \"Cabin\", \"kind\": \"backup\", \"format\": 1, \"settings\": {",
            "{\"a\":".repeat(50_000),
            "\u0000\u0001\u0002 not text at all �",
        )
        for (text in others) assertEquals(text.take(60), BackupRead.NotABackup, BackupFile.read(text))
    }

    @Test
    fun aBackupOfANewerKindIsToldApart() {
        val newer = written().replace("\"format\": 1", "\"format\": 2")

        assertEquals(BackupRead.TooNew, BackupFile.read(newer))
    }

    @Test
    fun valuesOfKindsABackupNeverHoldsAreLeftOut() {
        val text = "{\"app\": \"Cabin\", \"kind\": \"backup\", \"format\": 1, \"more\": [1], \"settings\": {" +
            "\"half\": 1.5, \"nothing\": null, \"inner\": {\"x\": 1}, \"big\": 12345678901, " +
            "\"mixed\": [1, \"x\", true, null, \"y\"], \"theme\": \"Teal\", \"brightness_day\": 9, \"animations\": false}}"
        val read = found(text)

        assertEquals(
            mapOf("mixed" to setOf("x", "y"), "theme" to "Teal", "brightness_day" to 9, "animations" to false),
            read.values,
        )
        assertNull(read.savedAtMs)
        assertEquals("", read.version)
    }
}

@RunWith(AndroidJUnit4::class)
class BackupDocumentsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val documents = AndroidBackupDocuments(context, CoroutineScope(Dispatchers.Default))
    private val file = File(context.cacheDir, "backup-test.json")
    private val document = Uri.fromFile(file)

    private fun <T> answer(ask: ((T) -> Unit) -> Unit): T {
        val answers = LinkedBlockingQueue<T>()
        ask { answers.put(it) }
        return answers.poll(5, TimeUnit.SECONDS) ?: error("no answer came")
    }

    @Test
    fun aDocumentIsWrittenAndReadBack() {
        file.delete()

        assertTrue(documents.write(document, "héllo दिल्ली"))
        assertEquals(Opened.Text("héllo दिल्ली"), documents.read(document))

        assertTrue(documents.write(document, "shorter"))
        assertEquals(Opened.Text("shorter"), documents.read(document))
        file.delete()
    }

    @Test
    fun aFileOverTheLimitIsRefusedAndOneThatIsGoneCannotBeRead() {
        file.writeBytes(ByteArray(1024 * 1024) { 'a'.code.toByte() })
        assertEquals(1024 * 1024, (documents.read(document) as Opened.Text).text.length)

        file.writeBytes(ByteArray(1024 * 1024 + 1) { 'a'.code.toByte() })
        assertEquals(Opened.TooBig, documents.read(document))

        file.delete()
        assertEquals(Opened.Failed, documents.read(document))
        assertEquals(Opened.Failed, documents.read(Uri.parse("content://nowhere.at.all/backup")))
        assertFalse(documents.write(Uri.parse("content://nowhere.at.all/backup"), "text"))
    }

    @Test
    fun withNoPickerNothingIsAskedAndTheAnswerSaysSo() {
        assertEquals(Saved.NoPicker, answer<Saved> { documents.save("a.json", "text", it) })
        assertEquals(Opened.NoPicker, answer<Opened> { documents.open(it) })

        documents.chooser = BackupChooser(create = { false }, open = { false })
        assertEquals(Saved.NoPicker, answer<Saved> { documents.save("a.json", "text", it) })
        assertEquals(Opened.NoPicker, answer<Opened> { documents.open(it) })
    }

    @Test
    fun theChosenPlaceIsWrittenToAndTheChosenFileRead() {
        val asked = mutableListOf<String>()
        documents.chooser = BackupChooser(create = { asked += it; true }, open = { asked += "open"; true })
        file.delete()

        assertEquals(Saved.Done, answer<Saved> { documents.save("Cabin-backup.json", "the text", it); documents.created(document) })
        assertEquals("the text", file.readText())
        assertEquals(Opened.Text("the text"), answer<Opened> { documents.open(it); documents.opened(document) })
        assertEquals(listOf("Cabin-backup.json", "open"), asked)

        assertEquals(Saved.Cancelled, answer<Saved> { documents.save("b.json", "other", it); documents.created(null) })
        assertEquals("the text", file.readText())
        assertEquals(Opened.Cancelled, answer<Opened> { documents.open(it); documents.opened(null) })

        documents.created(document)
        documents.opened(document)
        assertEquals("the text", file.readText())
        file.delete()
    }
}

@RunWith(AndroidJUnit4::class)
class BackupPageTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val documents get() = values.container.backups as FixedBackupDocuments
    private val settings get() = values.container.settings.settings.value
    private val chosen get() = SettingsCodec.portable(settings)

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()
    private fun says(text: String) = cabin.onNodeWithText(text).assertIsDisplayed()

    private fun change(transform: (CabinSettings) -> CabinSettings) {
        runBlocking { values.container.settings.update(transform) }
        cabin.waitForIdle()
    }

    private fun openThePage() {
        for (step in listOf("All apps", "Settings", "Open setup", "Next setup page", "Back up or restore Cabin's settings")) {
            tap(step)
        }
    }

    @Test
    fun setupLeadsToBackupAndRestoreAndDoneLeadsBack() {
        openThePage()
        says("Backup and restore")
        says("Every setting in one file, kept wherever you choose")
        shows("Back up Cabin's settings")
        shows("Restore Cabin's settings")
        gone("Restore this backup")

        tap("Back to setup")
        shows("Back up or restore Cabin's settings")
        shows("Choose the music app")
    }

    @Test
    fun aBackupIsSavedUnderTheDaysNameAndHoldsEverySetting() {
        change { it.copy(theme = ThemeId.Teal, sideSwipe = SideSwipe.Tracks, dayLevel = 9, nightHours = true) }
        openThePage()
        tap("Back up Cabin's settings")
        says("Backup saved")

        assertEquals(BackupRules.fileName(LocalDate.now()), documents.name)
        val read = (BackupFile.read(documents.file.orEmpty()) as BackupRead.Found).backup
        assertEquals(chosen, read.values)
        assertEquals("Teal", read.values["theme"])
        assertEquals(BuildConfig.VERSION_NAME, read.version)
        assertTrue(System.currentTimeMillis() - (read.savedAtMs ?: 0L) in 0..60_000)
    }

    @Test
    fun restoringAsksFirstAndThenBringsEverySettingBack() {
        change { it.copy(theme = ThemeId.Teal, sideSwipe = SideSwipe.Tracks, dayLevel = 9, nightHours = true, nightStart = 21 * 60) }
        val saved = chosen
        openThePage()
        tap("Back up Cabin's settings")
        says("Backup saved")
        change {
            it.copy(
                theme = ThemeId.Ruby, sideSwipe = SideSwipe.Off, dayLevel = 4, nightHours = false,
                bindings = it.bindings + (Slot.Music to FixedApps.id("Radio")), appHidden = setOf(FixedApps.id("Radio")),
            )
        }
        val changed = chosen
        assertTrue(saved != changed)

        tap("Restore Cabin's settings")
        shows("Restore this backup")
        shows("Cancel the restore")
        gone("Back up Cabin's settings")
        cabin.onNodeWithText("Restoring replaces every setting here.", substring = true).assertIsDisplayed()
        assertEquals(changed, chosen)

        tap("Cancel the restore")
        shows("Back up Cabin's settings")
        says("Every setting in one file, kept wherever you choose")
        assertEquals(changed, chosen)

        tap("Restore Cabin's settings")
        tap("Restore this backup")
        cabin.waitUntil(2_000) { values.container.backup.ui.value.note == BackupNote.Restored }
        says("Settings restored. Permissions are not in a backup: see Setup.")
        shows("Back up Cabin's settings")
        gone("Restore this backup")
        assertEquals(saved, chosen)
        assertEquals(ThemeId.Teal, settings.theme)
        assertTrue(settings.setupDone)
    }

    @Test
    fun aFileThatIsNoBackupOrFromANewerCabinChangesNothing() {
        openThePage()
        val before = chosen

        documents.file = "{\"name\": \"something else\"}"
        tap("Restore Cabin's settings")
        says("That file is not a Cabin backup")
        shows("Back up Cabin's settings")
        gone("Restore this backup")

        documents.file = BackupFile.write(Backup(null, "9.0.0", mapOf("theme" to "Ruby"))).replace("\"format\": 1", "\"format\": 2")
        tap("Restore Cabin's settings")
        says("That backup is from a newer Cabin. Update Cabin first.")
        gone("Restore this backup")

        documents.file = null
        tap("Restore Cabin's settings")
        says("That file could not be read")
        assertEquals(before, chosen)
    }

    @Test
    fun aUnitWithNoFilePickerIsToldSoAndAPlaceThatCannotBeWrittenToo() {
        openThePage()
        documents.picker = false
        tap("Back up Cabin's settings")
        says("This unit has no file picker to choose a place with")
        tap("Restore Cabin's settings")
        says("This unit has no file picker to choose a place with")

        documents.picker = true
        documents.writable = false
        tap("Back up Cabin's settings")
        says("The backup could not be written there. Try another place.")
        assertNull(documents.file)
    }

    @Test
    fun leavingThePickerOrThePageWithoutChoosingChangesNothing() {
        openThePage()
        tap("Back up Cabin's settings")
        says("Backup saved")

        documents.cancels = true
        tap("Back up Cabin's settings")
        says("Every setting in one file, kept wherever you choose")
        tap("Restore Cabin's settings")
        says("Every setting in one file, kept wherever you choose")
        gone("Restore this backup")

        documents.cancels = false
        tap("Restore Cabin's settings")
        shows("Restore this backup")
        tap("Back to setup")
        tap("Back up or restore Cabin's settings")
        shows("Back up Cabin's settings")
        gone("Restore this backup")
        says("Every setting in one file, kept wherever you choose")
    }
}
