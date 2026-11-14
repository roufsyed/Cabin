package com.roufsyed.cabin.bar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.roufsyed.cabin.AppContainer
import com.roufsyed.cabin.CabinApp
import com.roufsyed.cabin.MainActivity
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.sources.Opened
import com.roufsyed.cabin.ui.chrome.SIDE_BAR_WIDTH
import com.roufsyed.cabin.ui.chrome.SideBar
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Themes
import com.roufsyed.cabin.ui.theme.screenScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Keeps Cabin's rail on screen while another app is open, at half its width, and does there what
 * only this kind of service may: press Back, and show the recent apps.
 *
 * Android lets no other kind of app press Back in an app that is not its own or ask for the list of
 * recent apps, and an accessibility service may also draw over every app without a further
 * permission. All this one asks to be told is when windows open and close, which is how it knows a
 * keyboard has come up and gets out of its way. It never reads what an app shows. The one thing it
 * asks about other apps' windows is which apps they belong to, when Split is pressed (see
 * res/xml/side_bar_service.xml).
 *
 * The bar lies on top of the app's right-hand edge. Android gives an app no way to make other apps
 * narrower, so whatever an app draws under those 66 pixels is covered.
 */
class SideBarService : AccessibilityService() {

    /** Everything Cabin draws from, taken once when Android starts the service. */
    private var container: AppContainer? = null
    private var scope: CoroutineScope? = null

    /** The bar while it is up, and what its Compose content lives by. */
    private var bar: View? = null
    private var owner: BarOwner? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as CabinApp).container.also { container = it }
        val state = container.sideBar
        val scope = MainScope().also { scope = it }
        state.allowed.value = true
        state.showRecents = ::showRecents
        // The bar is in the theme's colours. Cabin's own screen may not have been opened in this run.
        scope.launch {
            container.settings.settings.map { it.theme }.distinctUntilChanged().collect { Slate.palette = Themes.palette(it) }
        }
        scope.launch {
            // Nothing is drawn until the saved settings are read, or a bar that was switched off
            // would show for a moment at every start.
            combine(
                container.settings.settings.map { it.loaded && it.sideBar },
                state.cabinShowing,
                state.keyboardUp,
                SideBarRules::shown,
            ).distinctUntilChanged().collect { up -> if (up) open(container) else close() }
        }
        lookForKeyboard()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        scope?.cancel()
        scope = null
        close()
        container?.sideBar?.let {
            it.allowed.value = false
            it.keyboardUp.value = false
            it.showRecents = null
        }
        container = null
    }

    /** The only events asked for are windows opening and closing. A keyboard is one of them. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) lookForKeyboard()
    }

    private fun lookForKeyboard() {
        val state = container?.sideBar ?: return
        state.keyboardUp.value = try {
            windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        } catch (e: RuntimeException) {
            false
        }
    }

    override fun onInterrupt() = Unit

    /** The screen may have turned or changed size, and the bar is cut to fit it. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val container = container
        if (bar != null && container != null) {
            close()
            open(container)
        }
    }

    private fun open(container: AppContainer) {
        if (bar != null) return
        val manager = getSystemService(WindowManager::class.java)
        // One design pixel is as large here as on Cabin's own screen.
        val scale = screenScale(this)
        val barOwner = BarOwner()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(barOwner)
            setViewTreeSavedStateRegistryOwner(barOwner)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density = scale, fontScale = 1f)) {
                    Bar(container)
                }
            }
        }
        val layout = WindowManager.LayoutParams(
            (SIDE_BAR_WIDTH * scale).roundToInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Never takes the keys from the app beside it, and runs the full height of the screen.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            title = "Cabin side bar"
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
        }
        barOwner.start()
        try {
            manager.addView(view, layout)
            bar = view
            owner = barOwner
        } catch (e: RuntimeException) {
            // Android has taken the service's right to draw away, and will stop it next.
            barOwner.stop()
        }
    }

    private fun close() {
        val view = bar ?: return
        bar = null
        try {
            getSystemService(WindowManager::class.java).removeView(view)
        } catch (e: RuntimeException) {
            // Already gone with the service.
        }
        owner?.stop()
        owner = null
    }

    @Composable
    private fun Bar(container: AppContainer) {
        val volume by container.volume.volume.collectAsStateWithLifecycle()
        SideBar(
            volume = volume,
            // With no app chosen for one of these, Cabin comes up, where the button leads to choosing one.
            onPhone = { if (container.sources.open(Slot.Phone) == Opened.NeedsApp) showCabin() },
            onLink = { if (container.sources.open(Slot.Link) == Opened.NeedsApp) showCabin() },
            onSplit = if (container.apps.splitScreen) ({ split(container) }) else null,
            onHome = ::showCabin,
            onRecents = { showRecents() },
            onBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            onVolumeUp = container.volume::up,
            onMute = container.volume::toggleMute,
            onVolumeDown = container.volume::down,
        )
    }

    /** Android's own list of recent apps, wherever the unit has one. */
    private fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * Split: the app in front keeps one half of the screen and the chooser for a second app opens in
     * the other. Cabin's activity has to be the one that asks Android for that. If it is not there,
     * as after Android has restarted Cabin behind another app, Cabin's Home comes up instead, and
     * the activity with it.
     */
    private fun split(container: AppContainer) {
        val open = container.sideBar.openSplitChooser
        if (open == null || !open(appsOnScreen())) showCabin()
    }

    /**
     * The packages of the apps on screen, one or, when the screen is already split, two, so that the
     * chooser can leave them out. It is the one thing Cabin ever asks about another app's window,
     * and only when Split is pressed.
     */
    private fun appsOnScreen(): List<String> = try {
        val own = packageName
        windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }
            .filter { it != own }
            .distinct()
    } catch (e: RuntimeException) {
        emptyList()
    }

    /** Brings Cabin's Home up, as the unit's Home button does. */
    private fun showCabin() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(home)
        } catch (e: RuntimeException) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /**
     * What Compose needs from the window it is drawn in, which an activity supplies and a service's
     * window does not: a lifecycle, running while the bar is up, and somewhere to keep saved state.
     */
    private class BarOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        fun start() {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
