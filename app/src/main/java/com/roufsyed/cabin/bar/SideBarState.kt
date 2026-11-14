package com.roufsyed.cabin.bar

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the bar over other apps goes by. It is shared between the service that draws the bar, the
 * activity, which knows when Cabin's own screen is showing, and Setup, which says whether the bar is
 * allowed.
 */
class SideBarState {
    /**
     * True while Android is running Cabin's accessibility service, which is what draws the bar and
     * presses Back in other apps. The user switches it on under the unit's Accessibility settings.
     */
    val allowed = MutableStateFlow(false)

    /** True while Cabin's own screen is showing. It has the whole rail, so the bar steps aside. */
    val cabinShowing = MutableStateFlow(false)

    /**
     * True while an on-screen keyboard is up, as the service learns from Android's list of open
     * windows. The bar would lie over the keyboard's right-hand keys.
     */
    val keyboardUp = MutableStateFlow(false)

    /**
     * Shows Android's list of recent apps. Set by the service while it runs: an accessibility service
     * is the only kind of app Android lets ask for it.
     */
    @Volatile
    var showRecents: (() -> Boolean)? = null

    /**
     * Opens the chooser for a second app in half the screen, beside what is on screen already, whose
     * packages it is given. Set by Cabin's activity while it exists: Android only puts one app beside
     * another when an activity asks.
     */
    @Volatile
    var openSplitChooser: ((onScreen: List<String>) -> Boolean)? = null
}

/** When the bar is drawn. Kept apart from Android so it can be tested. */
object SideBarRules {
    /**
     * [wanted] is the switch in Setup. The bar is for other apps, so it goes while Cabin is showing,
     * and it gets out of the way of a keyboard.
     */
    fun shown(wanted: Boolean, cabinShowing: Boolean, keyboardUp: Boolean): Boolean =
        wanted && !cabinShowing && !keyboardUp
}
