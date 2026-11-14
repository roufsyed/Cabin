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
