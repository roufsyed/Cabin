package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.nav.FixedNavigation
import com.roufsyed.cabin.nav.FixedVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Navigation panel with a route, as the design draws it. */
@RunWith(AndroidJUnit4::class)
class NavigationWithRouteTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val navigation get() = values.container.navigation as FixedNavigation

    @Test
    fun thePanelShowsTheNextTurnAndOpensMapsWhenTapped() {
        for (text in listOf("350 m", "Ring Road", "Arrive 10:48", "38 min · 17 km")) {
            cabin.onNodeWithText(text).assertIsDisplayed()
        }
        cabin.onNodeWithContentDescription("Open Maps").performClick()
        cabin.waitForIdle()
        assertEquals(listOf("open"), navigation.calls)
    }

    @Test
    fun aRoadNameTooLongForTheCardIsCutAndNeverRunsIntoTheLinesBelow() {
        val road = "Bengaluru - Mumbai Hwy / Mumbai Hwy / Mumbai - Pandharpur Rd / Mumbai - Pune Hwy / " +
            "Mumbai - Satara Hwy / NH 48 / Asian Highway 47"
        val arrive = "Arrive 10:48 tomorrow morning after the long way round"
        val remaining = "38 min · 17 km and a good deal more besides that"
        cabin.runOnUiThread { navigation.set(NavUi.Active("500 m", road, arrive, remaining)) }
        cabin.waitForIdle()

        fun box(text: String) = cabin.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = cabin.onNodeWithContentDescription("Open Maps").fetchSemanticsNode().boundsInRoot
        val scale = card.width / 326f
        val name = box(road)
        val distance = box("500 m")

        assertTrue("road $name under distance $distance", name.top >= distance.bottom)
        assertTrue("road $name over arrival ${box(arrive)}", name.bottom <= box(arrive).top)
        assertTrue("three lines at most: ${name.height / scale}", name.height / scale <= 3 * 34 * 1.2f + 1f)
        assertTrue("arrival ${box(arrive)} over remaining ${box(remaining)}", box(arrive).bottom <= box(remaining).top)
        for (text in listOf(road, arrive, remaining)) {
            val line = box(text)
            assertTrue("$line inside $card", line.left >= card.left && line.right <= card.right && line.bottom <= card.bottom)
        }
        assertTrue("one line each", box(arrive).height / scale <= 34f && box(remaining).height / scale <= 34f)
    }

    @Test
    fun thePanelFollowsTheRouteAndGoesIdleWhenItEnds() {
        cabin.runOnUiThread {
            navigation.set(NavUi.Active("1.2 km", "Airport Road", "Arrive 11:02", "52 min · 34 km"))
        }
        cabin.onNodeWithText("1.2 km").assertIsDisplayed()
        cabin.onNodeWithText("Airport Road").assertIsDisplayed()

        cabin.runOnUiThread { navigation.set(NavUi.Idle) }
        cabin.onNodeWithContentDescription("Where to?").assertIsDisplayed()
        cabin.onNodeWithContentDescription("Navigate home").assertIsDisplayed()
        cabin.onNodeWithText("Airport Road").assertDoesNotExist()
    }
}

/** The Navigation panel with no route: "Where to?" and the two saved places. */
@RunWith(AndroidJUnit4::class)
class NavigationIdleTest {

    private val voice = FixedVoice(heard = "Airport Road")

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(navigation = FixedNavigation(NavUi.Idle), voice = voice) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val navigation get() = values.container.navigation as FixedNavigation
    private val recents get() = values.container.settings.settings.value.recentPlaces

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()

    @Test
    fun homeAndOfficeUseThePlacesSavedInTheMapsApp() {
        tap("Navigate home")
        tap("Navigate to office")
        cabin.waitForIdle()
        assertEquals(listOf("saved:Home", "saved:Work"), navigation.calls)
    }

    @Test
    fun theCardAroundTheMicOpensTheMap() {
        cabin.onNodeWithText("Where to?", useUnmergedTree = true).performClick()
        cabin.waitForIdle()
        assertEquals(listOf("open"), navigation.calls)
        cabin.onNodeWithText("Listening…").assertDoesNotExist()

        cabin.onNodeWithText("Navigation", useUnmergedTree = true).performClick()
        cabin.onNodeWithContentDescription("Open Maps").performTouchInput { click(Offset(width - 20f, height / 2f)) }
        cabin.waitForIdle()
        assertEquals(listOf("open", "open", "open"), navigation.calls)
        assertTrue(!voice.listening)
    }

    @Test
    fun onlyTheMicListens() {
        tap("Where to?")
        cabin.onNodeWithText("Listening…").assertIsDisplayed()
        assertTrue(voice.listening)
        assertEquals(emptyList<String>(), navigation.calls)
    }

    @Test
    fun aTapJustBesideTheMicsRingIsStillTheMic() {
        cabin.onNodeWithContentDescription("Where to?").performTouchInput { click(Offset(4f, 4f)) }
        cabin.onNodeWithText("Listening…").assertIsDisplayed()
        assertEquals(emptyList<String>(), navigation.calls)
    }

    @Test
    fun whereToListensThenNavigatesToWhatWasSaid() {
        tap("Where to?")
        cabin.onNodeWithText("Listening…").assertIsDisplayed()
        assertTrue(voice.listening)

        cabin.runOnUiThread { voice.finish() }
        cabin.waitUntil(2_000) { navigation.calls == listOf("to:Airport Road") }
        cabin.onNodeWithText("Listening…").assertDoesNotExist()
        cabin.waitUntil(2_000) { recents == listOf("Airport Road", "Airport") }
    }

    @Test
    fun aRecentPlaceCanBePickedInsteadOfSpoken() {
        tap("Where to?")
        tap("Navigate to City Mall")
        cabin.waitForIdle()
        assertEquals(listOf("to:City Mall"), navigation.calls)
        cabin.onNodeWithText("Listening…").assertDoesNotExist()
        cabin.waitUntil(2_000) { recents == listOf("City Mall", "Airport") }
    }

    @Test
    fun cancelAndATapOutsideBothCloseTheDialogWithoutNavigating() {
        tap("Where to?")
        tap("Cancel")
        cabin.onNodeWithText("Listening…").assertDoesNotExist()
        tap("Where to?")
        // Outside the dialog, on the dimmed screen.
        cabin.onNodeWithContentDescription("Close").performTouchInput { click(Offset(60f, 60f)) }
        cabin.onNodeWithText("Listening…").assertDoesNotExist()
        assertEquals(emptyList<String>(), navigation.calls)
    }
}

/** What "Where to?" does when the unit cannot help. */
@RunWith(AndroidJUnit4::class)
class NavigationFallbackTest {

    private val deaf = FixedVoice(heard = null)

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(navigation = FixedNavigation(NavUi.Idle), voice = deaf) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val navigation get() = values.container.navigation as FixedNavigation

    @Test
    fun whenNothingIsUnderstoodTheDialogSaysSoAndCanListenAgain() {
        cabin.onNodeWithContentDescription("Where to?").performClick()
        cabin.runOnUiThread { deaf.finish() }
        cabin.onNodeWithText("Didn’t catch that").assertIsDisplayed()
        assertEquals(emptyList<String>(), navigation.calls)

        cabin.onNodeWithContentDescription("Listen again").performClick()
        cabin.onNodeWithText("Listening…").assertIsDisplayed()
        assertTrue(deaf.listening)
    }
}

@RunWith(AndroidJUnit4::class)
class NavigationWithoutSpeechTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        it.copy(navigation = FixedNavigation(NavUi.Idle), voice = FixedVoice(available = false))
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    @Test
    fun withNoSpeechRecogniserWhereToOpensTheMapsApp() {
        cabin.onNodeWithContentDescription("Where to?").performClick()
        cabin.waitForIdle()
        assertEquals(listOf("open"), (values.container.navigation as FixedNavigation).calls)
        cabin.onNodeWithText("Listening…").assertDoesNotExist()
    }
}

/** A unit on which Cabin can find no maps app: the Navigation panel leads to the chooser instead. */
@RunWith(AndroidJUnit4::class)
class NavigationWithoutMapsAppTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        it.copy(navigation = FixedNavigation(NavUi.Idle, hasMapsApp = false), voice = FixedVoice(available = false))
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val settings get() = values.container.settings.settings.value

    @Test
    fun aSavedPlaceAndWhereToBothLeadToTheChooser() {
        cabin.onNodeWithContentDescription("Navigate home").performClick()
        cabin.onNodeWithText("Maps opens").assertIsDisplayed()
        cabin.onNodeWithContentDescription("Browser").performClick()
        cabin.waitUntil(2_000) { settings.bindings[Slot.Maps] == FixedApps.id("Browser") }

        // Back on Home. With no speech recogniser "Where to?" opens the maps app, so it asks too.
        cabin.onNodeWithContentDescription("Where to?").performClick()
        cabin.onNodeWithText("Maps opens").assertIsDisplayed()
        cabin.onNodeWithContentDescription("Back").performClick()
        cabin.onNodeWithContentDescription("Where to?").assertIsDisplayed()
    }
}
