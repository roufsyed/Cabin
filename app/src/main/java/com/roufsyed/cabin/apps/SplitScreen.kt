package com.roufsyed.cabin.apps

import android.content.ComponentName
import android.content.Intent
import android.content.res.Resources
import android.os.Build

/**
 * What to start to show two apps side by side: [first] at once, then [then] a moment later. See
 * [SplitScreen.pair].
 */
class SplitPair(val first: Array<Intent>, val then: Intent) {
    val left: Intent get() = first.last()
    val right: Intent get() = first.first()
}

enum class SplitWay { None, Adjacent, Toggle }

class ToggleSteps(
    val split: () -> Boolean,
    val settled: () -> Boolean,
    val toggle: () -> Boolean,
    val within: suspend (Long, () -> Boolean) -> Boolean,
    val leftInFront: () -> Boolean = { true },
    val startLeft: () -> Boolean = { false },
    val startRight: () -> Boolean = { false },
)

/**
 * Two apps side by side, using Android's own split screen. Since Android 12L an activity may start
 * an app "adjacent", and Android then splits the screen between that app and the one that was in
 * front. A home app cannot itself be one of the halves, so Cabin puts both apps there.
 */
object SplitScreen {

    /**
     * How long after opening the two apps the right one is moved beside the left. Android needs a
     * moment to let go of an earlier pair; a tenth of this was enough on the emulator.
     */
    const val STEP_GAP_MS = 400L

    /**
     * How this unit can do it, if at all. Split screen has to be left switched on by whoever built
     * the firmware: the two switches are Android's own configuration values, and a build that does
     * not have one of them is taken to allow it. From Android 12L an app is opened adjacent. Before
     * that only Android itself splits the screen, and an accessibility service may ask it to.
     */
    fun way(): SplitWay = way(Build.VERSION.SDK_INT, multiWindow() != false, splitScreen() != false)

    fun way(sdk: Int, multiWindow: Boolean, splitScreen: Boolean): SplitWay = when {
        !multiWindow || !splitScreen -> SplitWay.None
        sdk >= Build.VERSION_CODES.S_V2 -> SplitWay.Adjacent
        else -> SplitWay.Toggle
    }

    fun ready(way: SplitWay, serviceOn: Boolean): Boolean =
        way == SplitWay.Adjacent || (way == SplitWay.Toggle && serviceOn)

    suspend fun toggledPair(steps: ToggleSteps): Boolean {
        if (!shown(steps)) return false
        if (steps.split()) {
            if (!steps.toggle() || !steps.within(SETTLE_WAIT_MS) { !steps.split() }) return false
            steps.within(REST_MS) { false }
            if (!shown(steps)) return false
        }
        return docked(steps) && steps.startRight()
    }

    suspend fun toggledBeside(steps: ToggleSteps, openChooser: () -> Boolean): Boolean {
        if (steps.split()) return steps.toggle()
        return docked(steps) && openChooser()
    }

    private suspend fun shown(steps: ToggleSteps): Boolean =
        steps.startLeft() && steps.within(OPEN_WAIT_MS, steps.leftInFront)

    private suspend fun docked(steps: ToggleSteps): Boolean {
        if (!steps.toggle() || !steps.within(SHOW_WAIT_MS, steps.split)) return false
        if (steps.within(SETTLE_WAIT_MS, steps.settled) && !steps.within(HOLD_MS) { !steps.settled() }) return true
        if (steps.split()) steps.toggle()
        return false
    }

    const val OPEN_WAIT_MS = 8_000L
    const val SHOW_WAIT_MS = 2_000L
    const val SETTLE_WAIT_MS = 4_000L
    const val HOLD_MS = 300L
    const val REST_MS = 800L

    fun multiWindow(): Boolean? = systemSwitch("config_supportsMultiWindow")

    fun splitScreen(): Boolean? = systemSwitch("config_supportsSplitScreenMultiWindow")

    private fun systemSwitch(name: String): Boolean? = try {
        val system = Resources.getSystem()
        val id = system.getIdentifier(name, "bool", "android")
        if (id == 0) null else system.getBoolean(id)
    } catch (e: RuntimeException) {
        null
    }

    /**
     * The two steps that show [leftId] and [rightId] side by side. Null if either is not a component.
     *
     * First both apps are opened the ordinary way, the right one and then the left one over it. That
     * leaves the left app in front, and it takes each of them out of any earlier pair: later builds
     * of Android 13 keep a pair alive in the background after Home, and an app that is still in one
     * cannot be paired afresh. Then the right app is started again, adjacent, which moves it beside
     * whatever is in front: the left app.
     *
     * Doing it in one go, left app then right app adjacent, also works from a clean start, but with
     * an earlier pair in the background it shows the old pair, or only one of the apps. (emulator,
     * Android 13; the reasons are in Android's StageCoordinator.)
     */
    fun pair(leftId: String, rightId: String): SplitPair? {
        val left = ComponentName.unflattenFromString(leftId) ?: return null
        val right = ComponentName.unflattenFromString(rightId) ?: return null
        return SplitPair(
            first = arrayOf(
                launcher(right).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                launcher(left).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
            then = launcher(right).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT),
        )
    }

    /**
     * What an activity that is itself one half of a split screen starts to open [appId] in its own
     * half. Android opens an app where the activity that asked for it is; asked for in any other way,
     * the app takes the whole screen and the split ends. (emulator) Null if [appId] is not a component.
     */
    fun inSameHalf(appId: String): Intent? =
        ComponentName.unflattenFromString(appId)?.let { launcher(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** An app as a launcher starts it, so that one already running is brought forward as it is. */
    private fun launcher(component: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
}
