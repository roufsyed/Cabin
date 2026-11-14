package com.roufsyed.cabin

import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.nav.MapsNavigation
import com.roufsyed.cabin.nav.PlaceShortcut
import com.roufsyed.cabin.nav.SavedPlace
import com.roufsyed.cabin.nav.ShortcutHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the real navigation class sends to the maps app. Nothing is actually opened: every activity
 * start made by the app is caught here and recorded instead.
 */
@RunWith(AndroidJUnit4::class)
class MapsNavigationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val started = mutableListOf<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            synchronized(started) { started += Intent(intent) }
            return Instrumentation.ActivityResult(0, null)
        }
    }

    /** Stands in for the launcher's shortcuts, which only the unit's real home app can read. */
    private class FakeShortcuts : ShortcutHost {
        val published = mutableMapOf<String, List<PlaceShortcut>>()
        val startedShortcuts = mutableListOf<String>()

        override fun shortcuts(packageName: String): List<PlaceShortcut> = published[packageName].orEmpty()

        override fun start(packageName: String, id: String): Boolean {
            synchronized(startedShortcuts) { startedShortcuts += "$packageName $id" }
            return true
        }
    }

    private val google = AppEntry("com.google.android.apps.maps/.Main", "Maps", Glyph.Maps)
    private val atlas = AppEntry("org.example.atlas/.Main", "Atlas", Glyph.Maps)
    private val apps = FixedApps(listOf(atlas, AppEntry("org.example.notes/.Main", "Notes", Glyph.Letter), google))
    private val settings = InMemorySettings(CabinSettings(loaded = true))
    private val shortcuts = FakeShortcuts()
    private val navigation =
        MapsNavigation(ApplicationProvider.getApplicationContext(), scope, apps, settings, shortcuts)

    @Before
    fun setUp() = instrumentation.addMonitor(monitor)

    @After
    fun tearDown() {
        instrumentation.removeMonitor(monitor)
        scope.cancel()
    }

    private fun choose(app: AppEntry) = settings.let { store ->
        kotlinx.coroutines.runBlocking { store.update { it.copy(bindings = it.bindings + (Slot.Maps to app.id)) } }
    }

    /** Waits for something that happens off the calling thread. */
    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun sent(): List<String> = synchronized(started) { started.map { "${it.`package`} ${it.dataString}" } }

    @Test
    fun aDestinationGoesToGoogleMapsAsNavigationWhenNothingElseIsChosen() {
        assertTrue(navigation.navigateTo(" City Mall "))
        assertEquals(listOf("com.google.android.apps.maps google.navigation:q=City%20Mall"), sent())
        assertEquals(Intent.ACTION_VIEW, started.single().action)
        assertTrue(started.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(emptyList<String>(), apps.launched)
    }

    @Test
    fun aDestinationGoesToTheChosenMapsAppAsAMapAddress() {
        choose(atlas)
        assertTrue(navigation.navigateTo("City Mall"))
        assertEquals(listOf("org.example.atlas geo:0,0?q=City%20Mall"), sent())
    }

    @Test
    fun openingMapsOpensTheChosenApp() {
        assertTrue(navigation.openMaps())
        choose(atlas)
        assertTrue(navigation.openMaps())
        assertEquals(listOf(google.id, atlas.id), apps.launched)
        assertEquals(emptyList<String>(), sent())
    }

    @Test
    fun aSavedPlaceUsesTheMapsAppsOwnShortcutWhenItPublishesOne() {
        shortcuts.published[google.id.substringBefore('/')] = listOf(
            PlaceShortcut("manifest_work", "Work"), PlaceShortcut("manifest_home", "Home"),
        )
        assertTrue(navigation.navigateTo(SavedPlace.Home))
        await("the Home shortcut") { shortcuts.startedShortcuts.size == 1 }
        assertTrue(navigation.navigateTo(SavedPlace.Work))
        await("the Work shortcut") { shortcuts.startedShortcuts.size == 2 }
        assertEquals(
            listOf("com.google.android.apps.maps manifest_home", "com.google.android.apps.maps manifest_work"),
            shortcuts.startedShortcuts,
        )
        assertEquals(emptyList<String>(), sent())
        assertEquals(emptyList<String>(), apps.launched)
    }

    @Test
    fun aSavedPlaceInGoogleMapsFallsBackToItsNavigationAddress() {
        // No shortcuts: Cabin is not the home app, or this Maps build publishes none.
        assertTrue(navigation.navigateTo(SavedPlace.Home))
        await("the Home intent") { sent().isNotEmpty() }
        assertTrue(navigation.navigateTo(SavedPlace.Work))
        await("the Work intent") { sent().size == 2 }
        assertEquals(
            listOf(
                "com.google.android.apps.maps google.navigation:place=home",
                "com.google.android.apps.maps google.navigation:place=work",
            ),
            sent(),
        )
        assertEquals(emptyList<String>(), shortcuts.startedShortcuts)
    }

    @Test
    fun aSavedPlaceInAnotherMapsAppUsesItsShortcutOrElseOpensIt() {
        choose(atlas)
        shortcuts.published["org.example.atlas"] = listOf(PlaceShortcut("search", "Search"), PlaceShortcut("go1", "Drive home"))
        assertTrue(navigation.navigateTo(SavedPlace.Home))
        await("the Home shortcut") { shortcuts.startedShortcuts.isNotEmpty() }
        assertEquals(listOf("org.example.atlas go1"), shortcuts.startedShortcuts)
        assertEquals(emptyList<String>(), apps.launched)

        // It has nothing for Work, and the navigation address is Google's alone, so the app is opened.
        assertTrue(navigation.navigateTo(SavedPlace.Work))
        await("the app to be opened") { apps.launched.isNotEmpty() }
        assertEquals(listOf(atlas.id), apps.launched)
        assertEquals(emptyList<String>(), sent())
    }

    @Test
    fun withNoMapsAppNothingIsSentAndTheCallerIsTold() {
        val bare = MapsNavigation(
            ApplicationProvider.getApplicationContext(), scope,
            FixedApps(listOf(AppEntry("org.example.notes/.Main", "Notes", Glyph.Letter))), settings, shortcuts,
        )
        assertFalse(bare.openMaps())
        assertFalse(bare.navigateTo("City Mall"))
        assertFalse(bare.navigateTo(SavedPlace.Home))
        assertFalse(navigation.navigateTo("   "))
        assertEquals(emptyList<String>(), sent())
    }
}
