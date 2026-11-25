package com.roufsyed.cabin.bar

import com.roufsyed.cabin.apps.SplitPair
import com.roufsyed.cabin.model.TuckAfter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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

    val lost = MutableStateFlow<SideBarLoss?>(null)

    /** True while Cabin's own screen is showing. It has the whole rail, so the bar steps aside. */
    val cabinShowing = MutableStateFlow(false)

    /**
     * True while an on-screen keyboard is up, as the service learns from Android's list of open
     * windows. The bar would lie over the keyboard's right-hand keys.
     */
    val keyboardUp = MutableStateFlow(false)

    /**
     * True while the bar is tucked away, with only its handle left at the screen's edge. The grip on
     * the bar sets it, and so does the bar being left untouched for as long as Settings says; the
     * handle clears it. It is not kept between runs, and whenever Cabin's own screen has been shown
     * it is put back to what the next app starts with (see [SideBarRules.startsTucked]).
     */
    val tucked = MutableStateFlow(false)

    /** Whether a finger is on the bar, as the bar itself reports through [touched]. */
    val touch = MutableStateFlow(BarTouch())

    /** A finger has come down on the bar ([held]), or the last one has left it. */
    fun touched(held: Boolean) = touch.update { BarTouch(held, it.changes + 1) }

    /**
     * Shows Android's list of recent apps. Set by the service while it runs: an accessibility service
     * is the only kind of app Android lets ask for it.
     */
    @Volatile
    var showRecents: (() -> Boolean)? = null

    /**
     * Has Android take a picture of the screen, as the unit's own screenshot button would, and save
     * it where the unit keeps them. Set by the service while it runs. It is asked for from Cabin's
     * tray, so the picture is taken a moment later, once the tray has gone from the screen.
     */
    @Volatile
    var takeScreenshot: (() -> Boolean)? = null

    /**
     * Shows Android's power menu, which is where the unit is restarted from: an app cannot restart
     * the unit itself. Set by the service while it runs.
     */
    @Volatile
    var showPowerMenu: (() -> Boolean)? = null

    /**
     * Opens the chooser for a second app in half the screen, beside what is on screen already, whose
     * packages it is given. Set by Cabin's activity while it exists: Android only puts one app beside
     * another when an activity asks.
     */
    @Volatile
    var openSplitChooser: ((onScreen: List<String>) -> Boolean)? = null

    @Volatile
    var openPair: ((SplitPair) -> Boolean)? = null

    @Volatile
    var offered: (() -> List<Int>?)? = null
}

/**
 * Whether a finger is on the bar. [changes] counts every time that has changed, so that a finger
 * which comes down and leaves again before anyone has looked still reads as news.
 */
data class BarTouch(val held: Boolean = false, val changes: Int = 0)

/** What is drawn over another app: nothing, the whole bar, or only the handle that brings it back. */
enum class BarView { None, Bar, Handle }

/** When the bar is drawn, and when it tucks itself away. Kept apart from Android so it can be tested. */
object SideBarRules {
    fun wentMissing(wanted: Boolean, granted: Boolean, enabled: Boolean): Boolean = wanted && granted && !enabled

    fun recover(wanted: Boolean, granted: Boolean, access: SideBarAccess): SideBarLoss? = when {
        !wanted || access.enabled() -> null
        access.restore() -> null
        granted -> SideBarLoss(access.stoppedAt())
        else -> null
    }

    fun lossText(stoppedAt: String?): String =
        if (stoppedAt != null) {
            "Side bar off: the unit stopped Cabin at $stoppedAt"
        } else {
            "Side bar off: the unit switched it off, not Cabin"
        }

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
     * Whether an app starts with the bar tucked away. Under Immediate it does, wherever the bar can
     * be tucked away at all, which takes the [handle].
     */
    fun startsTucked(handle: Boolean, after: TuckAfter): Boolean = handle && after == TuckAfter.Immediate

    /**
     * How long the bar stays out with no finger on it before it tucks itself away, in milliseconds,
     * or null where it stays until its grip is tapped. Under Immediate the bar is only ever out
     * because its handle was tapped, and it then stays as long as under the shortest wait, which is
     * long enough to reach a button and press it.
     */
    fun stayMs(handle: Boolean, after: TuckAfter): Long? = when {
        !handle -> null
        else -> when (after) {
            TuckAfter.Immediate, TuckAfter.FiveSeconds -> 5_000L
            TuckAfter.TenSeconds -> 10_000L
            TuckAfter.Never -> null
        }
    }
}
