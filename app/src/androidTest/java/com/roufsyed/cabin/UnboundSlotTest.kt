package com.roufsyed.cabin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A unit whose apps Cabin cannot recognise: every slot starts empty and has to be given an app. */
@RunWith(AndroidJUnit4::class)
class UnboundSlotTest {

    private val unknownApps = FixedApps(
        listOf(
            AppEntry("vendor.a/.Main", "Tuner", Glyph.Letter),
            AppEntry("vendor.b/.Main", "Handsfree", Glyph.Letter),
            AppEntry(FixedApps.id("Settings"), "Settings", Glyph.Settings),
        ),
    )

    @get:Rule(order = 0)
    val values = FreshDesignValues { design ->
        AppContainer(
            scope = design.scope,
            settings = InMemorySettings(),
            apps = unknownApps,
            clock = design.clock,
            homeRole = design.homeRole,
            media = FixedMedia(null),
        )
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()

    @Test
    fun aSourceWithNoAppLeadsToTheChooserAndThenWorks() {
        tap("Radio source")
        cabin.onNodeWithText("Radio opens").assertIsDisplayed()
        tap("Tuner")
        cabin.waitUntil(2_000) { values.container.settings.settings.value.bindings[Slot.Radio] == "vendor.a/.Main" }

        tap("Radio source")
        cabin.waitForIdle()
        assertEquals(listOf("vendor.a/.Main"), unknownApps.launched)
    }

    @Test
    fun aRailButtonWithNoAppLeadsToTheChooser() {
        tap("Phone")
        cabin.onNodeWithText("Phone opens").assertIsDisplayed()
        tap("Handsfree")
        tap("Phone")
        cabin.waitForIdle()
        assertEquals(listOf("vendor.b/.Main"), unknownApps.launched)
    }

    @Test
    fun withNothingPlayingThePanelSaysSo() {
        cabin.onNodeWithText("Nothing playing").assertIsDisplayed()
    }
}
