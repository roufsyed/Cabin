package com.roufsyed.cabin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.roufsyed.cabin.ui.CabinFrame
import com.roufsyed.cabin.ui.chrome.BottomRow
import com.roufsyed.cabin.ui.chrome.Rail
import com.roufsyed.cabin.ui.home.HomePanels
import com.roufsyed.cabin.ui.model.Samples
import com.roufsyed.cabin.ui.theme.DesignSurface

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            DesignSurface {
                CabinFrame(
                    content = {
                        HomePanels(
                            clock = Samples.clock,
                            status = Samples.status,
                            nav = Samples.nav,
                            nowPlaying = Samples.nowPlaying,
                            onOpenMaps = {},
                            onWhereTo = {},
                            onNavigateHome = {},
                            onNavigateOffice = {},
                            onOpenNowPlaying = {},
                            onPrevious = {},
                            onPlayPause = {},
                            onNext = {},
                        )
                    },
                    bottomRow = {
                        BottomRow(
                            brightness = Samples.brightness,
                            sources = Samples.sources,
                            onBrightnessDown = {},
                            onBrightnessMode = {},
                            onBrightnessUp = {},
                            onSource = {},
                        )
                    },
                    rail = {
                        Rail(
                            onHome = true,
                            volume = Samples.volume,
                            onPhone = {},
                            onLink = {},
                            onAppsOrHome = {},
                            onVolumeUp = {},
                            onMute = {},
                            onVolumeDown = {},
                        )
                    },
                )
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
}
