package com.roufsyed.cabin

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.roufsyed.cabin.apps.AndroidAppsSource
import com.roufsyed.cabin.apps.AppsSource
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.bar.AndroidSideBarAccess
import com.roufsyed.cabin.bar.FixedSideBarAccess
import com.roufsyed.cabin.bar.SideBarAccess
import com.roufsyed.cabin.bar.SideBarRules
import com.roufsyed.cabin.bar.SideBarState
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.DataStoreSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.data.SettingsStore
import com.roufsyed.cabin.media.AndroidMediaSource
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.media.MediaSource
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.nav.AndroidVoiceInput
import com.roufsyed.cabin.nav.FixedNavigation
import com.roufsyed.cabin.nav.FixedVoice
import com.roufsyed.cabin.nav.MapsNavigation
import com.roufsyed.cabin.nav.NavigationSource
import com.roufsyed.cabin.nav.VoiceInput
import com.roufsyed.cabin.notify.AndroidTray
import com.roufsyed.cabin.notify.FixedTray
import com.roufsyed.cabin.notify.ListenerClient
import com.roufsyed.cabin.notify.ListenerHub
import com.roufsyed.cabin.notify.TraySource
import com.roufsyed.cabin.sources.SourceController
import com.roufsyed.cabin.ui.theme.Palette
import com.roufsyed.cabin.ui.theme.Themes
import com.roufsyed.cabin.system.AndroidBrightness
import com.roufsyed.cabin.system.AndroidClock
import com.roufsyed.cabin.system.AndroidHomeRole
import com.roufsyed.cabin.system.AndroidMemory
import com.roufsyed.cabin.system.AndroidPermissions
import com.roufsyed.cabin.system.AndroidStatus
import com.roufsyed.cabin.system.AndroidSwitches
import com.roufsyed.cabin.system.AndroidUnit
import com.roufsyed.cabin.system.AndroidVolume
import com.roufsyed.cabin.system.BrightnessControl
import com.roufsyed.cabin.system.ClockSource
import com.roufsyed.cabin.system.FixedBrightness
import com.roufsyed.cabin.system.FixedClock
import com.roufsyed.cabin.system.FixedHeadlights
import com.roufsyed.cabin.system.FixedHomeRole
import com.roufsyed.cabin.system.FixedMemory
import com.roufsyed.cabin.system.FixedPermissions
import com.roufsyed.cabin.system.FixedStatus
import com.roufsyed.cabin.system.FixedSwitches
import com.roufsyed.cabin.system.FixedUnit
import com.roufsyed.cabin.system.FixedVolume
import com.roufsyed.cabin.system.HeadlightSignal
import com.roufsyed.cabin.system.HomeRole
import com.roufsyed.cabin.system.MemoryControl
import com.roufsyed.cabin.system.NoHeadlightSignal
import com.roufsyed.cabin.system.PermissionsSource
import com.roufsyed.cabin.system.StatusSource
import com.roufsyed.cabin.system.UnitSource
import com.roufsyed.cabin.system.UnitSwitches
import com.roufsyed.cabin.system.VolumeControl
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything the screens draw from, behind small interfaces. There is one live set, built from the
 * system, and one fixed set holding the design's own values, used by the demo launch and by tests.
 * No injection framework: these few objects are created by hand, once.
 */
class AppContainer(
    val scope: CoroutineScope,
    val settings: SettingsStore,
    val apps: AppsSource,
    val clock: ClockSource,
    val homeRole: HomeRole,
    val media: MediaSource,
    val volume: VolumeControl,
    val brightness: BrightnessControl,
    val navigation: NavigationSource,
    val voice: VoiceInput,
    val tray: TraySource,
    val status: StatusSource,
    val permissions: PermissionsSource,
    /** The unit's Bluetooth and Wi-Fi switches, for the tray. */
    val switches: UnitSwitches,
    /** Closes what other apps have left running, for the tray's Clear memory. */
    val memory: MemoryControl,
    /** The car's headlights, where the unit reports them. Auto brightness follows it. */
    val headlights: HeadlightSignal = NoHeadlightSignal,
    val listenerHub: ListenerHub = ListenerHub(),
    /** What the bar that stays over other apps goes by. */
    val sideBar: SideBarState = SideBarState(),
    private val sourcesNamedAfterApps: Boolean = true,
    private val sideBarAccess: SideBarAccess = FixedSideBarAccess(),
    val unit: UnitSource = FixedUnit(Samples.unit),
) {
    val sources = SourceController(settings, apps, media, scope, sourcesNamedAfterApps)

    val keysSeen = MutableStateFlow<List<Int>>(emptyList())

    val palette: Flow<Palette> = combine(
        settings.settings.map { it.theme to it.dayColours },
        brightness.state.map { it.night },
    ) { (theme, dayColours), night -> Themes.palette(theme, day = dayColours && !night) }.distinctUntilChanged()

    fun keepSideBar() {
        scope.launch {
            val saved = settings.settings.first { it.loaded }
            sideBar.lost.value = SideBarRules.recover(saved.sideBar, saved.sideBarGranted, sideBarAccess)
        }
    }

    /** The same set with some parts swapped, for tests that need a particular unit. */
    fun copy(
        clock: ClockSource = this.clock,
        settings: SettingsStore = this.settings,
        apps: AppsSource = this.apps,
        media: MediaSource = this.media,
        volume: VolumeControl = this.volume,
        brightness: BrightnessControl = this.brightness,
        navigation: NavigationSource = this.navigation,
        voice: VoiceInput = this.voice,
        tray: TraySource = this.tray,
        status: StatusSource = this.status,
        homeRole: HomeRole = this.homeRole,
        permissions: PermissionsSource = this.permissions,
        switches: UnitSwitches = this.switches,
        memory: MemoryControl = this.memory,
        headlights: HeadlightSignal = this.headlights,
        sourcesNamedAfterApps: Boolean = this.sourcesNamedAfterApps,
        sideBarAccess: SideBarAccess = this.sideBarAccess,
        unit: UnitSource = this.unit,
    ): AppContainer = AppContainer(
        scope, settings, apps, clock, homeRole, media, volume, brightness, navigation, voice, tray, status,
        permissions, switches, memory, headlights, listenerHub, sideBar, sourcesNamedAfterApps, sideBarAccess, unit,
    )

    companion object {
        fun live(app: Application): AppContainer {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { app.preferencesDataStoreFile("cabin") },
            )
            val settings = DataStoreSettings(store, scope)
            val media = AndroidMediaSource(app, scope)
            val clock = AndroidClock(app)
            val apps = AndroidAppsSource(app, settings, scope)
            val navigation = MapsNavigation(app, scope, apps, settings)
            val tray = AndroidTray(app)
            // How this unit tells apps about its headlights is not known yet, so nothing is read:
            // Auto goes by sunrise and sunset until a reader for the real signal takes this place.
            val headlights = NoHeadlightSignal
            val sideBar = SideBarState()
            return AppContainer(
                scope = scope,
                settings = settings,
                apps = apps,
                clock = clock,
                homeRole = AndroidHomeRole(app),
                media = media,
                volume = AndroidVolume(app),
                brightness = AndroidBrightness(app, settings, scope, clock, headlights),
                navigation = navigation,
                voice = AndroidVoiceInput(app),
                tray = tray,
                status = AndroidStatus(app),
                permissions = AndroidPermissions(app),
                switches = AndroidSwitches(app),
                memory = AndroidMemory(app, apps),
                headlights = headlights,
                listenerHub = ListenerHub(
                    listOf(
                        navigation,
                        tray,
                        object : ListenerClient {
                            override fun onConnected(service: NotificationListenerService) = media.connect()
                            override fun onDisconnected() = media.disconnect()
                        },
                    ),
                ),
                sideBar = sideBar,
                sideBarAccess = AndroidSideBarAccess(app),
                unit = AndroidUnit(app, apps.split, sideBar),
            ).also { it.keepSideBar() }
        }

        /** The design's own values, so a screenshot can be laid over the design. */
        fun design(): AppContainer {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val settings = InMemorySettings(
                CabinSettings(
                    loaded = true,
                    setupDone = true,
                    // The design shows the phone's Bluetooth audio playing; its sample track "plays" in Music.
                    activeSource = SourceId.Bluetooth,
                    bindings = mapOf(Slot.Bluetooth to FixedApps.id("Music")),
                    recentPlaces = listOf("Airport", "City Mall"),
                    animations = false,
                    dayColours = false,
                ),
            )
            // The design is drawn with the headlights off; its Settings has a switch to try them on.
            val headlights = FixedHeadlights(false)
            val status = FixedStatus(Samples.status)
            return AppContainer(
                scope = scope,
                settings = settings,
                apps = FixedApps(),
                clock = FixedClock(Samples.clock),
                homeRole = FixedHomeRole(),
                media = FixedMedia(Samples.playing, tracks = Samples.tracks),
                volume = FixedVolume(),
                // On its own unconfined scope, so a tap on the stepper has changed the level by the time
                // the tap returns, as it has on the unit's screen a frame later.
                brightness = FixedBrightness(settings, headlights = headlights),
                navigation = FixedNavigation(Samples.nav),
                voice = FixedVoice(),
                tray = FixedTray(Samples.notifications),
                status = status,
                permissions = FixedPermissions(),
                switches = FixedSwitches(status),
                memory = FixedMemory(),
                headlights = headlights,
                sourcesNamedAfterApps = false,
            )
        }
    }
}
