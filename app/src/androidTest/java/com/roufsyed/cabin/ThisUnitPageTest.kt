package com.roufsyed.cabin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.SplitWay
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.system.FixedUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThisUnitPageTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        it.copy(unit = FixedUnit(Samples.unit.copy(release = "10", sdk = 29, patch = "2020-09-05", split = SplitWay.Toggle)))
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()

    private fun openThisUnit() {
        for (step in listOf("All apps", "Settings", "Open setup", "Next setup page", "What Android says of this unit")) tap(step)
    }

    @Test
    fun setupLeadsToWhatAndroidSaysOfTheUnit() {
        openThisUnit()
        cabin.onNodeWithText("This unit").assertIsDisplayed()
        cabin.onNodeWithText("What Android says of it. Nothing here can be changed.").assertIsDisplayed()
        shows("Android: 10 (API 29), security patch 2020-09-05")
        shows("Screen: 1280 x 800 at 160 dpi")
        shows("Split screen: Android's older kind: Cabin asks for it through Accessibility (multi-window on, split screen on)")
        shows("Accessibility: Off: Cabin is not allowed under Accessibility just now")
    }

    @Test
    fun doneLeadsBackToSetupsSecondPage() {
        openThisUnit()
        tap("Back to setup")
        shows("Choose the music app")
        shows("What Android says of this unit")
        gone("Allow location")
    }
}
