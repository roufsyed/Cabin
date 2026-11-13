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
import com.roufsyed.cabin.model.AppTile
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.sources.Opened
import com.roufsyed.cabin.ui.apps.APPS_PER_PAGE
import com.roufsyed.cabin.ui.apps.AppsGrid
import com.roufsyed.cabin.ui.apps.AppsUi
import com.roufsyed.cabin.ui.apps.AppsViewModel
import com.roufsyed.cabin.ui.apps.EditBar
import com.roufsyed.cabin.ui.apps.PickBar
import com.roufsyed.cabin.ui.chrome.BottomRow
import com.roufsyed.cabin.ui.chrome.MiniPlayer
import com.roufsyed.cabin.ui.chrome.Rail
import com.roufsyed.cabin.ui.chrome.TopStrip
import com.roufsyed.cabin.ui.home.HomePanels
import com.roufsyed.cabin.ui.music.MusicScreen
import com.roufsyed.cabin.ui.settings.SettingsGrid
import com.roufsyed.cabin.ui.settings.SettingsTile
import com.roufsyed.cabin.ui.shell.EditKind
import com.roufsyed.cabin.ui.shell.Screen
import com.roufsyed.cabin.ui.shell.Shell
import com.roufsyed.cabin.ui.shell.ShellViewModel
import com.roufsyed.cabin.ui.sources.SourcesGrid
import com.roufsyed.cabin.ui.theme.DesignSurface

/** Things only the activity can do, handed down to the screens. */
class HostActions(
    val requestHomeRole: () -> Unit = {},
    val requestNotificationAccess: () -> Unit = {},
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

    // A slot with no app behind it leads to the chooser instead of doing nothing.
    val selectSource: (SourceId) -> Unit = { source ->
        if (dashboard.selectSource(source) == Opened.NeedsApp) shellModel.pickApp(Slot.of(source))
    }
    val openSlot: (Slot) -> Unit = { slot ->
        if (dashboard.open(slot) == Opened.NeedsApp) shellModel.pickApp(slot)
    }

    DesignSurface {
        CabinFrame(
            content = {
                if (shell.screen == Screen.Home) {
                    HomeContent(dashboard, shellModel, host)
                } else {
                    Column {
                        when {
                            shell.edit == EditKind.Apps -> AppsEditBar(shell, appsModel, shellModel)
                            shell.edit == EditKind.Sources -> SourcesEditBar(shell, container, shellModel)
                            shell.screen == Screen.Pick -> SlotPickBar(shell, container, shellModel)
                            else -> Strip(dashboard, shellModel, host, showMini = shell.screen != Screen.Music)
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.size(1036.dp, 488.dp)) {
                            when (shell.screen) {
                                Screen.Apps -> AppsContent(shell, appsModel, shellModel)
                                Screen.Music -> MusicContent(dashboard)
                                Screen.Settings -> SettingsContent(container, shellModel, host)
                                Screen.Sources -> SourcesContent(shell, container, shellModel)
                                Screen.Pick -> SlotPickContent(shell, container, appsModel, shellModel)
                                else -> Unit
                            }
                        }
                    }
                }
            },
            bottomRow = { BottomRowContent(dashboard, selectSource) },
            rail = { RailContent(dashboard, shell.screen == Screen.Home, shellModel, openSlot) },
        )
    }
}

@Composable
private fun HomeContent(dashboard: DashboardViewModel, shell: ShellViewModel, host: HostActions) {
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
        onOpenNowPlaying = { if (nowPlaying.needsAccess) host.requestNotificationAccess() else shell.open(Screen.Music) },
        onPrevious = dashboard::previous,
        onPlayPause = dashboard::playPause,
        onNext = dashboard::next,
    )
}

@Composable
private fun Strip(dashboard: DashboardViewModel, shell: ShellViewModel, host: HostActions, showMini: Boolean) {
    val clock by dashboard.clock.collectAsStateWithLifecycle()
    val status by dashboard.status.collectAsStateWithLifecycle()
    TopStrip(clock, status, mini = if (!showMini) null else ({
        val nowPlaying by dashboard.nowPlaying.collectAsStateWithLifecycle()
        MiniPlayer(
            nowPlaying = nowPlaying,
            onOpen = { if (nowPlaying.needsAccess) host.requestNotificationAccess() else shell.open(Screen.Music) },
            onPrevious = dashboard::previous,
            onPlayPause = dashboard::playPause,
            onNext = dashboard::next,
        )
    }))
}

@Composable
private fun MusicContent(dashboard: DashboardViewModel) {
    val nowPlaying by dashboard.nowPlaying.collectAsStateWithLifecycle()
    MusicScreen(
        now = nowPlaying,
        onPrevious = dashboard::previous,
        onPlayPause = dashboard::playPause,
        onNext = dashboard::next,
        onQueueItem = dashboard::playQueueItem,
    )
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

private fun pickedSource(shell: Shell): SourceId? = SourceId.entries.firstOrNull { it.name == shell.picked }

@Composable
private fun SourcesContent(shell: Shell, container: AppContainer, shellModel: ShellViewModel) {
    val state by container.sources.state.collectAsStateWithLifecycle()
    SourcesGrid(state.tiles, picked = pickedSource(shell), onTap = { shellModel.pick(it.name) })
}

@Composable
private fun SourcesEditBar(shell: Shell, container: AppContainer, shellModel: ShellViewModel) {
    val state by container.sources.state.collectAsStateWithLifecycle()
    val picked = state.tiles.firstOrNull { it.id == pickedSource(shell) }
    EditBar(
        title = "Arrange sources",
        hasPick = picked != null,
        pickHidden = picked?.hidden == true,
        onEarlier = { picked?.let { container.sources.move(it.id, -1) } },
        onLater = { picked?.let { container.sources.move(it.id, 1) } },
        onFlip = { picked?.let { container.sources.toggleHidden(it.id) } },
        onDone = { shellModel.done() },
        // Once a source is tapped, say which of the unit's apps it opens.
        hint = when {
            picked == null -> "Tap one, then move or hide it"
            picked.app == null -> "No app chosen yet"
            else -> "Opens ${picked.app}"
        },
        extraLabel = "App",
        extraDescription = "Choose the app for this source",
        onExtra = { picked?.let { shellModel.pickApp(Slot.of(it.id)) } },
    )
}

@Composable
private fun SlotPickBar(shell: Shell, container: AppContainer, shellModel: ShellViewModel) {
    val slot = shell.pickSlot ?: return
    PickBar(
        title = "${slot.label} opens",
        hint = "Tap the app for it",
        onAutomatic = {
            container.sources.bind(slot, null)
            shellModel.pickDone()
        },
        onBack = { shellModel.pickDone() },
    )
}

@Composable
private fun SlotPickContent(shell: Shell, container: AppContainer, apps: AppsViewModel, shellModel: ShellViewModel) {
    val slot = shell.pickSlot ?: return
    val ui by apps.ui.collectAsStateWithLifecycle()
    val state by container.sources.state.collectAsStateWithLifecycle()
    // Every installed app can be chosen, hidden ones too, but not Cabin itself.
    val choices = AppsUi(ui.all.filter { it.app.id != apps.ownId }.map { AppTile(it.app, hidden = false) })
    val page = shell.page.coerceIn(0, choices.pageCount(editing = true) - 1)
    AppsGrid(
        tiles = choices.page(page, editing = true),
        picked = state.bindings[slot]?.id,
        onTap = { app ->
            container.sources.bind(slot, app.id)
            shellModel.pickDone()
        },
    )
}

@Composable
private fun SettingsContent(container: AppContainer, shellModel: ShellViewModel, host: HostActions) {
    val isHome by container.homeRole.held.collectAsStateWithLifecycle()
    val notifications by container.media.access.collectAsStateWithLifecycle()
    SettingsGrid(
        listOf(
            SettingsTile("Apps", "Choose and arrange", "Arrange apps") { shellModel.arrangeApps() },
            SettingsTile("Sources", "Choose and arrange", "Arrange sources") { shellModel.arrangeSources() },
            SettingsTile(
                "Home app", "Open Cabin with the Home button", "Make Cabin the home app",
                value = if (isHome) "On" else "Off", on = isHome, onClick = host.requestHomeRole,
            ),
            SettingsTile(
                "Notification access", "Shows what is playing", "Allow notification access",
                value = if (notifications) "On" else "Off", on = notifications, onClick = host.requestNotificationAccess,
            ),
        ),
    )
}

@Composable
private fun BottomRowContent(dashboard: DashboardViewModel, onSource: (SourceId) -> Unit) {
    val brightness by dashboard.brightness.collectAsStateWithLifecycle()
    val sources by dashboard.sources.collectAsStateWithLifecycle()
    BottomRow(
        brightness = brightness,
        sources = sources,
        onBrightnessDown = {},
        onBrightnessMode = {},
        onBrightnessUp = {},
        onSource = onSource,
    )
}

@Composable
private fun RailContent(
    dashboard: DashboardViewModel,
    onHome: Boolean,
    shell: ShellViewModel,
    onSlot: (Slot) -> Unit,
) {
    val volume by dashboard.volume.collectAsStateWithLifecycle()
    Rail(
        onHome = onHome,
        volume = volume,
        onPhone = { onSlot(Slot.Phone) },
        onLink = { onSlot(Slot.Link) },
        onAppsOrHome = { if (onHome) shell.open(Screen.Apps) else shell.home() },
        onVolumeUp = {},
        onMute = {},
        onVolumeDown = {},
    )
}
