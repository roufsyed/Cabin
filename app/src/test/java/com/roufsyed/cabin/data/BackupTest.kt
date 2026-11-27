package com.roufsyed.cabin.data

import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.BarSide
import com.roufsyed.cabin.model.BrightnessMode
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.model.HomePanel
import com.roufsyed.cabin.model.ScreenOffShows
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.ThemeId
import com.roufsyed.cabin.model.TuckAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {

    private val chosen = CabinSettings(
        loaded = true,
        appOrder = listOf("com.a/.Main", "com.b/.Main"),
        appHidden = setOf("com.c/.Main"),
        appCache = listOf(AppEntry("com.a/.Main", "A", Glyph.Letter)),
        sourceOrder = listOf(SourceId.Usb, SourceId.Radio, SourceId.Bluetooth, SourceId.Auto, SourceId.Aux),
        sourceHidden = setOf(SourceId.Aux, SourceId.Auto),
        bindings = mapOf(Slot.Music to "com.a/.Main", Slot.Maps to "com.m/.Maps"),
        activeSource = SourceId.Usb,
        lastPlayer = "com.a",
        brightnessMode = BrightnessMode.Night,
        dayLevel = 9,
        nightLevel = 2,
        followHeadlights = false,
        nightHours = true,
        nightStart = 20 * 60 + 30,
        dayStart = 5 * 60,
        setupDone = true,
        swipeDownTray = false,
        sideSwipe = SideSwipe.Tracks,
        twoFingerDrag = TwoFingerDrag.Brightness,
        longPressSplit = false,
        theme = ThemeId.Teal,
        animations = true,
        dayColours = false,
        screenOff = ScreenOffShows(clock = false, date = true, playing = false, turn = true, status = false),
        sideBar = false,
        sideBarGranted = true,
        barHandle = false,
        barTuck = TuckAfter.Never,
        barSide = BarSide.Left,
        homeOrder = listOf(HomePanel.NowPlaying, HomePanel.Clock, HomePanel.Navigation),
        latitude = 12.97,
        longitude = 77.59,
        recentPlaces = listOf("Airport", "City Mall"),
    )

    private val here = CabinSettings(
        loaded = true,
        appCache = listOf(AppEntry("com.h/.Main", "H", Glyph.Letter)),
        bindings = mapOf(Slot.Phone to "com.p/.Dial"),
        activeSource = SourceId.Radio,
        lastPlayer = "com.here",
        setupDone = false,
        theme = ThemeId.Ruby,
        sideBarGranted = false,
        latitude = 1.0,
        longitude = 2.0,
    )

    @Test
    fun `a backup holds what was chosen and leaves out what Cabin only observed`() {
        val values = SettingsCodec.portable(chosen)

        for (observed in listOf("app_cache", "active_source", "last_player", "setup_done", "side_bar_granted", "position")) {
            assertFalse(observed, observed in values)
        }
        assertEquals("com.a/.Main\ncom.b/.Main", values["app_order"])
        assertEquals(setOf("com.c/.Main"), values["app_hidden"])
        assertEquals("Usb,Radio,Bluetooth,Auto,Aux", values["source_order"])
        assertEquals("com.a/.Main", values["bind_music"])
        assertEquals("com.m/.Maps", values["bind_maps"])
        assertFalse("bind_phone" in values)
        assertEquals("Teal", values["theme"])
        assertEquals(9, values["brightness_day"])
        assertEquals(true, values["night_hours"])
        assertEquals(20 * 60 + 30, values["night_start"])
        assertEquals("Never", values["side_bar_tuck"])
        assertEquals("Airport\nCity Mall", values["recent_places"])
        assertTrue(values.values.all { it is String || it is Int || it is Boolean || it is Set<*> })
    }

    @Test
    fun `restoring brings back every choice and keeps what this unit observed`() {
        val restored = SettingsCodec.restored(SettingsCodec.portable(chosen), here)

        assertEquals(
            chosen.copy(
                appCache = here.appCache,
                activeSource = here.activeSource,
                lastPlayer = here.lastPlayer,
                setupDone = here.setupDone,
                sideBarGranted = here.sideBarGranted,
                latitude = here.latitude,
                longitude = here.longitude,
            ),
            restored,
        )
    }

    @Test
    fun `a setting the backup does not have goes back to its default`() {
        val values = SettingsCodec.portable(chosen) - "theme" - "night_start" - "app_hidden" - "source_hidden" - "bind_maps"
        val restored = SettingsCodec.restored(values, chosen)

        assertEquals(ThemeId.Blue, restored.theme)
        assertEquals(19 * 60, restored.nightStart)
        assertEquals(emptySet<String>(), restored.appHidden)
        assertEquals(setOf(SourceId.Aux), restored.sourceHidden)
        assertEquals(mapOf(Slot.Music to "com.a/.Main"), restored.bindings)
        assertEquals(chosen.dayStart, restored.dayStart)
    }

    @Test
    fun `an empty backup leaves a unit with the defaults and what it observed`() {
        val restored = SettingsCodec.restored(emptyMap(), here)

        assertEquals(
            CabinSettings(
                loaded = true,
                appCache = here.appCache,
                activeSource = here.activeSource,
                lastPlayer = here.lastPlayer,
                latitude = here.latitude,
                longitude = here.longitude,
            ),
            restored,
        )
    }

    @Test
    fun `values of the wrong kind, out of range or unknown are not taken`() {
        val values = SettingsCodec.portable(chosen) + mapOf(
            "brightness_day" to "9",
            "animations" to "yes",
            "app_hidden" to "com.c/.Main",
            "theme" to "Mauve",
            "night_start" to 5000,
            "brightness_night" to 99,
            "bind_music" to "",
            "source_hidden" to setOf("Aux", "Nowhere", 7),
            "something_new" to true,
            "setup_done" to true,
            "side_bar_granted" to true,
            "position" to "9.0,9.0",
            "last_player" to "com.other",
        )
        val restored = SettingsCodec.restored(values, CabinSettings(loaded = true))

        assertEquals(8, restored.dayLevel)
        assertFalse(restored.animations)
        assertEquals(emptySet<String>(), restored.appHidden)
        assertEquals(ThemeId.Blue, restored.theme)
        assertEquals(19 * 60, restored.nightStart)
        assertEquals(10, restored.nightLevel)
        assertEquals(mapOf(Slot.Maps to "com.m/.Maps"), restored.bindings)
        assertEquals(setOf(SourceId.Aux), restored.sourceHidden)
        assertFalse(restored.setupDone)
        assertFalse(restored.sideBarGranted)
        assertNull(restored.latitude)
        assertNull(restored.lastPlayer)
        assertEquals(chosen.sideSwipe, restored.sideSwipe)
    }

    @Test
    fun `the file is named after the day it was made`() {
        assertEquals("Cabin-backup-2026-11-27.json", BackupRules.fileName(LocalDate.of(2026, 11, 27)))
        assertEquals("Cabin-backup-2027-01-05.json", BackupRules.fileName(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun `the line under the title says what happened and what a restore would do`() {
        val found = Backup(savedAtMs = 0L, version = "0.2.0", values = emptyMap())

        assertEquals("Every setting in one file, kept wherever you choose", BackupRules.hint(BackupUi(), null))
        assertEquals("Backup saved", BackupRules.hint(BackupUi(BackupNote.Saved), null))
        assertEquals(
            "The backup could not be written there. Try another place.",
            BackupRules.hint(BackupUi(BackupNote.SaveFailed), null),
        )
        assertEquals(
            "This unit has no file picker to choose a place with",
            BackupRules.hint(BackupUi(BackupNote.NoPicker), null),
        )
        assertEquals("That file is not a Cabin backup", BackupRules.hint(BackupUi(BackupNote.NotABackup), null))
        assertEquals(
            "That backup is from a newer Cabin. Update Cabin first.",
            BackupRules.hint(BackupUi(BackupNote.TooNew), null),
        )
        assertEquals("That file could not be read", BackupRules.hint(BackupUi(BackupNote.ReadFailed), null))
        assertEquals(
            "Settings restored. Permissions are not in a backup: see Setup.",
            BackupRules.hint(BackupUi(BackupNote.Restored), null),
        )
        assertEquals(
            "From 27 Nov, 4:40 PM. Restoring replaces every setting here.",
            BackupRules.hint(BackupUi(found = found), "27 Nov, 4:40 PM"),
        )
        assertEquals(
            "Restoring this backup replaces every setting here",
            BackupRules.hint(BackupUi(found = found), null),
        )
        for (note in BackupNote.entries) assertTrue(note.name, BackupRules.hint(BackupUi(note), null).length <= 64)
    }
}
