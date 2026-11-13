package com.roufsyed.cabin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.roufsyed.cabin.notify.CabinNotificationListener
import com.roufsyed.cabin.ui.CabinRoot
import com.roufsyed.cabin.ui.HostActions
import com.roufsyed.cabin.ui.shell.ShellViewModel

/** Cabin's only activity. It is the unit's home screen, so it is never finished. */
class MainActivity : ComponentActivity() {

    private val shell: ShellViewModel by viewModels()
    private val container: AppContainer get() = (application as CabinApp).container

    private val homeRoleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            container.homeRole.refresh()
        }

    /** Who is waiting to hear whether the microphone was granted. */
    private var microphoneAnswer: ((Boolean) -> Unit)? = null
    private val microphoneRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            microphoneAnswer?.invoke(granted)
            microphoneAnswer = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DESIGN_VALUES, false) == true) {
            (application as CabinApp).showDesignValues = true
        }
        hideSystemBars()
        val container = container
        val host = HostActions(
            requestHomeRole = { container.homeRole.requestIntent()?.let(homeRoleRequest::launch) },
            requestNotificationAccess = ::openNotificationAccess,
            requestWriteSettings = ::openWriteSettings,
            withMicrophone = ::withMicrophone,
        )
        setContent { CabinRoot(container, host) }
        followWindowBrightness()
    }

    /**
     * Until Cabin is allowed to change the unit's brightness setting, the stepper dims Cabin's own
     * window instead, so it is never a dead control.
     */
    private fun followWindowBrightness() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.brightness.windowBrightness.collect { level ->
                    window.attributes = window.attributes.apply {
                        screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }
        }
    }

    /**
     * Volume keys, which is also how the steering wheel's volume buttons arrive, step Cabin's volume
     * directly, so the rail's number moves and Android's own volume panel does not cover the screen.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> container.volume.up()
            KeyEvent.KEYCODE_VOLUME_DOWN -> container.volume.down()
            KeyEvent.KEYCODE_VOLUME_MUTE -> if (event.repeatCount == 0) container.volume.toggleMute()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> super.onKeyUp(keyCode, event)
    }

    private fun withMicrophone(answer: (Boolean) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            answer(true)
        } else {
            microphoneAnswer = answer
            microphoneRequest.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun openWriteSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            // The unit's settings app has no such screen; NOTES.md gives the adb command instead.
        }
    }

    /** The Home button while Cabin is already showing: go back to the Home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) shell.home()
    }

    override fun onResume() {
        super.onResume()
        // The user may be coming back from a system settings screen.
        container.homeRole.refresh()
        container.media.refreshAccess()
        container.brightness.refresh()
        container.volume.refresh()
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
        for (intent in listOfNotNull(direct, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next screen; some units leave these out of their settings app.
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
    }
}
