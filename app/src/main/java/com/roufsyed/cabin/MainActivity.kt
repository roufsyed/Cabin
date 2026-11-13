package com.roufsyed.cabin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DESIGN_VALUES, false) == true) {
            (application as CabinApp).showDesignValues = true
        }
        hideSystemBars()
        val container = container
        val host = HostActions(
            requestHomeRole = { container.homeRole.requestIntent()?.let(homeRoleRequest::launch) },
        )
        setContent { CabinRoot(container, host) }
    }

    /** The Home button while Cabin is already showing: go back to the Home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) shell.home()
    }

    override fun onResume() {
        super.onResume()
        container.homeRole.refresh()
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
