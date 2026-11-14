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
     * True while the bar is tucked away, with only its handle left at the screen's edge. The grip on
     * the bar sets it and the handle clears it. It is not kept between runs, and it ends whenever
     * Cabin's own screen has been shown: the bar is back for the next app.
     */
    val tucked = MutableStateFlow(false)

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

/** What is drawn over another app: nothing, the whole bar, or only the handle that brings it back. */
enum class BarView { None, Bar, Handle }

/** When the bar is drawn, and where its handle goes. Kept apart from Android so it can be tested. */
object SideBarRules {
    /**
     * [wanted] is the switch in Setup. The bar is for other apps, so it goes while Cabin is showing,
     * and it gets out of the way of a keyboard.
     */
    fun shown(wanted: Boolean, cabinShowing: Boolean, keyboardUp: Boolean): Boolean =
        wanted && !cabinShowing && !keyboardUp

    /**
     * What is drawn. With the handle switched on in Settings ([handle]) the bar can be [tucked] away,
     * and then only the handle is drawn. With it switched off the bar is always whole.
     */
    fun view(wanted: Boolean, cabinShowing: Boolean, keyboardUp: Boolean, handle: Boolean, tucked: Boolean): BarView =
        when {
            !shown(wanted, cabinShowing, keyboardUp) -> BarView.None
            handle && tucked -> BarView.Handle
            else -> BarView.Bar
        }

    /**
     * How far down the screen the handle's top edge is, in pixels: it is centred on the grip that
     * tucked the bar away, so that the same spot brings the bar back. The grip is [grip] high and
     * the handle [handleHeight]; the bar's [buttons] share the rest of [screenHeight] between them,
     * [above] of them over the grip.
     */
    fun handleTop(screenHeight: Int, grip: Float, handleHeight: Float, buttons: Int, above: Int): Int {
        val button = (screenHeight - grip) / buttons
        val gripMiddle = above * button + grip / 2f
        return (gripMiddle - handleHeight / 2f).toInt().coerceIn(0, (screenHeight - handleHeight).toInt().coerceAtLeast(0))
    }
}
