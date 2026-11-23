package com.roufsyed.cabin

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.roufsyed.cabin.apps.SplitPair
import com.roufsyed.cabin.apps.SplitScreen
import com.roufsyed.cabin.notify.CabinNotificationListener
import com.roufsyed.cabin.system.Grant
import com.roufsyed.cabin.ui.CabinRoot
import com.roufsyed.cabin.ui.HostActions
import com.roufsyed.cabin.ui.Motion
import com.roufsyed.cabin.ui.shell.Overlay
import com.roufsyed.cabin.ui.shell.ShellViewModel
import com.roufsyed.cabin.ui.split.SplitChooserActivity
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Themes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Cabin's only activity. It is the unit's home screen, so it is never finished. */
class MainActivity : ComponentActivity() {

    private val shell: ShellViewModel by viewModels()
    private val container: AppContainer get() = (application as CabinApp).container

    private val homeRoleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            container.homeRole.refresh()
        }

    /** Android's own question whether Bluetooth may be switched. Back from it, the marks are re-read. */
    private val bluetoothQuestion =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshFromSystem()
        }

    /** Who is waiting to hear how Android's permission dialog was answered. */
    private var permissionAnswer: ((Boolean) -> Unit)? = null
    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            refreshFromSystem()
            permissionAnswer?.invoke(granted)
            permissionAnswer = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DESIGN_VALUES, false) == true) {
            (application as CabinApp).showDesignValues = true
        }
        hideSystemBars()
        val container = container
        followTheme()
        val host = HostActions(
            requestHomeRole = { container.homeRole.requestIntent()?.let(homeRoleRequest::launch) },
            requestNotificationAccess = ::openNotificationAccess,
            requestWriteSettings = {
                open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:$packageName".toUri()))
            },
            withMicrophone = { answer -> withPermission(Grant.Microphone.permission, answer) },
            requestGrant = ::requestGrant,
            withGrant = { grant, answer -> withPermission(grant.permission, answer) },
            openBluetooth = { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            openWifi = { open(Intent(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS)) },
            askBluetooth = ::askBluetooth,
            // The hotspot page first, by the name Android's settings app answers to; a unit without
            // it gets the network page that leads there, or failing that its settings.
            openHotspot = {
                open(Intent(HOTSPOT_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            },
            openUnitSettings = { open(Intent(Settings.ACTION_SETTINGS)) },
            openPair = ::openPair,
            openAccessibility = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
        setContent { CabinRoot(container, host) }
        followWindowBrightness()
        container.sideBar.openSplitChooser = openSplitChooser
    }

    override fun onDestroy() {
        val state = container.sideBar
        if (state.openSplitChooser === openSplitChooser) state.openSplitChooser = null
        super.onDestroy()
    }

    /**
     * The side bar's Split button: opens the chooser for a second app beside what is on screen. The
     * side bar's service hands this over because Android only puts one app beside another when an
     * activity asks, and does it for this one even while it is in the background.
     */
    private val openSplitChooser: (List<String>) -> Boolean = { onScreen ->
        try {
            startActivity(SplitChooserActivity.beside(this, onScreen))
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    /** The Home button while Cabin is already showing: go back to the Home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) shell.home()
    }

    /** While Cabin's own screen shows, with the whole rail, the bar kept over other apps steps aside. */
    override fun onStart() {
        super.onStart()
        container.sideBar.cabinShowing.value = true
    }

    override fun onStop() {
        container.sideBar.cabinShowing.value = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // The user may be coming back from a system settings screen.
        refreshFromSystem()
    }

    private fun refreshFromSystem() {
        container.homeRole.refresh()
        container.media.refreshAccess()
        container.brightness.refresh()
        container.volume.refresh()
        container.permissions.refresh()
        container.status.refresh()
        container.keepSideBar()
    }

    /**
     * Puts the saved colour theme in use, now and whenever another is chosen. It starts before the
     * first frame is composed, so a theme that is already known is there from the start. The window
     * behind the screen takes the theme's ground colour too.
     */
    private fun followTheme() {
        lifecycleScope.launch {
            container.settings.settings.map { it.theme }.distinctUntilChanged().collect { id ->
                val palette = Themes.palette(id)
                Slate.palette = palette
                window.setBackgroundDrawable(palette.ground.toArgb().toDrawable())
            }
        }
    }

    /**
     * Until Cabin is allowed to change the unit's brightness setting, the stepper dims Cabin's own
     * window instead, so it is never a dead control.
     */
    private fun followWindowBrightness() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(container.brightness.windowBrightness, shell.state) { level, state ->
                    // The screen-off clock wants the backlight as low as it goes, whatever the level is.
                    if (state.overlay == Overlay.ScreenOff) 0.01f else level
                }.distinctUntilChanged().collectLatest { level ->
                    if (level == 0.01f && container.settings.settings.value.animations) delay(Motion.SCREEN_OFF_MS.toLong())
                    window.attributes = window.attributes.apply {
                        screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }
        }
    }

    /**
     * Keys, which is how the steering wheel's buttons arrive once the unit has mapped them. While Cabin
     * is showing it acts on them itself; while another app is showing, Android sends volume keys to the
     * system and media keys to whatever is playing, and Cabin is not involved.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (BuildConfig.DEBUG && event.repeatCount == 0) {
            // `adb logcat -s CabinKeys` shows what each wheel button really sends.
            Log.d(KEYS_TAG, "key ${KeyEvent.keyCodeToString(keyCode)} ($keyCode) scan ${event.scanCode}")
        }
        val first = event.repeatCount == 0
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> container.volume.up()
            KeyEvent.KEYCODE_VOLUME_DOWN -> container.volume.down()
            KeyEvent.KEYCODE_VOLUME_MUTE -> if (first) container.volume.toggleMute()
            KeyEvent.KEYCODE_MEDIA_NEXT -> if (first) container.media.next()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (first) container.media.previous()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> if (first) container.sources.playPause()
            KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST ->
                if (first) shell.requestVoice()
            // Android's own "mode" key. Whether the unit sends it for the wheel's Mode button has to be
            // seen on the unit; nothing vendor-specific is assumed.
            KeyEvent.KEYCODE_BUTTON_MODE -> if (first) container.sources.selectNext()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST,
        KeyEvent.KEYCODE_BUTTON_MODE,
        -> true
        else -> super.onKeyUp(keyCode, event)
    }

    /** Runs [answer] with whether [permission] is granted, asking with Android's dialog first if it is not. */
    private fun withPermission(permission: String, answer: (Boolean) -> Unit) {
        // "Nearby devices" does not exist before Android 12, where the phone mark needs no permission.
        val notNeeded = permission == Grant.NearbyDevices.permission && Build.VERSION.SDK_INT < 31
        if (notNeeded || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            answer(true)
        } else {
            permissionAnswer = answer
            permissionRequest.launch(permission)
        }
    }

    /**
     * Asks for one of the optional permissions. Once the user has refused it for good Android no longer
     * shows a dialog, so the app's own page in the unit's settings is opened instead.
     */
    private fun requestGrant(grant: Grant) {
        withPermission(grant.permission) { granted ->
            if (!granted && !shouldShowRequestPermissionRationale(grant.permission)) {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
            }
        }
    }

    /**
     * Has Android ask whether Bluetooth may be switched on or off, for a unit on which Cabin may not
     * switch it itself (Android 13 and later). It is asked as a question that reports back, which is
     * what lets Android name Cabin in it. Turning on is asked the documented way. Turning off is
     * asked of the same screen of Android's settings app, by a name that is not a public one; a unit
     * whose settings lack it, or refuse, gets its Bluetooth page instead. So does a unit whose
     * Bluetooth is not Android's own, about which Android has nothing to ask.
     */
    private fun askBluetooth(on: Boolean) {
        if (getSystemService(BluetoothManager::class.java)?.adapter == null) {
            open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }
        val question = Intent(if (on) BluetoothAdapter.ACTION_REQUEST_ENABLE else REQUEST_BLUETOOTH_OFF)
        try {
            bluetoothQuestion.launch(question)
        } catch (e: ActivityNotFoundException) {
            open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: SecurityException) {
            open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    /** The second step of a split-screen pair that is still to come, if any. */
    private var pairStep: Runnable? = null

    /**
     * Opens two apps side by side, in the two steps [SplitPair] describes. Both steps have to come
     * from this activity: started from the application alone, Android opens the second app full
     * screen instead of beside the first.
     */
    private fun openPair(pair: SplitPair): Boolean {
        pairStep?.let(window.decorView::removeCallbacks)
        pairStep = null
        try {
            startActivities(pair.first)
        } catch (e: RuntimeException) {
            // One of the apps has gone, or may not be started from outside.
            return false
        }
        val step = Runnable {
            pairStep = null
            // If Cabin is in front again, Home was pressed in the meantime: leave things as they are.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@Runnable
            try {
                startActivity(pair.then)
            } catch (e: RuntimeException) {
                // The left app stays open on its own.
            }
        }
        pairStep = step
        window.decorView.postDelayed(step, SplitScreen.STEP_GAP_MS)
        return true
    }

    /** Opens Android's notification-access switch for Cabin, or the list of such apps if that fails. */
    private fun openNotificationAccess() {
        val listener = ComponentName(this, CabinNotificationListener::class.java)
        val direct = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
        } else {
            null
        }
        open(*listOfNotNull(direct, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)).toTypedArray())
    }

    /**
     * Starts the first of [intents] the unit has a screen for. Head units often ship a cut-down
     * settings app, so a missing screen is expected; each of these can be granted over adb instead.
     */
    private fun open(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next one.
            } catch (e: SecurityException) {
                // Likewise.
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Cabin draws its own clock, status and tray, so the unit's bars are hidden while it is showing. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        /** `adb shell am start -n com.roufsyed.cabin/.MainActivity --ez design true` on a debug build. */
        const val EXTRA_DESIGN_VALUES = "design"
        private const val KEYS_TAG = "CabinKeys"

        /** Android's settings app asks "turn Bluetooth off?" for this, as it does for the public "on". */
        private const val REQUEST_BLUETOOTH_OFF = "android.bluetooth.adapter.action.REQUEST_DISABLE"

        /** Android's hotspot and tethering page. The name is the settings app's own, not a public constant. */
        private const val HOTSPOT_SETTINGS = "android.settings.TETHER_SETTINGS"
    }
}
