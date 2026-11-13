package com.roufsyed.cabin

import android.content.Intent
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as composeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.rules.ExternalResource

/** Gives each test a fresh copy of the design's values, before the activity starts. */
class FreshDesignValues(private val configure: (AppContainer) -> AppContainer = { it }) : ExternalResource() {
    lateinit var container: AppContainer
        private set

    override fun before() {
        container = configure(AppContainer.design())
        ApplicationProvider.getApplicationContext<CabinApp>().replaceContainer(container)
    }
}

typealias CabinRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Runs the real activity, full screen, as the unit would. */
fun cabinRule(): CabinRule = composeRule(
    activityRule = ActivityScenarioRule(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
    ),
    activityProvider = { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        activity!!
    },
)
