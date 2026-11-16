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

/**
 * The source row, and the apps behind it, behind the rail's Phone and Link buttons, behind the
 * Navigation panel and behind the play button.
 */
class SourceController(
    private val settings: SettingsStore,
    private val apps: AppsSource,
    private val media: MediaSource,
    private val scope: CoroutineScope,
    private val namedAfterApps: Boolean = true,
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

    init {
        // The media source needs to know which app was chosen for music: it decides what is on show
        // while nothing is playing.
        scope.launch {
            state.map { it.bindings[Slot.Music]?.let { app -> SourceRules.packageOf(app.id) } }
                .distinctUntilChanged()
                .collect { media.setMusicApp(it) }
        }
        // Which app played last is kept across restarts: see [CabinSettings.lastPlayer].
        scope.launch {
            playingPackage.collect { playing ->
                if (playing != null && playing != settings.settings.value.lastPlayer) {
                    settings.update { it.copy(lastPlayer = playing) }
                }
            }
        }
    }

    private fun build(settings: CabinSettings, installed: List<AppEntry>, playing: String?): State {
        val bindings = SourceRules.bindings(settings, installed)
        val visible = SourceRules.visible(settings)
        val active = SourceRules.active(playing, bindings, settings.activeSource, visible)
        fun label(id: SourceId) = if (namedAfterApps) SourceRules.label(id, settings, bindings) else id.label
        return State(
            row = visible.map { SourceUi(it, active = it == active, label = label(it)) },
            tiles = settings.sourceOrder.map { id ->
                SourceTile(id, hidden = id in settings.sourceHidden, app = bindings[Slot.of(id)]?.label, label = label(id))
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

    /** The source after the active one in the row, wrapping round: what the wheel's Mode key selects. */
    fun nextSource(): SourceId? {
        val row = state.value.row
        return SourceRules.next(row.map { it.id }, row.firstOrNull { it.active }?.id)
    }

    /** The wheel's Mode key. */
    fun selectNext(): Opened = nextSource()?.let(::select) ?: Opened.NeedsApp

    /**
     * Play or pause, from the screen or the wheel. With nothing to resume and a music app chosen, that
     * app is started; if it cannot be started from outside, it is opened instead.
     */
    fun playPause() {
        if (media.playPause()) return
        val app = state.value.bindings[Slot.Music] ?: return
        val packageName = SourceRules.packageOf(app.id)
        media.start(packageName, wasLastPlayer = settings.settings.value.lastPlayer == packageName) {
            apps.launch(app.id)
        }
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
