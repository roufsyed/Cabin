package com.roufsyed.cabin.bar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.roufsyed.cabin.apps.SplitPair
import com.roufsyed.cabin.apps.SplitScreen
import com.roufsyed.cabin.apps.SplitWay
import com.roufsyed.cabin.apps.ToggleSteps
import com.roufsyed.cabin.model.BarSide
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.sources.Opened
import com.roufsyed.cabin.ui.Motion
import com.roufsyed.cabin.ui.chrome.SIDE_BAR_HANDLE_HEIGHT
import com.roufsyed.cabin.ui.chrome.SIDE_BAR_HANDLE_WIDTH
import com.roufsyed.cabin.ui.chrome.SIDE_BAR_WIDTH
import com.roufsyed.cabin.ui.chrome.SideBar
import com.roufsyed.cabin.ui.chrome.SideBarHandle
import com.roufsyed.cabin.ui.split.SplitChooserActivity
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.screenScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Keeps Cabin's rail on screen while another app is open, at half its width, and does there what
 * only this kind of service may: press Back, and show the recent apps. For Cabin's tray it also
 * asks Android for a picture of the screen and for the power menu, which only it may do either.
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

    /** What is up over the app in front and on which edge, its window, and what its Compose content lives by. */
    private val showing = MutableStateFlow(BarView.None)
    private var side = BarSide.Right
    private val leaving = MutableStateFlow(false)
    private var bar: View? = null
    private var owner: BarOwner? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as CabinApp).container.also { container = it }
        val state = container.sideBar
        val scope = MainScope().also { scope = it }
        val saved = container.settings.settings
        state.allowed.value = true
        state.lost.value = null
        container.scope.launch { container.settings.update { it.copy(sideBarGranted = true) } }
        state.showRecents = ::showRecents
        state.takeScreenshot = ::takeScreenshot
        state.showPowerMenu = ::showPowerMenu
        state.openPair = ::openPair
        // The bar is in the theme's colours. Cabin's own screen may not have been opened in this run.
        scope.launch {
            container.palette.collect { Slate.palette = it }
        }
        scope.launch {
            // Nothing is drawn until the saved settings have been read, or a bar that was switched
            // off, or one that should start tucked away, would show for a moment at every start.
            val first = saved.first { it.loaded }
            state.tucked.value = SideBarRules.startsTucked(first.barHandle, first.barTuck)
            // Whether the bar can be tucked away, and how soon it does that by itself.
            val tuck = saved.map { it.barHandle to it.barTuck }.distinctUntilChanged()
            launch {
                combine(
                    saved.map { it.loaded && it.sideBar },
                    state.cabinShowing,
                    state.keyboardUp,
                    saved.map { it.barHandle },
                    state.tucked,
                    SideBarRules::view,
                ).combine(saved.map { it.barSide }) { view, edge -> view to edge }
                    .distinctUntilChanged()
                    .collectLatest { (view, edge) -> change(view, edge, container) }
            }
            // Tucked away is for the app in front. Whenever Cabin's own screen comes or goes, or the
            // setting is changed, the bar is put back to what an app starts with: whole, or under
            // Immediate tucked away already.
            launch {
                combine(tuck, state.cabinShowing) { (handle, after), _ -> SideBarRules.startsTucked(handle, after) }
                    .collect { state.tucked.value = it }
            }
            // Left with no finger on it for as long as Settings says, the bar tucks itself away. The
            // wait starts again each time the bar comes up and each time a finger leaves it.
            launch {
                combine(showing, tuck, state.touch) { view, (handle, after), touch ->
                    if (view == BarView.Bar && !touch.held) SideBarRules.stayMs(handle, after) else null
                }.collectLatest { stay ->
                    if (stay != null) {
                        delay(stay)
                        state.tucked.value = true
                    }
                }
            }
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
        container?.let { cabin -> cabin.scope.launch { cabin.settings.update { it.copy(sideBarGranted = false) } } }
        container?.sideBar?.let {
            it.allowed.value = false
            it.keyboardUp.value = false
            it.tucked.value = false
            it.touched(false)
            it.showRecents = null
            it.takeScreenshot = null
            it.showPowerMenu = null
            it.openPair = null
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
        val container = container ?: return
        val view = showing.value
        if (view != BarView.None) {
            close()
            show(view, side, container)
        }
    }

    private suspend fun change(view: BarView, edge: BarSide, container: AppContainer) {
        val was = showing.value
        val slides = edge == side && container.settings.settings.value.animations
        if (slides && was == BarView.Bar && view == BarView.Handle) {
            leaving.value = true
            val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            delay((Motion.BAR_MS * scale).toLong())
        }
        show(view, edge, container, slideIn = slides && was == BarView.Handle && view == BarView.Bar)
    }

    /** Puts up the whole bar, only its handle, or nothing, on [edge], in place of whatever was up. */
    private fun show(view: BarView, edge: BarSide, container: AppContainer, slideIn: Boolean = false) {
        if (view == showing.value && edge == side && !leaving.value) return
        close()
        leaving.value = false
        side = edge
        val onLeft = edge == BarSide.Left
        // One design pixel is as large here as on Cabin's own screen.
        val scale = screenScale(this)
        when (view) {
            BarView.None -> Unit
            // The bar runs the full height of the screen.
            BarView.Bar -> open(
                scale, "Cabin side bar", onLeft,
                width = (SIDE_BAR_WIDTH * scale).roundToInt(), height = WindowManager.LayoutParams.MATCH_PARENT,
            ) { Bar(container, onLeft, slideIn) }
            // The handle is in the corner where the bar's grip was, so the same spot brings the bar back.
            BarView.Handle -> open(
                scale, "Cabin side bar handle", onLeft,
                width = (SIDE_BAR_HANDLE_WIDTH * scale).roundToInt(),
                height = (SIDE_BAR_HANDLE_HEIGHT * scale).roundToInt(),
            ) { SideBarHandle(onClick = { container.sideBar.tucked.value = false }, onLeft = onLeft) }
        }
        showing.value = if (bar == null) BarView.None else view
    }

    /**
     * A window of Cabin's against the screen's right or left edge and its bottom, over whatever app
     * is in front.
     */
    private fun open(
        scale: Float,
        title: String,
        onLeft: Boolean,
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ) {
        val manager = getSystemService(WindowManager::class.java)
        val barOwner = BarOwner()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(barOwner)
            setViewTreeSavedStateRegistryOwner(barOwner)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density = scale, fontScale = 1f), content = content)
            }
        }
        val layout = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Never takes the keys from the app beside it, and is placed on the whole screen, bars and all.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (onLeft) Gravity.LEFT else Gravity.RIGHT) or Gravity.BOTTOM
            this.title = title
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
        showing.value = BarView.None
        // No finger is on a bar that is not there.
        container?.sideBar?.let { if (it.touch.value.held) it.touched(false) }
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
    private fun Bar(container: AppContainer, onLeft: Boolean, slideIn: Boolean) {
        val away by leaving.collectAsStateWithLifecycle()
        var entered by remember { mutableStateOf(!slideIn) }
        LaunchedEffect(Unit) { entered = true }
        val shift by animateFloatAsState(if (entered && !away) 0f else 1f, tween(Motion.BAR_MS), label = "bar")
        Box(Modifier.graphicsLayer { translationX = shift * size.width * (if (onLeft) -1f else 1f) }) {
            BarButtons(container, onLeft)
        }
    }

    @Composable
    private fun BarButtons(container: AppContainer, onLeft: Boolean) {
        val volume by container.volume.volume.collectAsStateWithLifecycle()
        val handle by remember(container) {
            container.settings.settings.map { it.barHandle }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = true)
        SideBar(
            volume = volume,
            // With no app chosen for one of these, Cabin comes up, where the button leads to choosing one.
            onPhone = { if (container.sources.open(Slot.Phone) == Opened.NeedsApp) showCabin() },
            onLink = { if (container.sources.open(Slot.Link) == Opened.NeedsApp) showCabin() },
            onSplit = when (container.apps.split) {
                SplitWay.Adjacent -> ({ split(container) })
                SplitWay.Toggle -> ({ splitByToggle() })
                SplitWay.None -> null
            },
            onHome = ::showCabin,
            onRecents = { showRecents() },
            onBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            onVolumeUp = container.volume::up,
            onMute = container.volume::toggleMute,
            onVolumeDown = container.volume::down,
            onTuck = if (handle) ({ container.sideBar.tucked.value = true }) else null,
            onLeft = onLeft,
            onTouch = container.sideBar::touched,
        )
    }

    /** Android's own list of recent apps, wherever the unit has one. */
    private fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * A picture of the screen, taken and saved by Android as by its own screenshot button; Cabin
     * never sees it. The tray asks for it, so it waits until the tray has gone from the screen.
     */
    private fun takeScreenshot(): Boolean {
        val scope = scope ?: return false
        scope.launch {
            delay(SCREENSHOT_WAIT_MS)
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
        return true
    }

    /** Android's power menu, wherever the unit has one. Restart is in it. */
    private fun showPowerMenu(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

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

    private var stepping: Job? = null

    private fun toggleSteps(docked: String?, pair: SplitPair? = null) = ToggleSteps(
        split = ::splitShowing,
        settled = { splitShowing() && (docked == null || !fullSize(docked)) },
        toggle = { performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) },
        within = { wait, condition -> withTimeoutOrNull(wait) { while (!condition()) delay(STEP_LOOK_MS) } != null },
        leftInFront = { docked != null && inFront(docked) },
        startLeft = { pair != null && started(pair.left) },
        startRight = { pair != null && started(pair.right) },
    )

    private fun openPair(pair: SplitPair): Boolean {
        val scope = scope ?: return false
        stepping?.cancel()
        stepping = scope.launch {
            if (!SplitScreen.toggledPair(toggleSteps(pair.left.component?.packageName, pair))) noSplit()
        }
        return true
    }

    private fun splitByToggle() {
        val scope = scope ?: return
        stepping?.cancel()
        stepping = scope.launch {
            val onScreen = appsOnScreen()
            val opened = SplitScreen.toggledBeside(toggleSteps(onScreen.firstOrNull())) {
                started(SplitChooserActivity.intent(this@SideBarService, onScreen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (!opened) noSplit()
        }
    }

    private fun noSplit() {
        try {
            Toast.makeText(this, "The unit did not split the screen", Toast.LENGTH_SHORT).show()
        } catch (e: RuntimeException) {
        }
    }

    private fun started(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: RuntimeException) {
        false
    }

    private fun splitShowing(): Boolean = try {
        windows.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
    } catch (e: RuntimeException) {
        false
    }

    private fun fullSize(packageName: String): Boolean = try {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        val most = metrics.widthPixels.toLong() * metrics.heightPixels * 3 / 4
        val bounds = Rect()
        windows.any { window ->
            window.getBoundsInScreen(bounds)
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.root?.packageName?.toString() == packageName &&
                bounds.width().toLong() * bounds.height() >= most
        }
    } catch (e: RuntimeException) {
        false
    }

    private fun inFront(packageName: String): Boolean = try {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        apps.firstOrNull()?.root?.packageName?.toString() == packageName ||
            apps.any { (it.isFocused || it.isActive) && it.root?.packageName?.toString() == packageName }
    } catch (e: RuntimeException) {
        false
    }

    /**
     * The packages of the apps on screen, one or, when the screen is already split, two, so that the
     * chooser can leave them out. Cabin asks about another app's window for split screen and nothing
     * else: this when Split is pressed, and before Android 12L which app is in front and how much of
     * the screen it fills while the screen is being split.
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

    private companion object {
        /** How long the tray is given to go from the screen before the picture is taken. */
        const val SCREENSHOT_WAIT_MS = 350L

        const val STEP_LOOK_MS = 80L
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
