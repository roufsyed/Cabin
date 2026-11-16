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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import com.roufsyed.cabin.gesture.GestureLayer
import com.roufsyed.cabin.media.MediaSource
import com.roufsyed.cabin.media.PlayerReport
import com.roufsyed.cabin.media.PlayerRules
import com.roufsyed.cabin.model.AppTile
import com.roufsyed.cabin.model.BarSide
import com.roufsyed.cabin.model.HomePanel
import com.roufsyed.cabin.model.LayoutRules
import com.roufsyed.cabin.model.ScreenOffShows
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.ThemeId
import com.roufsyed.cabin.model.TuckAfter
import com.roufsyed.cabin.model.screenOffUi
import com.roufsyed.cabin.nav.SavedPlace
import com.roufsyed.cabin.sources.Opened
import com.roufsyed.cabin.system.BrightnessRules
import com.roufsyed.cabin.system.Grant
import com.roufsyed.cabin.system.MemoryRules
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
import com.roufsyed.cabin.ui.icons.SlateIcon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.music.MusicScreen
import com.roufsyed.cabin.ui.overlay.BrightnessHud
import com.roufsyed.cabin.ui.overlay.ControlLook
import com.roufsyed.cabin.ui.overlay.ScreenOffOverlay
import com.roufsyed.cabin.ui.overlay.TrayControl
import com.roufsyed.cabin.ui.overlay.TrayOverlay
import com.roufsyed.cabin.ui.overlay.VoiceOverlay
import com.roufsyed.cabin.ui.overlay.VolumeHud
import com.roufsyed.cabin.ui.settings.KeysScreen
import com.roufsyed.cabin.ui.settings.PlayerPage
import com.roufsyed.cabin.ui.settings.SettingsGrid
import com.roufsyed.cabin.ui.settings.SettingsTile
import com.roufsyed.cabin.ui.settings.ThemeGrid
import com.roufsyed.cabin.ui.settings.WheelActions
import com.roufsyed.cabin.ui.shell.EditKind
import com.roufsyed.cabin.ui.shell.Overlay
import com.roufsyed.cabin.ui.shell.PREFERENCES_PAGES
import com.roufsyed.cabin.ui.shell.SETUP_PAGES
import com.roufsyed.cabin.ui.shell.Screen
import com.roufsyed.cabin.ui.shell.Shell
import com.roufsyed.cabin.ui.shell.ShellViewModel
import com.roufsyed.cabin.ui.sources.SourcesGrid
import com.roufsyed.cabin.ui.theme.DesignSurface
import kotlinx.coroutines.delay
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
    /** Asks for one of them if it has not been granted, then reports whether it is held. */
    val withGrant: (Grant, (Boolean) -> Unit) -> Unit = { _, answer -> answer(true) },
    val openBluetooth: () -> Unit = {},
    val openWifi: () -> Unit = {},
    /**
     * Has Android itself ask whether Bluetooth may be switched on or off, for a unit on which Cabin
     * may not switch it. Where the unit has no such question its Bluetooth settings open instead.
     */
    val askBluetooth: (on: Boolean) -> Unit = {},
    /** Opens the unit's hotspot settings: no app may switch a hotspot. */
    val openHotspot: () -> Unit = {},
    /** Opens the unit's own settings. */
    val openUnitSettings: () -> Unit = {},
    /** Opens two apps side by side. The activity has to do it itself: see `AppsSource.launchPair`. */
    val openPair: (SplitPair) -> Boolean = { false },
    /** Opens the unit's Accessibility settings, where the bar over other apps is allowed. */
    val openAccessibility: () -> Unit = {},
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
                !onHome || chosen.sideSwipe == SideSwipe.Apps ->
                    shellModel.swipeLeft(pageCount(shell, appsModel, container.media))
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
    // otherwise every start would show a frame of the default theme, order and levels first. Where
    // the rail and the Home panels go is read in the same piece, so they are right from that frame.
    val placing by remember(container) {
        container.settings.settings.map { Placing(it.loaded, it.barSide, it.homeOrder) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = Placing(loaded = false))

    DesignSurface {
        if (placing.loaded) GestureLayer(onGesture) {
            CabinFrame(
                railOnLeft = placing.barSide == BarSide.Left,
                content = {
                    if (shell.screen == Screen.Home) {
                        HomeContent(placing.homeOrder, dashboard, shellModel, host, whereTo, openMaps, navigateTo)
                    } else {
                        Column {
                            when {
                                shell.edit == EditKind.Apps -> AppsEditBar(shell, appsModel, shellModel)
                                shell.edit == EditKind.Sources -> SourcesEditBar(shell, container, shellModel)
                                shell.screen == Screen.Pick -> SlotPickBar(shell, container, shellModel)
                                shell.screen == Screen.Split -> SplitBar(shell, appsModel, shellModel)
                                shell.screen == Screen.Setup -> SetupBar(shell, container, shellModel)
                                shell.screen == Screen.Preferences -> PreferencesBar(shell, shellModel)
                                shell.screen == Screen.Theme -> ThemeBar(shellModel)
                                shell.screen == Screen.ScreenOff -> ScreenOffBar(shellModel)
                                shell.screen == Screen.Players -> PlayersBar(shell, container, shellModel)
                                else -> Strip(dashboard, shellModel, host, showMini = shell.screen != Screen.Music)
                            }
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.size(1036.dp, 488.dp)) {
                                when (shell.screen) {
                                    Screen.Apps -> AppsContent(shell, appsModel, shellModel)
                                    Screen.Music -> MusicContent(dashboard)
                                    Screen.Settings -> SettingsContent(container, shellModel, host)
                                    Screen.Keys -> KeysScreen(wheel, onBack = { shellModel.open(Screen.Settings) })
                                    Screen.Preferences -> PreferencesContent(shell, container, shellModel, host)
                                    Screen.Theme -> ThemeContent(container)
                                    Screen.ScreenOff -> ScreenOffOptions(container, shellModel)
                                    Screen.Setup -> SetupContent(shell, container, shellModel, host)
                                    Screen.Players -> PlayersContent(shell, container)
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
                rail = { RailContent(container, dashboard, shell.screen == Screen.Home, shellModel, openSlot) },
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
            if (shell.overlay == Overlay.ScreenOff) ScreenOffLayer(container, dashboard, shellModel)
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
        controls = trayControls(container, shellModel, host),
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

/**
 * The tray's controls, in the order they are drawn: the four switches, then what is done in one tap.
 *
 * A switch leaves the tray open, so that what it did can be seen; anything that leads somewhere else
 * closes it first. What each does on a unit that will not let Cabin do it outright:
 *
 * - Bluetooth and Wi-Fi are switched in place where Android allows. Where it does not, Bluetooth
 *   becomes Android's own question, "turn Bluetooth off?", and Wi-Fi the unit's Wi-Fi panel.
 * - No app may switch a hotspot, so that button opens the unit's hotspot settings.
 * - Recent apps, Screenshot and Restart are asked of Android by the side bar's service, the only
 *   kind of app that may. Until Cabin has been allowed as one they are dimmed and lead to where
 *   that is done. Restart opens Android's power menu: an app cannot restart the unit itself.
 * - Split screen is dimmed, and no button, where the unit has no split screen. So is Clear memory
 *   where the unit's Android no longer lets an app close what others have left running.
 */
@Composable
private fun trayControls(container: AppContainer, shellModel: ShellViewModel, host: HostActions): List<TrayControl> {
    val status by container.status.status.collectAsStateWithLifecycle()
    val bluetooth by container.status.bluetoothOn.collectAsStateWithLifecycle()
    val hotspot by container.status.hotspotOn.collectAsStateWithLifecycle()
    val brightness by container.brightness.state.collectAsStateWithLifecycle()
    val serviceOn by container.sideBar.allowed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // What Clear memory left free, shown in place of its name for a few seconds afterwards, and
    // whether it is at work or still showing that.
    var freed by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }

    fun switch(name: String, on: Boolean?, icon: SlateIcon, onClick: () -> Unit) = TrayControl(
        label = name,
        description = if (on == null) name else "$name: ${if (on) "on" else "off"}",
        icon = icon,
        look = when (on) {
            true -> ControlLook.On
            false -> ControlLook.Off
            null -> ControlLook.Plain
        },
        onClick = onClick,
    )

    // Something only the side bar's service can ask Android for.
    fun served(name: String, description: String, icon: SlateIcon, ask: () -> Unit) = TrayControl(
        label = name,
        description = if (serviceOn) description else "$name: allow Cabin under Accessibility first",
        icon = icon,
        look = if (serviceOn) ControlLook.Plain else ControlLook.Unavailable,
        onClick = {
            shellModel.closeOverlay()
            if (serviceOn) ask() else host.openAccessibility()
        },
    )

    return listOf(
        switch("Bluetooth", bluetooth, SlateIcons.Bluetooth) {
            val on = !bluetooth
            // Android 12 and later want the "nearby devices" permission for any of this. Refused,
            // there is still the unit's own Bluetooth page.
            host.withGrant(Grant.NearbyDevices) { granted ->
                when {
                    !granted -> host.openBluetooth()
                    !container.switches.setBluetooth(on) -> host.askBluetooth(on)
                }
            }
        },
        switch("Wi-Fi", status.wifi, SlateIcons.StatusWifi) {
            if (!container.switches.setWifi(!status.wifi)) host.openWifi()
        },
        switch("Hotspot", hotspot, SlateIcons.Hotspot, host.openHotspot),
        switch("Night mode", brightness.night, SlateIcons.Moon, container.brightness::toggleNight),
        served("Recent apps", "Recent apps", SlateIcons.Recents) { container.sideBar.showRecents?.invoke() },
        if (container.apps.splitScreen) {
            TrayControl("Split screen", "Split screen: choose two apps", SlateIcons.Split, onClick = shellModel::split)
        } else {
            TrayControl(
                "Split screen", "Split screen: the unit has none", SlateIcons.Split, ControlLook.Unavailable,
                onClick = null,
            )
        },
        served("Screenshot", "Take a screenshot", SlateIcons.Screenshot) { container.sideBar.takeScreenshot?.invoke() },
        TrayControl("Unit settings", "Unit settings", SlateIcons.Settings) {
            shellModel.closeOverlay()
            host.openUnitSettings()
        },
        if (container.memory.allowed) {
            TrayControl(freed ?: "Clear memory", "Clear memory", SlateIcons.Memory) {
                // One at a time: a tap while the last result is still showing changes nothing.
                if (!clearing) {
                    clearing = true
                    scope.launch {
                        try {
                            // Whatever is on show in Now playing is kept, paused or not: closing it
                            // would lose its place.
                            val playing = container.media.nowPlaying.value?.packageName
                            freed = MemoryRules.label(container.memory.clear(setOfNotNull(playing)))
                            delay(FREED_SHOWN_MS)
                        } finally {
                            freed = null
                            clearing = false
                        }
                    }
                }
            }
        } else {
            TrayControl(
                "Clear memory", "Clear memory: this Android does not let an app do it", SlateIcons.Memory,
                ControlLook.Unavailable, onClick = null,
            )
        },
        served("Restart", "Restart: opens the power menu", SlateIcons.Power) { container.sideBar.showPowerMenu?.invoke() },
    )
}

/** How long Clear memory shows what it left free. */
private const val FREED_SHOWN_MS = 4_000L

/**
 * The dark screen, with as much on it as Settings > Screen off asks for. What it draws from is
 * collected only while it is showing.
 */
@Composable
private fun ScreenOffLayer(container: AppContainer, dashboard: DashboardViewModel, shellModel: ShellViewModel) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val clock by dashboard.clock.collectAsStateWithLifecycle()
    val status by dashboard.status.collectAsStateWithLifecycle()
    val nav by dashboard.nav.collectAsStateWithLifecycle()
    val nowPlaying by dashboard.nowPlaying.collectAsStateWithLifecycle()
    val brightness by dashboard.brightness.collectAsStateWithLifecycle()
    ScreenOffOverlay(
        ui = screenOffUi(settings.screenOff, clock, status, nav, nowPlaying, brightness.night),
        onWake = shellModel::closeOverlay,
    )
}

/** Where the rail and the Home panels go, with whether the saved settings have been read yet. */
private data class Placing(
    val loaded: Boolean,
    val barSide: BarSide = BarSide.Right,
    val homeOrder: List<HomePanel> = LayoutRules.DESIGN_ORDER,
)

/** How many pages the grid on screen has, so a swipe left knows whether there is a next one. */
private fun pageCount(shell: Shell, apps: AppsViewModel, media: MediaSource): Int {
    val ui = apps.ui.value
    return when {
        shell.screen == Screen.Setup -> SETUP_PAGES
        shell.screen == Screen.Preferences -> PREFERENCES_PAGES
        // A page for each player that is running.
        shell.screen == Screen.Players -> media.players().size
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
    order: List<HomePanel>,
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
        order = order,
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
        onSeek = dashboard::seekTo,
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
        hint = when {
            // Opened from the tray, with both apps still to choose.
            shell.splitFirst == null -> "Tap the app for the left."
            first == null -> "Tap the app for the right."
            else -> "${first.label} on the left. Tap the app for the right."
        },
        action = "Back",
        actionDescription = "Cancel split screen",
        onAction = shellModel::splitDone,
    )
}

/**
 * The chooser for a split: the grid again, without Cabin and without the app already chosen for the
 * left. Reached by holding an app, that app is the left one and a tap chooses the right. Reached
 * from the tray, the first tap chooses the left and the second the right.
 */
@Composable
private fun SplitContent(shell: Shell, apps: AppsViewModel, shellModel: ShellViewModel, host: HostActions) {
    val first = shell.splitFirst
    val ui by apps.ui.collectAsStateWithLifecycle()
    val choices = ui.beside(first.orEmpty(), apps.ownId)
    val page = shell.page.coerceIn(0, choices.pageCount(editing = false) - 1)
    AppsGrid(
        tiles = choices.page(page, editing = false),
        picked = null,
        onTap = { app ->
            if (first == null) {
                shellModel.splitLeft(app.id)
            } else {
                apps.launchPair(first, app.id, host.openPair)
                shellModel.splitDone()
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
            picked.label != picked.id.label -> "${picked.id.label}: opens ${picked.app}"
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
            // Turning the screen off is one tap further in, on a page with what the dark screen shows.
            // The tray's Screen off button still does it in one.
            SettingsTile("Screen off", "Turn off, or set what it shows", "Screen off options") {
                shellModel.open(Screen.ScreenOff)
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
            SettingsTile("Theme and gestures", "Colours, swipes and layout", "Theme and gestures") {
                shellModel.open(Screen.Preferences)
            },
            SettingsTile("Setup", "Permissions and apps", "Open setup") { shellModel.open(Screen.Setup) },
        ),
    )
}

@Composable
private fun PreferencesBar(shell: Shell, shellModel: ShellViewModel) {
    val onLayout = shell.page > 0
    TitleBar(
        title = if (onLayout) "Layout" else "Theme and gestures",
        hint = if (onLayout) "Where things are. Tap one to change it." else "Tap one to change it",
        action = "Done",
        actionDescription = "Back to settings",
        onAction = { shellModel.open(Screen.Settings) },
        // The screen has two pages; a swipe turns them as well.
        pageIcon = if (onLayout) SlateIcons.ChevronLeft else SlateIcons.ChevronRight,
        pageDescription = if (onLayout) "Theme and gestures page" else "Layout page",
        onPage = { shellModel.preferencesPage(if (onLayout) 0 else 1) },
    )
}

/**
 * Theme and gestures, in two pages. First the colour theme and what each of Cabin's finger movements
 * does: the theme tile leads to the chooser, and a tap on a gesture's tile moves it to its next
 * option. Then Layout: the side bar, and where things are on screen.
 */
@Composable
private fun PreferencesContent(shell: Shell, container: AppContainer, shellModel: ShellViewModel, host: HostActions) {
    if (shell.page > 0) LayoutTiles(container, host) else ThemeAndGestureTiles(container, shellModel)
}

@Composable
private fun ThemeAndGestureTiles(container: AppContainer, shellModel: ShellViewModel) {
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

/**
 * Layout: the side bar's switches, how soon it tucks itself away and which edge it is on, then the
 * place of each of Home's three panels, then the tile that puts every place back as the design has
 * it. A tap on a panel's tile moves that panel one place to the right, and from the right-hand place
 * round to the left. How soon the bar tucks away is one of four, so its tile drops a list instead.
 */
@Composable
private fun LayoutTiles(container: AppContainer, host: HostActions) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val barAllowed by container.sideBar.allowed.collectAsStateWithLifecycle()
    fun change(transform: (CabinSettings) -> CabinSettings) {
        container.scope.launch { container.settings.update(transform) }
    }
    fun panel(panel: HomePanel) = SettingsTile(
        panel.label, "Its place on Home", "${panel.label} on Home: left, middle or right",
        value = LayoutRules.place(settings.homeOrder, panel), on = true,
        onClick = { change { it.copy(homeOrder = LayoutRules.moved(it.homeOrder, panel)) } },
    )
    SettingsGrid(
        listOf(
            // The same switch as on Setup's first page, here with the other things about the bar.
            sideBarTile(container, host, settings.sideBar, barAllowed, "Side bar over other apps: on or off"),
            // The grip on that bar that tucks it away, and the handle that brings it back.
            SettingsTile(
                "Side bar handle", "Tucks the bar away and back", "Side bar handle: on or off",
                value = if (settings.barHandle) "On" else "Off", on = settings.barHandle,
                onClick = { change { it.copy(barHandle = !it.barHandle) } },
            ),
            // How soon that bar tucks itself away. It takes the handle, which is what brings it back.
            SettingsTile(
                "Tuck away",
                if (settings.barHandle) settings.barTuck.hint else "Needs the side bar handle",
                "Side bar tucks away: immediate, 5 sec, 10 sec or never",
                value = if (settings.barHandle) settings.barTuck.label else "Off",
                on = settings.barHandle && settings.barTuck != TuckAfter.Never,
                options = if (settings.barHandle) TuckAfter.entries.map { it.label } else emptyList(),
                chosen = settings.barTuck.ordinal,
                onPick = { option -> change { it.copy(barTuck = TuckAfter.entries[option]) } },
            ),
            // Cabin's own rail and the bar over other apps go to the same edge.
            SettingsTile(
                "Side bar position", "The edge of the screen it is on", "Side bar position: right or left",
                value = settings.barSide.label, on = true,
                onClick = { change { it.copy(barSide = it.barSide.next()) } },
            ),
            panel(HomePanel.Clock),
            panel(HomePanel.Navigation),
            panel(HomePanel.NowPlaying),
            SettingsTile("Reset positions", "Side bar right, panels as designed", "Reset positions") {
                change { it.copy(barSide = BarSide.Right, homeOrder = LayoutRules.DESIGN_ORDER) }
            },
        ),
    )
}

/**
 * The switch for the rail kept on screen over other apps. Android only lets an accessibility service
 * do that, so until Cabin has been switched on as one, a tap leads to where that is done. After that
 * the tile is the bar's own switch. It is on Setup's first page, among what Cabin asks of the unit,
 * and on the Layout page of Theme and gestures, with the bar's other settings.
 */
private fun sideBarTile(
    container: AppContainer,
    host: HostActions,
    wanted: Boolean,
    allowed: Boolean,
    description: String,
) = SettingsTile(
    "Side bar in apps",
    if (allowed) "The rail stays beside every app" else "Allow Cabin under Accessibility",
    description,
    value = if (allowed && wanted) "On" else "Off", on = allowed && wanted,
    onClick = {
        if (allowed) {
            container.scope.launch { container.settings.update { it.copy(sideBar = !it.sideBar) } }
        } else {
            container.scope.launch { container.settings.update { it.copy(sideBar = true) } }
            host.openAccessibility()
        }
    },
)

@Composable
private fun ScreenOffBar(shellModel: ShellViewModel) {
    TitleBar(
        title = "Screen off",
        hint = "Choose what the dark screen shows",
        action = "Done",
        actionDescription = "Back to settings",
        onAction = { shellModel.open(Screen.Settings) },
    )
}

/**
 * Screen off: the tile that turns the screen off, then a switch for each line the dark screen can
 * show, in the order the lines come on it. Turned off from here, the screen wakes back here, which is
 * how a choice is tried out.
 */
@Composable
private fun ScreenOffOptions(container: AppContainer, shellModel: ShellViewModel) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val shows = settings.screenOff
    fun line(title: String, subtitle: String, what: String, on: Boolean, flip: (ScreenOffShows) -> ScreenOffShows) =
        SettingsTile(
            title, subtitle, "Show $what: on or off",
            value = if (on) "On" else "Off", on = on,
            onClick = {
                container.scope.launch { container.settings.update { it.copy(screenOff = flip(it.screenOff)) } }
            },
        )
    SettingsGrid(
        listOf(
            SettingsTile(
                "Turn off now", if (shows.nothing) "Plain black, tap to wake" else "Tap the screen to wake it",
                "Turn the screen off",
            ) { shellModel.show(Overlay.ScreenOff) },
            line("Clock", "The time", "the clock", shows.clock) { it.copy(clock = !it.clock) },
            line("Date", "Day and date", "the date", shows.date) { it.copy(date = !it.date) },
            line("Status marks", "Phone, Wi-Fi and GPS", "the status marks", shows.status) { it.copy(status = !it.status) },
            // The turn only while a route is followed, the track only while it plays.
            line("Next turn", "While Maps is guiding", "the next turn", shows.turn) { it.copy(turn = !it.turn) },
            line("Now playing", "Title and artist", "what is playing", shows.playing) { it.copy(playing = !it.playing) },
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
    val barAllowed by container.sideBar.allowed.collectAsStateWithLifecycle()
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
            sideBarTile(container, host, settings.sideBar, barAllowed, "Keep the side bar over other apps"),
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
            // Not an app to choose: what the players that are running tell Cabin about themselves,
            // which is where a unit's own players are looked into.
            SettingsTile("Players", "What each one tells Cabin", "What the players tell Cabin") {
                shellModel.open(Screen.Players)
            },
        ),
    )
}

/** How often the Players page reads the players again: a player gives no sign when most of it changes. */
private const val PLAYERS_READ_MS = 1_000L

/** What each running player says of itself, kept fresh for as long as the page that shows it is up. */
@Composable
private fun rememberPlayers(media: MediaSource): List<PlayerReport> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val players by produceState(media.players().map(PlayerRules::report), media, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = media.players().map(PlayerRules::report)
                delay(PLAYERS_READ_MS)
            }
        }
    }
    return players
}

/** The page of Players on show: the one asked for, or the last there is if players have gone since. */
private fun playersPage(shell: Shell, players: List<PlayerReport>): Int =
    shell.page.coerceIn(0, (players.size - 1).coerceAtLeast(0))

@Composable
private fun PlayersBar(shell: Shell, container: AppContainer, shellModel: ShellViewModel) {
    val players = rememberPlayers(container.media)
    val page = playersPage(shell, players)
    val player = players.getOrNull(page)
    TitleBar(
        title = player?.appLabel ?: "Players",
        hint = when {
            player == null -> "What each player tells Cabin"
            player.shown -> "Player ${page + 1} of ${players.size}, the one Cabin shows"
            else -> "Player ${page + 1} of ${players.size}"
        },
        action = "Done",
        actionDescription = "Back to setup",
        onAction = shellModel::back,
        // A page to each player; a swipe turns them as well, and the button goes round.
        pageIcon = if (players.size > 1) SlateIcons.ChevronRight else null,
        pageDescription = "Next player",
        onPage = { shellModel.playersPage((page + 1) % players.size) },
    )
}

@Composable
private fun PlayersContent(shell: Shell, container: AppContainer) {
    val players = rememberPlayers(container.media)
    val access by container.media.access.collectAsStateWithLifecycle()
    PlayerPage(players.getOrNull(playersPage(shell, players)), access)
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
    container: AppContainer,
    dashboard: DashboardViewModel,
    onHome: Boolean,
    shell: ShellViewModel,
    onSlot: (Slot) -> Unit,
) {
    val volume by dashboard.volume.collectAsStateWithLifecycle()
    // Android's list of recent apps can only be asked for through the service that draws the side
    // bar. Until that is allowed, holding the button is the same as tapping it.
    val barAllowed by container.sideBar.allowed.collectAsStateWithLifecycle()
    Rail(
        onHome = onHome,
        volume = volume,
        onPhone = { onSlot(Slot.Phone) },
        onLink = { onSlot(Slot.Link) },
        onAppsOrHome = { if (onHome) shell.open(Screen.Apps) else shell.home() },
        onRecents = if (barAllowed) ({ container.sideBar.showRecents?.invoke() }) else null,
        onVolumeUp = dashboard::volumeUp,
        onMute = dashboard::toggleMute,
        onVolumeDown = dashboard::volumeDown,
    )
}
