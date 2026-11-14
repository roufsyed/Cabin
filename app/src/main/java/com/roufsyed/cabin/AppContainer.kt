package com.roufsyed.cabin

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.roufsyed.cabin.apps.AndroidAppsSource
import com.roufsyed.cabin.apps.AppsSource
import com.roufsyed.cabin.apps.FixedApps
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
import com.roufsyed.cabin.system.AndroidBrightness
import com.roufsyed.cabin.system.AndroidClock
import com.roufsyed.cabin.system.AndroidHomeRole
import com.roufsyed.cabin.system.AndroidPermissions
import com.roufsyed.cabin.system.AndroidStatus
import com.roufsyed.cabin.system.AndroidVolume
import com.roufsyed.cabin.system.BrightnessControl
import com.roufsyed.cabin.system.ClockSource
import com.roufsyed.cabin.system.FixedBrightness
import com.roufsyed.cabin.system.FixedClock
import com.roufsyed.cabin.system.FixedHeadlights
import com.roufsyed.cabin.system.FixedHomeRole
import com.roufsyed.cabin.system.FixedPermissions
import com.roufsyed.cabin.system.FixedStatus
import com.roufsyed.cabin.system.FixedVolume
import com.roufsyed.cabin.system.HeadlightSignal
import com.roufsyed.cabin.system.HomeRole
import com.roufsyed.cabin.system.NoHeadlightSignal
import com.roufsyed.cabin.system.PermissionsSource
import com.roufsyed.cabin.system.StatusSource
import com.roufsyed.cabin.system.VolumeControl
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    /** The car's headlights, where the unit reports them. Auto brightness follows it. */
    val headlights: HeadlightSignal = NoHeadlightSignal,
    val listenerHub: ListenerHub = ListenerHub(),
) {
    val sources = SourceController(settings, apps, media, scope)

    /** The same set with some parts swapped, for tests that need a particular unit. */
    fun copy(
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
        headlights: HeadlightSignal = this.headlights,
    ): AppContainer = AppContainer(
        scope, settings, apps, clock, homeRole, media, volume, brightness, navigation, voice, tray, status,
        permissions, headlights, listenerHub,
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
            )
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
                ),
            )
            // The design is drawn with the headlights off; its Settings has a switch to try them on.
            val headlights = FixedHeadlights(false)
            return AppContainer(
                scope = scope,
                settings = settings,
                apps = FixedApps(),
                clock = FixedClock(Samples.clock),
                homeRole = FixedHomeRole(),
                media = FixedMedia(Samples.playing),
                volume = FixedVolume(),
                // On its own unconfined scope, so a tap on the stepper has changed the level by the time
                // the tap returns, as it has on the unit's screen a frame later.
                brightness = FixedBrightness(settings, headlights = headlights),
                navigation = FixedNavigation(Samples.nav),
                voice = FixedVoice(),
                tray = FixedTray(Samples.notifications),
                status = FixedStatus(Samples.status),
                permissions = FixedPermissions(),
                headlights = headlights,
            )
        }
    }
}
