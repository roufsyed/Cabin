package com.roufsyed.cabin.apps

import android.content.ComponentName
import android.content.Intent
import android.content.res.Resources
import android.os.Build

/**
 * What to start to show two apps side by side: [first] at once, then [then] a moment later. See
 * [SplitScreen.pair].
 */
class SplitPair(val first: Array<Intent>, val then: Intent)

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
     * Whether this unit can do it: Android 12L or later, with split screen left switched on by whoever
     * built the firmware. The two switches are Android's own configuration values; a build that does
     * not have one of them is taken to allow it.
     */
    fun supported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 &&
            systemSwitch("config_supportsMultiWindow") &&
            systemSwitch("config_supportsSplitScreenMultiWindow")

    private fun systemSwitch(name: String): Boolean = try {
        val system = Resources.getSystem()
        val id = system.getIdentifier(name, "bool", "android")
        id == 0 || system.getBoolean(id)
    } catch (e: RuntimeException) {
        true
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

    /** An app as a launcher starts it, so that one already running is brought forward as it is. */
    private fun launcher(component: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
}
