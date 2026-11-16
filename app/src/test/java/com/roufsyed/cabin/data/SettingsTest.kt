package com.roufsyed.cabin.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    /** A settings store on [file], as a fresh process would open it. */
    private fun open(file: File): DataStoreSettings {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return DataStoreSettings(store, scope)
    }

    private fun DataStoreSettings.loaded(): CabinSettings = runBlocking { settings.first { it.loaded } }

    @Test
    fun `a new install has the design's defaults`() {
        val settings = open(File(folder.root, "new.preferences_pb")).loaded()

        assertEquals(SourceId.entries.toList(), settings.sourceOrder)
        assertEquals(setOf(SourceId.Aux), settings.sourceHidden)
        assertEquals(BrightnessMode.Auto, settings.brightnessMode)
        assertEquals(8, settings.dayLevel)
        assertEquals(3, settings.nightLevel)
        assertTrue(settings.appOrder.isEmpty())
        assertTrue(settings.appHidden.isEmpty())
        assertFalse(settings.setupDone)
        assertNull(settings.activeSource)
        // Gestures do what the design gives them, and Auto goes by the headlights where it can.
        assertTrue(settings.swipeDownTray)
        assertEquals(SideSwipe.Apps, settings.sideSwipe)
        assertEquals(TwoFingerDrag.Volume, settings.twoFingerDrag)
        assertTrue(settings.longPressSplit)
        assertTrue(settings.followHeadlights)
        assertEquals(ThemeId.Blue, settings.theme)
        // The screen-off screen is the design's: the time and the date, and nothing else.
        assertEquals(ScreenOffShows(clock = true, date = true, playing = false, turn = false, status = false), settings.screenOff)
        // The bar over other apps is wanted from the start; it shows once Android allows it.
        assertTrue(settings.sideBar)
        assertFalse(settings.sideBarGranted)
        assertTrue(settings.barHandle)
        // Left untouched, it tucks itself away after five seconds.
        assertEquals(TuckAfter.FiveSeconds, settings.barTuck)
        // The side bar is on the right and Home's panels are in the design's order.
        assertEquals(BarSide.Right, settings.barSide)
        assertEquals(listOf(HomePanel.Clock, HomePanel.Navigation, HomePanel.NowPlaying), settings.homeOrder)
    }

    @Test
    fun `every setting survives a restart`() {
        val file = File(folder.root, "saved.preferences_pb")
        val saved = CabinSettings(
            loaded = true,
            appOrder = listOf("com.b/.Main", "com.a/.Main"),
            appHidden = setOf("com.a/.Main"),
            appCache = listOf(
                AppEntry("com.a/.Main", "Alpha", Glyph.Music),
                AppEntry("com.b/.Main", "Beta\twith tab", Glyph.Letter),
            ),
            sourceOrder = listOf(SourceId.Usb, SourceId.Radio, SourceId.Aux, SourceId.Bluetooth, SourceId.Auto),
            sourceHidden = setOf(SourceId.Radio),
            bindings = mapOf(
                Slot.Radio to "com.radio/.Main", Slot.Phone to "com.phone/.Dial",
                Slot.Maps to "com.maps/.Main", Slot.Music to "com.music/.Main",
            ),
            activeSource = SourceId.Usb,
            lastPlayer = "com.music",
            brightnessMode = BrightnessMode.Night,
            dayLevel = 10,
            nightLevel = 1,
            followHeadlights = false,
            setupDone = true,
            swipeDownTray = false,
            sideSwipe = SideSwipe.Tracks,
            twoFingerDrag = TwoFingerDrag.Brightness,
            longPressSplit = false,
            theme = ThemeId.Amber,
            screenOff = ScreenOffShows(clock = false, date = true, playing = true, turn = false, status = true),
            sideBar = false,
            sideBarGranted = true,
            barHandle = false,
            barTuck = TuckAfter.Immediate,
            barSide = BarSide.Left,
            homeOrder = listOf(HomePanel.NowPlaying, HomePanel.Clock, HomePanel.Navigation),
            latitude = 12.97,
            longitude = 77.59,
            recentPlaces = listOf("Airport", "City Mall"),
        )

        val first = open(file)
        first.loaded()
        runBlocking {
            first.update { saved }
            // Close the first store completely, as a process exit would, before opening the file again.
            scopes.removeAt(0).coroutineContext[Job]!!.cancelAndJoin()
        }

        val restored = open(file).loaded()
        assertEquals(saved.copy(appCache = saved.appCache.map { it.copy(label = it.label.replace('\t', ' ')) }), restored)
    }

    @Test
    fun `an update changes only what it touches`() {
        val store = open(File(folder.root, "partial.preferences_pb"))
        store.loaded()
        runBlocking {
            store.update { it.copy(appHidden = setOf("x")) }
            store.update { it.copy(dayLevel = 5) }
        }
        val settings = runBlocking { store.settings.first { it.dayLevel == 5 } }
        assertEquals(setOf("x"), settings.appHidden)
        assertEquals(3, settings.nightLevel)
    }

    @Test
    fun `damaged values fall back to defaults instead of failing`() {
        val file = File(folder.root, "damaged.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        runBlocking {
            raw.edit {
                it[stringPreferencesKey("source_order")] = "Usb,Nonsense,Radio"
                it[stringPreferencesKey("brightness_mode")] = "Dusk"
                it[intPreferencesKey("brightness_day")] = 99
                it[stringPreferencesKey("position")] = "not,numbers"
                it[stringPreferencesKey("app_cache")] = "broken line\ncom.a/.Main\tWhat\tAlpha"
                it[stringPreferencesKey("gesture_side")] = "Diagonal"
                it[stringPreferencesKey("theme")] = "Tartan"
                it[stringPreferencesKey("gesture_two_finger")] = ""
                it[stringPreferencesKey("side_bar_side")] = "Top"
                it[stringPreferencesKey("side_bar_tuck")] = "Soon"
                it[stringPreferencesKey("home_order")] = "Clock,Clock,Weather"
            }
        }
        val settings = SettingsCodec.decode(runBlocking { raw.data.first() })
        assertEquals(BarSide.Right, settings.barSide)
        assertEquals(TuckAfter.FiveSeconds, settings.barTuck)
        assertEquals(listOf(HomePanel.Clock, HomePanel.Navigation, HomePanel.NowPlaying), settings.homeOrder)
        assertEquals(SideSwipe.Apps, settings.sideSwipe)
        assertEquals(TwoFingerDrag.Volume, settings.twoFingerDrag)
        assertEquals(ThemeId.Blue, settings.theme)

        // Known sources keep their saved place; the rest are appended so none can go missing.
        assertEquals(
            listOf(SourceId.Usb, SourceId.Radio, SourceId.Bluetooth, SourceId.Auto, SourceId.Aux),
            settings.sourceOrder,
        )
        assertEquals(BrightnessMode.Auto, settings.brightnessMode)
        assertEquals(10, settings.dayLevel)
        assertNull(settings.latitude)
        assertEquals(listOf(AppEntry("com.a/.Main", "Alpha", Glyph.Letter)), settings.appCache)
    }

    @Test
    fun `a value left by an earlier build is ignored and dropped on the next save`() {
        val file = File(folder.root, "older.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val retired = stringPreferencesKey("swipe_action")
        runBlocking { raw.edit { it[retired] = "Pages" } }

        val store = DataStoreSettings(raw, scope)
        assertEquals(SideSwipe.Apps, store.loaded().sideSwipe)
        runBlocking { store.update { it.copy(dayLevel = 6) } }
        assertNull(runBlocking { raw.data.first() }[retired])
    }

    @Test
    fun `a tap on an option's tile moves it to the next one and round again`() {
        assertEquals(SideSwipe.Tracks, SideSwipe.Apps.next())
        assertEquals(SideSwipe.Off, SideSwipe.Tracks.next())
        assertEquals(SideSwipe.Apps, SideSwipe.Off.next())
        assertEquals(TwoFingerDrag.Brightness, TwoFingerDrag.Volume.next())
        assertEquals(TwoFingerDrag.Volume, TwoFingerDrag.Off.next())
    }
}
