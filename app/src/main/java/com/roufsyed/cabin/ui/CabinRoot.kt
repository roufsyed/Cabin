package com.roufsyed.cabin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roufsyed.cabin.AppContainer
import com.roufsyed.cabin.ui.apps.APPS_PER_PAGE
import com.roufsyed.cabin.ui.apps.AppsGrid
import com.roufsyed.cabin.ui.apps.AppsViewModel
import com.roufsyed.cabin.ui.apps.EditBar
import com.roufsyed.cabin.ui.chrome.BottomRow
import com.roufsyed.cabin.ui.chrome.MiniPlayer
import com.roufsyed.cabin.ui.chrome.Rail
import com.roufsyed.cabin.ui.chrome.TopStrip
import com.roufsyed.cabin.ui.home.HomePanels
import com.roufsyed.cabin.ui.settings.SettingsGrid
import com.roufsyed.cabin.ui.settings.SettingsTile
import com.roufsyed.cabin.ui.shell.EditKind
import com.roufsyed.cabin.ui.shell.Screen
import com.roufsyed.cabin.ui.shell.Shell
import com.roufsyed.cabin.ui.shell.ShellViewModel
import com.roufsyed.cabin.ui.theme.DesignSurface

/** Things only the activity can do, handed down to the screens. */
class HostActions(
    val requestHomeRole: () -> Unit = {},
)

@Composable
private inline fun <reified VM : ViewModel> cabinViewModel(crossinline create: () -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { create() } })

/** The whole of Cabin: the fixed frame, with the current screen in its content area. */
@Composable
fun CabinRoot(container: AppContainer, host: HostActions = HostActions()) {
    val shellModel: ShellViewModel = viewModel()
    val dashboard = cabinViewModel { DashboardViewModel(container) }
    val appsModel = cabinViewModel { AppsViewModel(container.apps, container.settings) }
    val shell by shellModel.state.collectAsStateWithLifecycle()

    // The launcher has nothing behind it, so Back is always handled here and never closes Cabin.
    BackHandler { shellModel.back() }

    DesignSurface {
        CabinFrame(
            content = {
                if (shell.screen == Screen.Home) {
                    HomeContent(dashboard, shellModel)
                } else {
                    Column {
                        if (shell.edit == EditKind.Apps) {
                            AppsEditBar(shell, appsModel, shellModel)
                        } else {
                            Strip(dashboard, shellModel, showMini = shell.screen != Screen.Music)
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.size(1036.dp, 488.dp)) {
                            when (shell.screen) {
                                Screen.Apps -> AppsContent(shell, appsModel, shellModel)
                                Screen.Settings -> SettingsContent(container, shellModel, host)
                                else -> Unit
                            }
                        }
                    }
                }
            },
            bottomRow = { BottomRowContent(dashboard) },
            rail = { RailContent(dashboard, shell.screen == Screen.Home, shellModel) },
        )
    }
}

@Composable
private fun HomeContent(dashboard: DashboardViewModel, shell: ShellViewModel) {
    val clock by dashboard.clock.collectAsStateWithLifecycle()
    val status by dashboard.status.collectAsStateWithLifecycle()
    val nav by dashboard.nav.collectAsStateWithLifecycle()
    val nowPlaying by dashboard.nowPlaying.collectAsStateWithLifecycle()
    HomePanels(
        clock = clock,
        status = status,
        nav = nav,
        nowPlaying = nowPlaying,
        onOpenMaps = {},
        onWhereTo = {},
        onNavigateHome = {},
        onNavigateOffice = {},
        onOpenNowPlaying = {},
        onPrevious = {},
        onPlayPause = {},
        onNext = {},
    )
}

@Composable
private fun Strip(dashboard: DashboardViewModel, shell: ShellViewModel, showMini: Boolean) {
    val clock by dashboard.clock.collectAsStateWithLifecycle()
    val status by dashboard.status.collectAsStateWithLifecycle()
    TopStrip(clock, status, mini = if (!showMini) null else ({
        val nowPlaying by dashboard.nowPlaying.collectAsStateWithLifecycle()
        MiniPlayer(nowPlaying, onOpen = {}, onPrevious = {}, onPlayPause = {}, onNext = {})
    }))
}

@Composable
private fun AppsContent(shell: Shell, apps: AppsViewModel, shellModel: ShellViewModel) {
    val ui by apps.ui.collectAsStateWithLifecycle()
    val editing = shell.edit == EditKind.Apps
    val page = shell.page.coerceIn(0, ui.pageCount(editing) - 1)
    AppsGrid(
        tiles = ui.page(page, editing),
        picked = shell.picked.takeIf { editing },
        onTap = { app ->
            when {
                editing -> shellModel.pick(app.id)
                app.id == apps.ownId -> shellModel.open(Screen.Settings)
                else -> apps.launch(app.id)
            }
        },
    )
}

@Composable
private fun AppsEditBar(shell: Shell, apps: AppsViewModel, shellModel: ShellViewModel) {
    val ui by apps.ui.collectAsStateWithLifecycle()
    val index = ui.all.indexOfFirst { it.app.id == shell.picked }
    val picked = ui.all.getOrNull(index)
    // Moving a tile across a page boundary takes the view with it.
    fun move(delta: Int) {
        val id = picked?.app?.id ?: return
        val target = index + delta
        if (target !in ui.all.indices) return
        apps.move(id, delta)
        shellModel.appsPage(target / APPS_PER_PAGE)
    }
    EditBar(
        title = "Arrange apps",
        hasPick = picked != null,
        pickHidden = picked?.hidden == true,
        onEarlier = { move(-1) },
        onLater = { move(1) },
        onFlip = { picked?.let { apps.toggleHidden(it.app.id) } },
        onDone = { shellModel.done() },
    )
}

@Composable
private fun SettingsContent(container: AppContainer, shellModel: ShellViewModel, host: HostActions) {
    val isHome by container.homeRole.held.collectAsStateWithLifecycle()
    SettingsGrid(
        listOf(
            SettingsTile("Apps", "Choose and arrange", "Arrange apps") { shellModel.arrangeApps() },
            SettingsTile(
                "Home app", "Open Cabin with the Home button", "Make Cabin the home app",
                value = if (isHome) "On" else "Off", on = isHome, onClick = host.requestHomeRole,
            ),
        ),
    )
}

@Composable
private fun BottomRowContent(dashboard: DashboardViewModel) {
    val brightness by dashboard.brightness.collectAsStateWithLifecycle()
    val sources by dashboard.sources.collectAsStateWithLifecycle()
    BottomRow(
        brightness = brightness,
        sources = sources,
        onBrightnessDown = {},
        onBrightnessMode = {},
        onBrightnessUp = {},
        onSource = {},
    )
}

@Composable
private fun RailContent(dashboard: DashboardViewModel, onHome: Boolean, shell: ShellViewModel) {
    val volume by dashboard.volume.collectAsStateWithLifecycle()
    Rail(
        onHome = onHome,
        volume = volume,
        onPhone = {},
        onLink = {},
        onAppsOrHome = { if (onHome) shell.open(Screen.Apps) else shell.home() },
        onVolumeUp = {},
        onMute = {},
        onVolumeDown = {},
    )
}
