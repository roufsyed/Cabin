package com.roufsyed.cabin.ui.shell

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class Screen { Home, Apps, Music, Settings, Keys, Sources }

enum class EditKind { Apps, Sources }

enum class Overlay { None, Tray, Voice, ScreenOff }

/** Where the user is in Cabin. */
data class Shell(
    val screen: Screen = Screen.Home,
    /** Which page of the Apps grid. */
    val page: Int = 0,
    /** Set while arranging apps or sources. */
    val edit: EditKind? = null,
    /** The tile tapped while arranging. */
    val picked: String? = null,
    val overlay: Overlay = Overlay.None,
)

/** Screen-to-screen movement. It holds no data of its own, only where the user is. */
class ShellViewModel : ViewModel() {
    private val _state = MutableStateFlow(Shell())
    val state: StateFlow<Shell> = _state

    /** Home, with every overlay and edit mode closed. Also what the unit's Home button does. */
    fun home() = _state.update { Shell() }

    fun open(screen: Screen) = _state.update { Shell(screen = screen) }

    fun appsPage(page: Int) = _state.update { it.copy(screen = Screen.Apps, page = page.coerceAtLeast(0)) }

    fun arrangeApps() = _state.update { Shell(screen = Screen.Apps, edit = EditKind.Apps) }

    fun arrangeSources() = _state.update { Shell(screen = Screen.Sources, edit = EditKind.Sources) }

    fun pick(id: String) = _state.update { if (it.edit == null) it else it.copy(picked = id) }

    /** Leaves arranging: apps go back to the grid, sources back to Settings. */
    fun done() = _state.update {
        when (it.edit) {
            EditKind.Sources -> Shell(screen = Screen.Settings)
            EditKind.Apps -> Shell(screen = Screen.Apps, page = it.page)
            null -> it
        }
    }

    fun show(overlay: Overlay) = _state.update { it.copy(overlay = overlay) }

    fun closeOverlay() = _state.update { it.copy(overlay = Overlay.None) }

    /** One step back: close an overlay, finish arranging, leave a sub-screen. Home has nothing behind it. */
    fun back() = _state.update {
        when {
            it.overlay != Overlay.None -> it.copy(overlay = Overlay.None)
            it.edit == EditKind.Sources -> Shell(screen = Screen.Settings)
            it.edit == EditKind.Apps -> Shell(screen = Screen.Apps, page = it.page)
            it.screen == Screen.Keys || it.screen == Screen.Sources -> Shell(screen = Screen.Settings)
            it.screen == Screen.Settings -> Shell(screen = Screen.Apps)
            else -> Shell()
        }
    }
}
