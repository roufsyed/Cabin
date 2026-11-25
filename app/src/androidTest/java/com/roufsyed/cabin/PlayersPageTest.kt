package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.media.MediaSource
import com.roufsyed.cabin.media.PlayerFacts
import com.roufsyed.cabin.media.PlayerRules
import com.roufsyed.cabin.model.Samples
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Two players at once, the second of them the unit's own kind: a title, no length, and no screen of its own. */
private class TwoPlayers(inner: FixedMedia) : MediaSource by inner {
    private val first = inner.players().single()

    override fun players(): List<PlayerFacts> = listOf(
        first,
        first.copy(
            packageName = "com.unit.media", appLabel = "Unit player", shown = false,
            state = PlayerRules.STATE_PAUSED, positionMs = 0, speed = 0f,
            actions = PlayerRules.TRANSPORT or PlayerRules.SEEK_TO,
            durationMs = null, details = listOf("android.media.metadata.TITLE", "length=215"),
            screen = false,
        ),
    )
}

/** Setup > Players: what each running player tells Cabin about itself. */
@RunWith(AndroidJUnit4::class)
class PlayersPageTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = TwoPlayers(FixedMedia(Samples.playing))) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()
    private fun text(text: String) = cabin.onNodeWithText(text).assertIsDisplayed()

    private fun openPlayers() {
        tap("All apps")
        tap("Settings")
        tap("Open setup")
        tap("Next setup page")
        tap("What the players tell Cabin")
    }

    @Test
    fun eachPlayerHasAPageSayingWhatItTellsCabin() {
        openPlayers()
        text("Bluetooth audio")
        text("Player 1 of 2, the one Cabin shows")
        shows("App: design.music")
        shows("A tap opens: Its own screen")
        shows("State: Playing, at 1:24, speed 0.0")
        shows("Length: 3:41")
        shows("Can be told to: Play, pause, next, previous (0x36)")
        shows("Track details: title, artist, duration")
        shows("Also sends: Nothing")

        tap("Next player")
        text("Unit player")
        text("Player 2 of 2")
        shows("App: com.unit.media")
        shows("A tap opens: The music app from Setup: it has no screen of its own")
        shows("State: Paused, at 0:00, speed 0.0")
        shows("Length: Not given")
        shows("Can be told to: Play, pause, next, previous, move within the track (0x136)")
        shows("Track details: title, length=215")

        // The button goes round, and a swipe turns the pages as well.
        tap("Next player")
        text("Bluetooth audio")
        cabin.onRoot().performTouchInput {
            advanceEventTime(1_000)
            swipe(Offset(centerX + 200f, centerY), Offset(centerX - 200f, centerY), durationMillis = 200)
        }
        cabin.waitForIdle()
        text("Unit player")
    }

    @Test
    fun doneLeadsBackToSetupsSecondPage() {
        openPlayers()
        tap("Next player")
        tap("Back to setup")
        shows("Choose the music app")
        shows("What the players tell Cabin")
        gone("Allow location")
    }
}

/** The same page with nothing to show on it. */
@RunWith(AndroidJUnit4::class)
class PlayersPageWithNoPlayerTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = FixedMedia(Samples.playing, ownScreen = false)) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val media get() = values.container.media as FixedMedia

    @Test
    fun aPlayerThatStopsOrStartsShowsWithinAMoment() {
        for (step in listOf("All apps", "Settings", "Open setup", "Next setup page", "What the players tell Cabin")) {
            cabin.onNodeWithContentDescription(step).performClick()
        }
        // One player, so there is no page to turn to.
        cabin.onNodeWithText("Player 1 of 1, the one Cabin shows").assertIsDisplayed()
        cabin.onNodeWithContentDescription("Next player").assertDoesNotExist()
        // It gives no screen of its own, and is one of the apps in the grid.
        cabin.onNodeWithContentDescription("A tap opens: Its app").assertIsDisplayed()

        media.set(null)
        cabin.waitUntil(5_000) { cabin.onAllNodesWithText("No player is running").fetchSemanticsNodes().isNotEmpty() }
        cabin.onNodeWithText("Players").assertIsDisplayed()

        media.set(Samples.playing.copy(packageName = "com.unit.media"))
        cabin.waitUntil(5_000) { cabin.onAllNodesWithText("Bluetooth audio").fetchSemanticsNodes().isNotEmpty() }
        cabin.onNodeWithContentDescription("A tap opens: The music app from Setup: it has no screen of its own").assertIsDisplayed()
    }
}

/** And before Cabin has been given notification access, which is what lets it see players at all. */
@RunWith(AndroidJUnit4::class)
class PlayersPageWithoutAccessTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = FixedMedia(null, hasAccess = false)) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    @Test
    fun thePageSaysWhatIsMissing() {
        for (step in listOf("All apps", "Settings", "Open setup", "Next setup page", "What the players tell Cabin")) {
            cabin.onNodeWithContentDescription(step).performClick()
        }
        cabin.onNodeWithText("Cabin cannot see the players yet").assertIsDisplayed()
        cabin.onNodeWithText("Allow notification access on Setup's first page.").assertIsDisplayed()
    }
}
