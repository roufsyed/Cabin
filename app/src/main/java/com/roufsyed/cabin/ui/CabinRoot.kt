package com.roufsyed.cabin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roufsyed.cabin.AppContainer
import com.roufsyed.cabin.apps.SplitPair
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.SideSwipe
import com.roufsyed.cabin.data.TwoFingerDrag
import com.roufsyed.cabin.data.next
import com.roufsyed.cabin.gesture.Gesture
import com.roufsyed.cabin.gesture.cabinGestures
import com.roufsyed.cabin.model.AppTile
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.ThemeId
import com.roufsyed.cabin.nav.SavedPlace
import com.roufsyed.cabin.sources.Opened
import com.roufsyed.cabin.system.BrightnessRules
import com.roufsyed.cabin.system.Grant
import com.roufsyed.cabin.ui.apps.APPS_PER_PAGE
import com.roufsyed.cabin.ui.apps.AppsGrid
import com.roufsyed.cabin.ui.apps.AppsUi
import com.roufsyed.cabin.ui.apps.AppsViewModel
import com.roufsyed.cabin.ui.apps.EditBar
import com.roufsyed.cabin.ui.apps.PickBar
import com.roufsyed.cabin.ui.apps.TitleBar
import com.roufsyed.cabin.ui.chrome.BottomRow
import com.roufsyed.cabin.ui.chrome.MiniPlayer
import com.roufsyed.cabin.ui.chrome.Rail
import com.roufsyed.cabin.ui.chrome.TopStrip
import com.roufsyed.cabin.ui.home.HomePanels
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.music.MusicScreen
import com.roufsyed.cabin.ui.overlay.BrightnessHud
import com.roufsyed.cabin.ui.overlay.ScreenOffClock
import com.roufsyed.cabin.ui.overlay.TrayOverlay
import com.roufsyed.cabin.ui.overlay.VoiceOverlay
import com.roufsyed.cabin.ui.overlay.VolumeHud
import com.roufsyed.cabin.ui.settings.KeysScreen
import com.roufsyed.cabin.ui.settings.SettingsGrid
import com.roufsyed.cabin.ui.settings.SettingsTile
import com.roufsyed.cabin.ui.settings.ThemeGrid
import com.roufsyed.cabin.ui.settings.WheelActions
import com.roufsyed.cabin.ui.shell.EditKind
import com.roufsyed.cabin.ui.shell.Overlay
import com.roufsyed.cabin.ui.shell.SETUP_PAGES
import com.roufsyed.cabin.ui.shell.Screen
import com.roufsyed.cabin.ui.shell.Shell
import com.roufsyed.cabin.ui.shell.ShellViewModel
import com.roufsyed.cabin.ui.sources.SourcesGrid
import com.roufsyed.cabin.ui.theme.DesignSurface
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Things only the activity can do, handed down to the screens. */
class HostActions(
    val requestHomeRole: () -> Unit = {},
    val requestNotificationAccess: () -> Unit = {},
    val requestWriteSettings: () -> Unit = {},
    /** Asks for the microphone if it has not been granted, then reports whether it may be used. */
    val withMicrophone: ((Boolean) -> Unit) -> Unit = { it(true) },
    /** Shows Android's dialog for one of the optional permissions. */
    val requestGrant: (Grant) -> Unit = {},
    val openBluetooth: () -> Unit = {},
    val openWifi: () -> Unit = {},
    /** Opens two apps side by side. The activity has to do it itself: see `AppsSource.launchPair`. */
    val openPair: (SplitPair) -> Boolean = { false },
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
    val voiceModel = cabinViewModel { VoiceViewModel(container) }
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

    // The Navigation panel works through the maps app chosen in Setup. With none on the unit that
    // Cabin can find, it leads to the chooser like any other slot.
    val openMaps: () -> Unit = { if (!dashboard.openMaps()) shellModel.pickApp(Slot.Maps) }
    val navigateTo: (SavedPlace) -> Unit = { place ->
        if (!dashboard.navigateTo(place)) shellModel.pickApp(Slot.Maps)
    }

    // "Where to?" listens for a destination. With no recogniser on the unit, or no microphone
    // permission, it opens the maps app instead, which has its own search.
    val whereTo: () -> Unit = {
        if (!container.voice.available) {
            openMaps()
        } else {
            host.withMicrophone { granted ->
                if (granted) {
                    shellModel.show(Overlay.Voice)
                    voiceModel.listen(onDone = shellModel::closeOverlay)
                } else {
                    openMaps()
                }
            }
        }
    }

    // The wheel's voice key does what "Where to?" does.
    LaunchedEffect(Unit) { shellModel.voiceRequests.collect { whereTo() } }

    // The first time Cabin runs it opens on Setup, which walks through what it needs from the unit.
    LaunchedEffect(Unit) {
        if (!container.settings.settings.first { it.loaded }.setupDone) shellModel.open(Screen.Setup)
    }

    val wheel = WheelActions(
        volumeUp = dashboard::volumeUp,
        volumeDown = dashboard::volumeDown,
        next = dashboard::next,
        previous = dashboard::previous,
        voice = whereTo,
        mode = { container.sources.nextSource()?.let(selectSource) },
    )

    // The two-finger drag: which readout is up, if any, the level the drag began at, and, for
    // brightness, the level it has been moved to so far.
    var drag by remember { mutableStateOf<TwoFingerDrag?>(null) }
    var dragFrom by remember { mutableIntStateOf(0) }
    var dragAt by remember { mutableIntStateOf(0) }
    val onGesture: (Gesture) -> Unit = { gesture ->
        // What each gesture does is the user's choice, in Settings > Gestures; read as it is now.
        val chosen = container.settings.settings.value
        val onHome = shell.screen == Screen.Home && shell.overlay == Overlay.None
        when (gesture) {
            // On Home a sideways swipe does what was chosen. Everywhere else it turns pages and
            // leads back to Home, which the grids rely on to reach their further pages.
            Gesture.SwipeLeft -> when {
                !onHome || chosen.sideSwipe == SideSwipe.Apps -> shellModel.swipeLeft(pageCount(shell, appsModel))
                chosen.sideSwipe == SideSwipe.Tracks -> dashboard.next()
                else -> Unit
            }
            Gesture.SwipeRight -> when {
                !onHome -> shellModel.swipeRight()
                chosen.sideSwipe == SideSwipe.Tracks -> dashboard.previous()
                else -> Unit
            }
            Gesture.SwipeDown -> if (chosen.swipeDownTray && shell.overlay == Overlay.None) shellModel.show(Overlay.Tray)
            Gesture.SwipeUp -> if (shell.overlay == Overlay.Tray) shellModel.closeOverlay()
            Gesture.VolumeBegin -> when (chosen.twoFingerDrag) {
                TwoFingerDrag.Volume -> {
                    dragFrom = dashboard.volume.value.level
                    drag = TwoFingerDrag.Volume
                }
                TwoFingerDrag.Brightness -> {
                    dragFrom = dashboard.brightness.value.level
                    dragAt = dragFrom
                    drag = TwoFingerDrag.Brightness
                }
                TwoFingerDrag.Off -> Unit
            }
            is Gesture.Volume -> when (drag) {
                TwoFingerDrag.Volume -> dashboard.setVolume(dragFrom + gesture.steps)
                TwoFingerDrag.Brightness -> {
                    // Brightness is saved in steps, so the drag asks for the difference from where it
                    // has already been taken; that keeps it where the fingers are even past either end.
                    val target = (dragFrom + gesture.steps).coerceIn(BrightnessRules.MIN_LEVEL, BrightnessRules.MAX_LEVEL)
                    if (target != dragAt) dashboard.stepBrightness(target - dragAt)
                    dragAt = target
                }
                else -> Unit
            }
            Gesture.VolumeEnd -> drag = null
        }
    }

    // Nothing is drawn until the saved settings have been read, which takes a few milliseconds:
    // otherwise every start would show a frame of the default theme, order and levels first.
    val loaded by remember(container) {
        container.settings.settings.map { it.loaded }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    DesignSurface {
        if (loaded) Box(Modifier.fillMaxSize().cabinGestures(onGesture)) {
            CabinFrame(
                content = {
                    if (shell.screen == Screen.Home) {
                        HomeContent(dashboard, shellModel, host, whereTo, openMaps, navigateTo)
                    } else {
                        Column {
                            when {
                                shell.edit == EditKind.Apps -> AppsEditBar(shell, appsModel, shellModel)
                                shell.edit == EditKind.Sources -> SourcesEditBar(shell, container, shellModel)
                                shell.screen == Screen.Pick -> SlotPickBar(shell, container, shellModel)
                                shell.screen == Screen.Split -> SplitBar(shell, appsModel, shellModel)
                                shell.screen == Screen.Setup -> SetupBar(shell, container, shellModel)
                                shell.screen == Screen.Preferences -> PreferencesBar(shellModel)
                                shell.screen == Screen.Theme -> ThemeBar(shellModel)
                                else -> Strip(dashboard, shellModel, host, showMini = shell.screen != Screen.Music)
                            }
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.size(1036.dp, 488.dp)) {
                                when (shell.screen) {
                                    Screen.Apps -> AppsContent(shell, appsModel, shellModel)
                                    Screen.Music -> MusicContent(dashboard)
                                    Screen.Settings -> SettingsContent(container, shellModel, host)
                                    Screen.Keys -> KeysScreen(wheel, onBack = { shellModel.open(Screen.Settings) })
                                    Screen.Preferences -> PreferencesContent(container, shellModel)
                                    Screen.Theme -> ThemeContent(container)
                                    Screen.Setup -> SetupContent(shell, container, shellModel, host)
                                    Screen.Sources -> SourcesContent(shell, container, shellModel)
                                    Screen.Pick -> SlotPickContent(shell, container, appsModel, shellModel)
                                    Screen.Split -> SplitContent(shell, appsModel, shellModel, host)
                                    else -> Unit
                                }
                            }
                        }
                    }
                },
                bottomRow = { BottomRowContent(dashboard, selectSource) },
                rail = { RailContent(dashboard, shell.screen == Screen.Home, shellModel, openSlot) },
            )
            if (shell.overlay == Overlay.Voice) {
                val voice by voiceModel.ui.collectAsStateWithLifecycle()
                VoiceOverlay(
                    ui = voice,
                    onRetry = { voiceModel.listen(onDone = shellModel::closeOverlay) },
                    onPick = { place ->
                        voiceModel.go(place)
                        shellModel.closeOverlay()
                    },
                    onCancel = {
                        voiceModel.cancel()
                        shellModel.closeOverlay()
                    },
                )
            }
            if (shell.overlay == Overlay.Tray) TrayContent(container, shellModel, host)
            if (shell.overlay == Overlay.ScreenOff) {
                val clock by dashboard.clock.collectAsStateWithLifecycle()
                ScreenOffClock(clock, onWake = shellModel::closeOverlay)
            }
            when (drag) {
                TwoFingerDrag.Volume -> VolumeReadout(dashboard)
                TwoFingerDrag.Brightness -> BrightnessReadout(dashboard)
                else -> Unit
            }
        }
    }
}

@Composable
private fun TrayContent(container: AppContainer, shellModel: ShellViewModel, host: HostActions) {
    val items by container.tray.items.collectAsStateWithLifecycle()
    val access by container.media.access.collectAsStateWithLifecycle()
    TrayOverlay(
        items = items,
        needsAccess = !access,
        onOpen = { item ->
            container.tray.open(item.key)
            shellModel.closeOverlay()
        },
        onAllowAccess = {
            shellModel.closeOverlay()
            host.requestNotificationAccess()
        },
        onClearAll = container.tray::clearAll,
        onScreenOff = { shellModel.show(Overlay.ScreenOff) },
        onClose = shellModel::closeOverlay,
    )
}

/** How many pages the grid on screen has, so a swipe left knows whether there is a next one. */
private fun pageCount(shell: Shell, apps: AppsViewModel): Int {
    val ui = apps.ui.value
    return when {
        shell.screen == Screen.Setup -> SETUP_PAGES
        shell.screen == Screen.Pick -> AppsUi.pagesFor(ui.all.count { it.app.id != apps.ownId })
        shell.screen == Screen.Split -> ui.beside(shell.splitFirst.orEmpty(), apps.ownId).pageCount(editing = false)
        else -> ui.pageCount(editing = shell.edit == EditKind.Apps)
    }
}

@Composable
private fun VolumeReadout(dashboard: DashboardViewModel) {
    val volume by dashboard.volume.collectAsStateWithLifecycle()
    VolumeHud(volume.level)
}

@Composable
private fun BrightnessReadout(dashboard: DashboardViewModel) {
    val brightness by dashboard.brightness.collectAsStateWithLifecycle()
    BrightnessHud(brightness.level, brightness.night)
}

@Composable
private fun HomeContent(
    dashboard: DashboardViewModel,
    shell: ShellViewModel,
    host: HostActions,
    onWhereTo: () -> Unit,
    onOpenMaps: () -> Unit,
    onNavigateTo: (SavedPlace) -> Unit,
) {
    val clock by dashboard.clock.collectAsStateWithLifecycle()
    val status by dashboard.status.collectAsStateWithLifecycle()
    val nav by dashboard.nav.collectAsStateWithLifecycle()
    val nowPlaying by dashboard.nowPlaying.collectAsStateWithLifecycle()
    HomePanels(
        clock = clock,
        status = status,
        nav = nav,
        nowPlaying = nowPlaying,
        onOpenMaps = onOpenMaps,
        onWhereTo = onWhereTo,
        onNavigateHome = { onNavigateTo(SavedPlace.Home) },
        onNavigateOffice = { onNavigateTo(SavedPlace.Work) },
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
    val holdToSplit by apps.holdToSplit.collectAsStateWithLifecycle()
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
        // Holding an app leads to choosing a second one to show beside it. Cabin's own tile cannot
        // be split, and while arranging a hold is just a tap.
        onHold = if (!editing && holdToSplit) {
            { app -> if (app.id != apps.ownId) shellModel.splitWith(app.id) }
        } else {
            null
        },
    )
}

@Composable
private fun SplitBar(shell: Shell, apps: AppsViewModel, shellModel: ShellViewModel) {
    val ui by apps.ui.collectAsStateWithLifecycle()
    val first = ui.all.firstOrNull { it.app.id == shell.splitFirst }?.app
    TitleBar(
        title = "Split screen",
        hint = if (first == null) "Tap the app for the right." else "${first.label} on the left. Tap the app for the right.",
        action = "Back",
        actionDescription = "Cancel split screen",
        onAction = shellModel::splitDone,
    )
}

/** The second half of a split: the grid again, without the app that was held and without Cabin. */
@Composable
private fun SplitContent(shell: Shell, apps: AppsViewModel, shellModel: ShellViewModel, host: HostActions) {
    val first = shell.splitFirst ?: return
    val ui by apps.ui.collectAsStateWithLifecycle()
    val choices = ui.beside(first, apps.ownId)
    val page = shell.page.coerceIn(0, choices.pageCount(editing = false) - 1)
    AppsGrid(
        tiles = choices.page(page, editing = false),
        picked = null,
        onTap = { second ->
            apps.launchPair(first, second.id, host.openPair)
            shellModel.splitDone()
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
    val status by container.status.status.collectAsStateWithLifecycle()
    val bluetooth by container.status.bluetoothOn.collectAsStateWithLifecycle()
    SettingsGrid(
        listOf(
            SettingsTile("Apps", "Choose and arrange", "Arrange apps") { shellModel.arrangeApps() },
            SettingsTile("Sources", "Choose and arrange", "Arrange sources") { shellModel.arrangeSources() },
            SettingsTile("Steering wheel keys", "Six keys mapped", "Steering wheel keys") { shellModel.open(Screen.Keys) },
            SettingsTile("Screen off", "Clock only, tap to wake", "Turn the screen off") {
                shellModel.show(Overlay.ScreenOff)
            },
            // Android does not let an ordinary app switch these, so the tiles show the state and open
            // the unit's own switch.
            SettingsTile(
                "Bluetooth", "Phone calls and music", "Bluetooth settings",
                value = if (bluetooth) "On" else "Off", on = bluetooth, onClick = host.openBluetooth,
            ),
            SettingsTile(
                "Wi-Fi", "Hotspot and updates", "Wi-Fi settings",
                value = if (status.wifi) "On" else "Off", on = status.wifi, onClick = host.openWifi,
            ),
            SettingsTile("Theme and gestures", "Colours and what swipes do", "Theme and gestures") {
                shellModel.open(Screen.Preferences)
            },
            SettingsTile("Setup", "Permissions and apps", "Open setup") { shellModel.open(Screen.Setup) },
        ),
    )
}

@Composable
private fun PreferencesBar(shellModel: ShellViewModel) {
    TitleBar(
        title = "Theme and gestures",
        hint = "Tap one to change it",
        action = "Done",
        actionDescription = "Back to settings",
        onAction = { shellModel.open(Screen.Settings) },
    )
}

/**
 * Theme and gestures: the colour theme, then what each of Cabin's finger movements does. The theme
 * tile leads to the chooser; a tap on a gesture's tile moves it to its next option.
 */
@Composable
private fun PreferencesContent(container: AppContainer, shellModel: ShellViewModel) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val canSplit = container.apps.splitScreen
    fun change(transform: (CabinSettings) -> CabinSettings) {
        container.scope.launch { container.settings.update(transform) }
    }
    SettingsGrid(
        listOf(
            SettingsTile(
                "Theme", "Colours of the screen", "Choose a theme",
                value = settings.theme.label, on = true,
                onClick = { shellModel.open(Screen.Theme) },
            ),
            SettingsTile(
                "Swipe down", "One finger", "Swipe down: tray or off",
                value = if (settings.swipeDownTray) "Tray" else "Off", on = settings.swipeDownTray,
                onClick = { change { it.copy(swipeDownTray = !it.swipeDownTray) } },
            ),
            SettingsTile(
                "Swipe sideways", "One finger, on Home", "Swipe sideways: apps, tracks or off",
                value = settings.sideSwipe.label, on = settings.sideSwipe != SideSwipe.Off,
                onClick = { change { it.copy(sideSwipe = it.sideSwipe.next()) } },
            ),
            SettingsTile(
                "Two-finger drag", "Up or down", "Two-finger drag: volume, brightness or off",
                value = settings.twoFingerDrag.label, on = settings.twoFingerDrag != TwoFingerDrag.Off,
                onClick = { change { it.copy(twoFingerDrag = it.twoFingerDrag.next()) } },
            ),
            // Holding an app in the grid opens it beside a second one. Where the unit has no split
            // screen the tile says so and stays off.
            SettingsTile(
                "Long press", if (canSplit) "On an app" else "Unit has no split screen",
                "Long press on an app: split screen or off",
                value = if (canSplit && settings.longPressSplit) "Split" else "Off",
                on = canSplit && settings.longPressSplit,
                onClick = { if (canSplit) change { it.copy(longPressSplit = !it.longPressSplit) } },
            ),
        ),
    )
}

@Composable
private fun ThemeBar(shellModel: ShellViewModel) {
    TitleBar(
        title = "Theme",
        hint = "Tap one to use it",
        action = "Done",
        actionDescription = "Back to theme and gestures",
        onAction = { shellModel.open(Screen.Preferences) },
    )
}

/** The ten themes. A tap saves the choice; the whole screen follows it at once, which is the preview. */
@Composable
private fun ThemeContent(container: AppContainer) {
    val current by remember(container) {
        container.settings.settings.map { it.theme }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = ThemeId.Blue)
    ThemeGrid(
        current = current,
        onPick = { id -> container.scope.launch { container.settings.update { it.copy(theme = id) } } },
    )
}

@Composable
private fun SetupBar(shell: Shell, container: AppContainer, shellModel: ShellViewModel) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val firstRun = !settings.setupDone
    val onApps = shell.page > 0
    TitleBar(
        title = if (firstRun) "Welcome to Cabin" else "Setup",
        hint = if (onApps) "The apps Cabin opens. Tap one to change it." else "Tap each one to allow it. Each can be left off.",
        action = "Done",
        actionDescription = "Finish setup",
        onAction = {
            if (firstRun) {
                container.scope.launch { container.settings.update { it.copy(setupDone = true) } }
                shellModel.home()
            } else {
                shellModel.open(Screen.Settings)
            }
        },
        // Setup has two pages; a swipe turns them as well.
        pageIcon = if (onApps) SlateIcons.ChevronLeft else SlateIcons.ChevronRight,
        pageDescription = if (onApps) "Previous setup page" else "Next setup page",
        onPage = { shellModel.setupPage(if (onApps) 0 else 1) },
    )
}

/**
 * Setup, in two pages: first everything Cabin asks of the unit, each with its state, then the apps it
 * opens for the things it does not do itself.
 */
@Composable
private fun SetupContent(shell: Shell, container: AppContainer, shellModel: ShellViewModel, host: HostActions) {
    if (shell.page > 0) SetupApps(container, shellModel) else SetupPermissions(container, host)
}

@Composable
private fun SetupPermissions(container: AppContainer, host: HostActions) {
    val isHome by container.homeRole.held.collectAsStateWithLifecycle()
    val notifications by container.media.access.collectAsStateWithLifecycle()
    val canSetBrightness by container.brightness.canWrite.collectAsStateWithLifecycle()
    val granted by container.permissions.granted.collectAsStateWithLifecycle()
    val headlights by container.headlights.on.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val follow = settings.followHeadlights

    fun state(on: Boolean) = if (on) "On" else "Off"
    fun grant(title: String, subtitle: String, grant: Grant) = SettingsTile(
        title, subtitle, "Allow ${title.lowercase()}",
        value = state(grant in granted), on = grant in granted, onClick = { host.requestGrant(grant) },
    )

    SettingsGrid(
        listOf(
            SettingsTile(
                "Home app", "Opens Cabin from the Home button", "Make Cabin the home app",
                value = state(isHome), on = isHome, onClick = host.requestHomeRole,
            ),
            SettingsTile(
                "Notification access", "Now playing, navigation, tray", "Allow notification access",
                value = state(notifications), on = notifications, onClick = host.requestNotificationAccess,
            ),
            SettingsTile(
                "Brightness control", "Lets Cabin dim the screen", "Allow changing system settings",
                value = state(canSetBrightness), on = canSetBrightness, onClick = host.requestWriteSettings,
            ),
            // Whether Auto goes by the headlights. It only shows as on once the unit really reports them.
            SettingsTile(
                "Headlights",
                when {
                    !follow -> "Auto goes by the sun"
                    headlights == null -> "No signal from the unit yet"
                    else -> "Auto dims with them"
                },
                "Dim with the headlights",
                value = state(follow), on = follow && headlights != null,
                onClick = {
                    container.scope.launch { container.settings.update { it.copy(followHeadlights = !it.followHeadlights) } }
                },
            ),
            grant("Location", "Sunrise and sunset for Auto", Grant.Location),
            grant("Microphone", "Say a destination", Grant.Microphone),
            grant("Nearby devices", "Shows a connected phone", Grant.NearbyDevices),
        ),
    )
}

@Composable
private fun SetupApps(container: AppContainer, shellModel: ShellViewModel) {
    val sources by container.sources.state.collectAsStateWithLifecycle()
    fun app(title: String, slot: Slot, none: String = "Not chosen") = SettingsTile(
        title, sources.bindings[slot]?.label ?: none, "Choose the ${title.lowercase()}",
    ) { shellModel.pickApp(slot) }

    SettingsGrid(
        listOf(
            app("Phone app", Slot.Phone),
            app("Phone link app", Slot.Link),
            app("Maps app", Slot.Maps),
            // With none chosen, play wakes whichever app played last.
            app("Music app", Slot.Music, none = "Last one that played"),
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
        onBrightnessDown = dashboard::brightnessDown,
        onBrightnessMode = dashboard::cycleBrightnessMode,
        onBrightnessUp = dashboard::brightnessUp,
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
        onVolumeUp = dashboard::volumeUp,
        onMute = dashboard::toggleMute,
        onVolumeDown = dashboard::volumeDown,
    )
}
