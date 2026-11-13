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
import com.roufsyed.cabin.notify.ListenerClient
import com.roufsyed.cabin.notify.ListenerHub
import com.roufsyed.cabin.sources.SourceController
import com.roufsyed.cabin.system.AndroidClock
import com.roufsyed.cabin.system.AndroidHomeRole
import com.roufsyed.cabin.system.ClockSource
import com.roufsyed.cabin.system.FixedClock
import com.roufsyed.cabin.system.FixedHomeRole
import com.roufsyed.cabin.system.HomeRole
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
    val listenerHub: ListenerHub = ListenerHub(),
) {
    val sources = SourceController(settings, apps, media, scope)

    companion object {
        fun live(app: Application): AppContainer {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { app.preferencesDataStoreFile("cabin") },
            )
            val settings = DataStoreSettings(store, scope)
            val media = AndroidMediaSource(app, scope)
            return AppContainer(
                scope = scope,
                settings = settings,
                apps = AndroidAppsSource(app, settings, scope),
                clock = AndroidClock(app),
                homeRole = AndroidHomeRole(app),
                media = media,
                listenerHub = ListenerHub(
                    listOf(
                        object : ListenerClient {
                            override fun onConnected(service: NotificationListenerService) = media.connect()
                            override fun onDisconnected() = media.disconnect()
                        },
                    ),
                ),
            )
        }

        /** The design's own values, so a screenshot can be laid over the design. */
        fun design(): AppContainer = AppContainer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            settings = InMemorySettings(
                CabinSettings(
                    loaded = true,
                    // The design shows the phone's Bluetooth audio playing; its sample track "plays" in Music.
                    activeSource = SourceId.Bluetooth,
                    bindings = mapOf(Slot.Bluetooth to FixedApps.id("Music")),
                ),
            ),
            apps = FixedApps(),
            clock = FixedClock(Samples.clock),
            homeRole = FixedHomeRole(),
            media = FixedMedia(Samples.playing),
        )
    }
}
