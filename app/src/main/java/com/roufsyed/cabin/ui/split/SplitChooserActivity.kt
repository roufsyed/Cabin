package com.roufsyed.cabin.ui.split

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roufsyed.cabin.AppContainer
import com.roufsyed.cabin.CabinApp
import com.roufsyed.cabin.apps.SplitChoices
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.ui.apps.AppsViewModel
import com.roufsyed.cabin.ui.chrome.SIDE_BAR_WIDTH
import com.roufsyed.cabin.ui.theme.DESIGN_WIDTH
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Themes
import com.roufsyed.cabin.ui.theme.screenScale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The chooser for a second app, in half the screen beside the app that was in front when Split was
 * pressed on the side bar. A tap on an app opens it in this half, in the chooser's place. Back, or
 * leaving in any other way, closes the chooser, and the other app has the whole screen again.
 */
class SplitChooserActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as CabinApp).container

    /** The packages of the apps that were on screen when Split was pressed, which are left out of the choice. */
    private var onScreen by mutableStateOf<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onScreen = intent.getStringArrayListExtra(EXTRA_ON_SCREEN).orEmpty()
        val container = container
        lifecycleScope.launch {
            container.settings.settings.map { it.theme }.distinctUntilChanged().collect { id ->
                val palette = Themes.palette(id)
                Slate.palette = palette
                window.setBackgroundDrawable(palette.ground.toArgb().toDrawable())
            }
        }
        // A design pixel is as large here as on Cabin's own screen, however little of the screen this is.
        val scale = screenScale(this)
        val screenWidth = scale * DESIGN_WIDTH
        val barWantedAtFirst = container.settings.settings.value.sideBar
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = scale, fontScale = 1f)) {
                val apps: AppsViewModel = viewModel(
                    factory = viewModelFactory { initializer { AppsViewModel(container.apps, container.settings) } },
                )
                val ui by apps.ui.collectAsStateWithLifecycle()
                val barWanted by remember(container) {
                    container.settings.settings.map { it.sideBar }.distinctUntilChanged()
                }.collectAsStateWithLifecycle(initialValue = barWantedAtFirst)
                val barAllowed by container.sideBar.allowed.collectAsStateWithLifecycle()
                // How far this window reaches under the side bar, which lies along the screen's right edge.
                var underBar by remember { mutableFloatStateOf(0f) }
                val view = LocalView.current
                Box(
                    Modifier.fillMaxSize().onGloballyPositioned {
                        val place = IntArray(2)
                        view.getLocationOnScreen(place)
                        val right = place[0] + view.width
                        underBar = (right - (screenWidth - SIDE_BAR_WIDTH * scale)).coerceIn(0f, SIDE_BAR_WIDTH * scale)
                    },
                ) {
                    SplitChooser(
                        apps = remember(ui, onScreen) { SplitChoices.of(ui.visible, container.apps.ownId, onScreen) },
                        barRoom = if (barWanted && barAllowed) (underBar / scale).dp else 0.dp,
                        onPick = ::open,
                        onCancel = ::finish,
                    )
                }
            }
        }
    }

    /** Split pressed again while the chooser is up: what is beside it may be another app by now. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        onScreen = intent.getStringArrayListExtra(EXTRA_ON_SCREEN).orEmpty()
    }

    /** The app takes this half only if it is this activity that starts it. Then the chooser goes. */
    private fun open(app: AppEntry) {
        container.apps.launchBeside(app.id) { intent -> startActivity(intent) }
        finish()
    }

    companion object {
        private const val EXTRA_ON_SCREEN = "on_screen"

        /** The chooser, told which apps are on screen already: [onScreen] are their packages. */
        fun intent(context: Context, onScreen: List<String>): Intent =
            Intent(context, SplitChooserActivity::class.java).putStringArrayListExtra(EXTRA_ON_SCREEN, ArrayList(onScreen))

        /**
         * What Cabin's activity starts to put the chooser beside the app in front. "Adjacent" is what
         * makes Android split the screen between that app and the chooser.
         */
        fun beside(context: Context, onScreen: List<String>): Intent =
            intent(context, onScreen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
    }
}
