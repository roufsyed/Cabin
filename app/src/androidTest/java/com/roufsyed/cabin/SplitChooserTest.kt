package com.roufsyed.cabin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.ui.split.SplitChooserActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The chooser the side bar's Split button opens, told that Maps is the app on screen. It is given the
 * design's fifteen apps and seventeen more, so that there is a second page. Here it has the whole
 * screen; that Android puts it in half of one, beside the app in front, is checked by hand (see
 * NOTES.md).
 */
@RunWith(AndroidJUnit4::class)
class SplitChooserTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { design ->
        val more = (1..17).map { AppEntry(FixedApps.id("Extra $it"), "Extra $it", Glyph.Letter) }
        design.copy(apps = FixedApps(FixedApps.designApps + more))
    }

    @get:Rule(order = 1)
    val chooser = AndroidComposeTestRule(
        activityRule = ActivityScenarioRule<SplitChooserActivity>(
            SplitChooserActivity.intent(ApplicationProvider.getApplicationContext(), listOf("design.maps")),
        ),
        activityProvider = { rule ->
            var activity: SplitChooserActivity? = null
            rule.scenario.onActivity { activity = it }
            activity!!
        },
    )

    private val apps get() = values.container.apps as FixedApps

    private fun shows(description: String) = chooser.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = chooser.onNodeWithContentDescription(description).assertDoesNotExist()

    @Test
    fun itOffersTheGridsAppsWithoutCabinAndWithoutTheAppOnScreen() {
        chooser.onNodeWithText("Split screen").assertIsDisplayed()
        shows("Browser")
        shows("Clock")
        // Maps is on screen already, and Settings is Cabin's own tile.
        gone("Maps")
        gone("Settings")
    }

    @Test
    fun aSwipeTurnsItsPages() {
        // Thirty apps, fifteen to a page on a whole screen.
        chooser.onNodeWithText("Page 1 of 2. Swipe for more.").assertIsDisplayed()
        shows("Browser")
        gone("Extra 17")
        chooser.onRoot().performTouchInput { swipeLeft() }
        chooser.onNodeWithText("Page 2 of 2. Swipe for more.").assertIsDisplayed()
        shows("Extra 17")
        gone("Browser")
        // There is no page after the last, nor before the first.
        chooser.onRoot().performTouchInput { swipeLeft() }
        shows("Extra 17")
        chooser.onRoot().performTouchInput { swipeRight() }
        shows("Browser")
        chooser.onRoot().performTouchInput { swipeRight() }
        shows("Browser")
    }

    @Test
    fun aTapOpensTheAppFromTheChooserAndClosesIt() {
        chooser.onNodeWithContentDescription("Clock").performClick()
        chooser.waitUntil(3_000) { chooser.activityRule.scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(listOf(FixedApps.id("Clock")), apps.launched)
    }

    @Test
    fun backClosesItWithoutOpeningAnything() {
        chooser.onNodeWithContentDescription("Cancel split screen").performClick()
        chooser.waitUntil(3_000) { chooser.activityRule.scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(emptyList<String>(), apps.launched)
    }
}
