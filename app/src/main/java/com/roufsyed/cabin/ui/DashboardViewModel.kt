package com.roufsyed.cabin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roufsyed.cabin.AppContainer
import com.roufsyed.cabin.model.BrightnessUi
import com.roufsyed.cabin.model.ClockUi
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.model.NowPlayingUi
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.SourceUi
import com.roufsyed.cabin.model.StatusUi
import com.roufsyed.cabin.model.VolumeUi
import com.roufsyed.cabin.model.nowPlayingUi
import com.roufsyed.cabin.nav.SavedPlace
import com.roufsyed.cabin.sources.Opened
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the Home panels, the strip, the rail and the bottom row show. Each value is its own flow, so a
 * change to one (a clock tick, a track change) redraws only the part of the screen that shows it.
 */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    val clock: StateFlow<ClockUi> = container.clock.clock

    val nowPlaying: StateFlow<NowPlayingUi> =
        combine(container.media.nowPlaying, container.media.access, ::nowPlayingUi).stateIn(
            viewModelScope, SharingStarted.Eagerly,
            nowPlayingUi(container.media.nowPlaying.value, container.media.access.value),
        )

    val sources: StateFlow<List<SourceUi>> = container.sources.state.map { it.row }
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.sources.state.value.row)

    val volume: StateFlow<VolumeUi> = container.volume.volume
    val brightness: StateFlow<BrightnessUi> = container.brightness.state

    val nav: StateFlow<NavUi> = container.navigation.state

    val status: StateFlow<StatusUi> = container.status.status

    /** Play or pause; with nothing to resume, the music app chosen in Setup is started. */
    fun playPause() = container.sources.playPause()
    fun next() = container.media.next()
    fun previous() = container.media.previous()
    fun openPlayer(): Opened = container.sources.openPlayer()

    fun volumeUp() = container.volume.up()
    fun volumeDown() = container.volume.down()
    fun toggleMute() = container.volume.toggleMute()
    fun setVolume(level: Int) = container.volume.set(level)

    fun brightnessUp() = container.brightness.up()
    fun brightnessDown() = container.brightness.down()
    fun stepBrightness(delta: Int) = container.brightness.step(delta)
    fun cycleBrightnessMode() = container.brightness.cycleMode()

    fun openMaps() = container.navigation.openMaps()
    fun navigateTo(place: SavedPlace) = container.navigation.navigateTo(place)

    fun selectSource(source: SourceId): Opened = container.sources.select(source)
    fun open(slot: Slot): Opened = container.sources.open(slot)
}
