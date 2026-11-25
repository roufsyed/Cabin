package com.roufsyed.cabin.ui.apps

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roufsyed.cabin.apps.AppsSource
import com.roufsyed.cabin.apps.SplitPair
import com.roufsyed.cabin.apps.SplitWay
import com.roufsyed.cabin.data.Arrange
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.SettingsStore
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.AppTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val APPS_PER_PAGE = 15

/** The grid's contents: [all] in the user's order, hidden ones flagged; [visible] without the hidden ones. */
@Immutable
data class AppsUi(val all: List<AppTile> = emptyList()) {
    val visible: List<AppTile> = all.filter { !it.hidden }

    /** Tiles for [page] of the grid, or of the arranging view when [editing]. */
    fun page(page: Int, editing: Boolean): List<AppTile> =
        (if (editing) all else visible).drop(page * APPS_PER_PAGE).take(APPS_PER_PAGE)

    fun pageCount(editing: Boolean): Int = pagesFor((if (editing) all else visible).size)

    fun pageOf(id: String): Int = all.indexOfFirst { it.app.id == id }.coerceAtLeast(0) / APPS_PER_PAGE

    /** The apps that can be shown beside [firstId] in split screen: the grid without it and without Cabin. */
    fun beside(firstId: String, ownId: String): AppsUi =
        AppsUi(visible.filter { it.app.id != firstId && it.app.id != ownId })

    companion object {
        fun pagesFor(count: Int): Int = if (count == 0) 1 else (count + APPS_PER_PAGE - 1) / APPS_PER_PAGE
    }
}

class AppsViewModel(
    private val apps: AppsSource,
    private val settings: SettingsStore,
) : ViewModel() {

    val ownId: String get() = apps.ownId

    val ui: StateFlow<AppsUi> = combine(apps.installed, settings.settings, ::build)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(apps.installed.value, settings.settings.value))

    private fun build(installed: List<AppEntry>, settings: CabinSettings): AppsUi {
        val byId = installed.associateBy { it.id }
        val order = Arrange.ordered(settings.appOrder, installed.map { it.id })
        return AppsUi(order.map { id -> AppTile(byId.getValue(id), hidden = id in settings.appHidden) })
    }

    /**
     * Whether holding an app leads to choosing a second one for split screen: the unit must be able
     * to split, and the long press must be left on in Settings > Gestures. Only this one setting is
     * watched here, so the grid is not redrawn when any other one changes.
     */
    val holdToSplit: StateFlow<Boolean> = settings.settings
        .map { apps.split != SplitWay.None && it.longPressSplit }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, apps.split != SplitWay.None && settings.settings.value.longPressSplit)

    fun launch(id: String): Boolean = apps.launch(id)

    fun launchPair(leftId: String, rightId: String, open: (SplitPair) -> Boolean): Boolean =
        apps.launchPair(leftId, rightId, open)

    /** Moves a tile one place earlier (-1) or later (+1). */
    fun move(id: String, delta: Int) {
        val current = ui.value.all.map { it.app.id }
        viewModelScope.launch {
            settings.update { it.copy(appOrder = Arrange.move(current, id, delta)) }
        }
    }

    /** Hides or shows a tile. Cabin's own tile cannot be hidden: it is the way into its settings. */
    fun toggleHidden(id: String) {
        if (id == apps.ownId) return
        viewModelScope.launch {
            settings.update { it.copy(appHidden = Arrange.toggle(it.appHidden, id)) }
        }
    }
}
