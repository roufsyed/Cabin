package com.roufsyed.cabin.sources

import com.roufsyed.cabin.apps.AppsSource
import com.roufsyed.cabin.data.Arrange
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.SettingsStore
import com.roufsyed.cabin.media.MediaSource
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.SourceTile
import com.roufsyed.cabin.model.SourceUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What tapping a source or a rail button led to. */
enum class Opened {
    /** The source's app was already running a media session and was told to play; Cabin stays up. */
    Played,

    /** The app was brought to the front. */
    Launched,

    /** No app is set for this slot and none could be guessed: the user has to choose one. */
    NeedsApp,
}

/** The source row, the apps behind it and behind the rail's Phone and Link buttons. */
class SourceController(
    private val settings: SettingsStore,
    private val apps: AppsSource,
    private val media: MediaSource,
    private val scope: CoroutineScope,
) {
    data class State(
        /** The visible sources, in order, for the bottom row. */
        val row: List<SourceUi> = emptyList(),
        /** Every source, hidden ones included, for arranging. */
        val tiles: List<SourceTile> = emptyList(),
        val bindings: Map<Slot, AppEntry> = emptyMap(),
    )

    private val playingPackage = media.nowPlaying
        .map { now -> now?.packageName?.takeIf { now.playing } }
        .distinctUntilChanged()

    val state: StateFlow<State> = combine(settings.settings, apps.installed, playingPackage, ::build)
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope, SharingStarted.Eagerly,
            build(settings.settings.value, apps.installed.value, media.nowPlaying.value?.let { it.packageName.takeIf { _ -> it.playing } }),
        )

    private fun build(settings: CabinSettings, installed: List<AppEntry>, playing: String?): State {
        val bindings = SourceRules.bindings(settings, installed)
        val visible = SourceRules.visible(settings)
        val active = SourceRules.active(playing, bindings, settings.activeSource, visible)
        return State(
            row = visible.map { SourceUi(it, active = it == active) },
            tiles = settings.sourceOrder.map { id ->
                SourceTile(id, hidden = id in settings.sourceHidden, app = bindings[Slot.of(id)]?.label)
            },
            bindings = bindings,
        )
    }

    /**
     * Selects a source. If its app already has a media session it is simply told to play, so the driver
     * stays on the screen they were on; tapping the source that is already active opens its app.
     * Android Auto always opens, since it takes over the screen.
     */
    fun select(source: SourceId): Opened {
        val current = state.value
        val app = current.bindings[Slot.of(source)] ?: return Opened.NeedsApp
        val wasActive = current.row.any { it.id == source && it.active }
        scope.launch { settings.update { it.copy(activeSource = source) } }
        if (source != SourceId.Auto && !wasActive && media.play(SourceRules.packageOf(app.id))) return Opened.Played
        return if (apps.launch(app.id)) Opened.Launched else Opened.NeedsApp
    }

    /** The wheel's Mode key: the next source in the row. */
    fun selectNext(): Opened {
        val row = state.value.row
        val next = SourceRules.next(row.map { it.id }, row.firstOrNull { it.active }?.id) ?: return Opened.NeedsApp
        return select(next)
    }

    /** Opens the app behind a rail button. */
    fun open(slot: Slot): Opened {
        val app = state.value.bindings[slot] ?: return Opened.NeedsApp
        return if (apps.launch(app.id)) Opened.Launched else Opened.NeedsApp
    }

    fun move(source: SourceId, delta: Int) = update { it.copy(sourceOrder = Arrange.move(it.sourceOrder, source, delta)) }

    /** Hides or shows a source. The last visible one stays: the row is never empty. */
    fun toggleHidden(source: SourceId) = update {
        if (Arrange.canHide(it.sourceOrder, it.sourceHidden, source)) {
            it.copy(sourceHidden = Arrange.toggle(it.sourceHidden, source))
        } else {
            it
        }
    }

    /** Sets the app a slot opens. Null goes back to guessing from the installed apps' names. */
    fun bind(slot: Slot, appId: String?) = update {
        it.copy(bindings = if (appId == null) it.bindings - slot else it.bindings + (slot to appId))
    }

    private fun update(transform: (CabinSettings) -> CabinSettings) {
        scope.launch { settings.update(transform) }
    }
}
