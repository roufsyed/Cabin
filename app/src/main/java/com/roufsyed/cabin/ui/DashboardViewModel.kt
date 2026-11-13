package com.roufsyed.cabin.ui

import androidx.lifecycle.ViewModel
import com.roufsyed.cabin.AppContainer
import com.roufsyed.cabin.model.BrightnessUi
import com.roufsyed.cabin.model.ClockUi
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.model.NowPlayingUi
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.model.SourceUi
import com.roufsyed.cabin.model.StatusUi
import com.roufsyed.cabin.model.VolumeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Home panels, the strip, the rail and the bottom row show. Each value is its own flow, so a
 * change to one (a clock tick, a track change) redraws only the part of the screen that shows it.
 */
class DashboardViewModel(container: AppContainer) : ViewModel() {
    val clock: StateFlow<ClockUi> = container.clock.clock

    // Not wired to the unit yet: these still show the design's values.
    val status: StateFlow<StatusUi> = MutableStateFlow(Samples.status)
    val nav: StateFlow<NavUi> = MutableStateFlow(Samples.nav)
    val nowPlaying: StateFlow<NowPlayingUi> = MutableStateFlow(Samples.nowPlaying)
    val volume: StateFlow<VolumeUi> = MutableStateFlow(Samples.volume)
    val brightness: StateFlow<BrightnessUi> = MutableStateFlow(Samples.brightness)
    val sources: StateFlow<List<SourceUi>> = MutableStateFlow(Samples.sources)
}
