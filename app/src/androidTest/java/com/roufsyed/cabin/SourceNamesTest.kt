package com.roufsyed.cabin

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceNamesTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(sourcesNamedAfterApps = true) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val apps get() = values.container.apps as FixedApps
    private val settings get() = values.container.settings.settings.value

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()
    private fun text(text: String) = cabin.onNodeWithText(text).assertIsDisplayed()

    private fun awaitSource(name: String) = cabin.waitUntil(3_000) {
        cabin.onAllNodesWithContentDescription("$name source").fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aSourceGivenAnAppIsCalledAfterThatApp() {
        awaitSource("Music")
        shows("Radio source")
        shows("USB source")
        shows("Android Auto source")
        gone("Bluetooth source")

        tap("All apps")
        tap("Settings")
        tap("Arrange sources")
        tap("Select USB")
        text("Opens Music")
        tap("Choose the app for this source")
        text("USB opens")
        tap("Video")

        awaitSource("Video")
        gone("USB source")
        text("USB: opens Video")
        assertEquals(FixedApps.id("Video"), settings.bindings[Slot.Usb])

        tap("Done arranging")
        tap("Home")
        shows("Video source")
        tap("Video source")
        cabin.waitForIdle()
        assertEquals(FixedApps.id("Video"), apps.launched.last())
    }

    @Test
    fun autoGivesASourceItsOwnNameBack() {
        awaitSource("Music")
        tap("All apps")
        tap("Settings")
        tap("Arrange sources")
        tap("Select Bluetooth")
        text("Bluetooth: opens Music")
        tap("Choose the app for this source")
        text("Bluetooth opens")
        tap("Choose automatically")

        awaitSource("Bluetooth")
        gone("Music source")
        text("No app chosen yet")
        assertEquals(null, settings.bindings[Slot.Bluetooth])
    }
}

private fun longNamed(label: String) = AppEntry("unit.${label.lowercase().filter { it.isLetter() }}/.Main", label, Glyph.Letter)

@RunWith(AndroidJUnit4::class)
class LongSourceNamesTest {

    private val names = listOf(
        "Internet Radio Player Professional", "Bluetooth Music And Calls Centre",
        "AICT Media Launcher For USB Drives", "Wireless Phone Projection Service",
    )
    private val unitApps = names.map(::longNamed)

    @get:Rule(order = 0)
    val values = FreshDesignValues { design ->
        design.copy(
            apps = FixedApps(FixedApps.designApps + unitApps),
            settings = InMemorySettings(
                CabinSettings(
                    loaded = true, setupDone = true,
                    bindings = mapOf(
                        Slot.Radio to unitApps[0].id, Slot.Bluetooth to unitApps[1].id,
                        Slot.Usb to unitApps[2].id, Slot.Auto to unitApps[3].id,
                    ),
                ),
            ),
            sourcesNamedAfterApps = true,
        )
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun inDesign(box: Rect): Rect {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        val left = (root.width - 1280f * scale) / 2f
        val top = (root.height - 800f * scale) / 2f
        return Rect((box.left - left) / scale, (box.top - top) / scale, (box.right - left) / scale, (box.bottom - top) / scale)
    }

    @Test
    fun namesTooLongForTheRowAreCutAndTheRowStaysWhole() {
        cabin.waitUntil(3_000) {
            cabin.onAllNodesWithContentDescription("${names[0]} source").fetchSemanticsNodes().isNotEmpty()
        }
        val cells = names.map { name ->
            inDesign(cabin.onNodeWithContentDescription("$name source").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        }
        assertEquals(395f, cells.first().left, 1.01f)
        assertEquals(1075f, cells.last().right, 1.01f)
        for (i in 1 until cells.size) assertEquals(cells[i - 1].right, cells[i].left, 0.01f)
        for (cell in cells) assertEquals(170f, cell.width, 1.01f)

        for ((i, name) in names.withIndex()) {
            val drawn = inDesign(
                cabin.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot,
            )
            assertTrue("$name drawn at $drawn in ${cells[i]}", drawn.left >= cells[i].left + 13f && drawn.right <= cells[i].right - 13f)
        }
    }
}
