package com.roufsyed.cabin.ui.shell

import androidx.lifecycle.ViewModel
import com.roufsyed.cabin.model.Slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class Screen { Home, Apps, Music, Settings, Keys, Gestures, Sources, Pick, Setup }

/** Setup has more tiles than fit at once: what Cabin may do on the first page, the apps it opens on the second. */
const val SETUP_PAGES = 2

enum class EditKind { Apps, Sources }

enum class Overlay { None, Tray, Voice, ScreenOff }

/** Where the user is in Cabin. */
data class Shell(
    val screen: Screen = Screen.Home,
    /** Which page of the Apps grid, of the app chooser or of Setup. */
    val page: Int = 0,
    /** Set while arranging apps or sources. */
    val edit: EditKind? = null,
    /** The tile tapped while arranging. */
    val picked: String? = null,
    val overlay: Overlay = Overlay.None,
    /** On the Pick screen: the slot an app is being chosen for, and where to go back to. */
    val pickSlot: Slot? = null,
    val pickFrom: Screen = Screen.Home,
    val pickFromPage: Int = 0,
)

/** Screen-to-screen movement. It holds no data of its own, only where the user is. */
class ShellViewModel : ViewModel() {
    private val _state = MutableStateFlow(Shell())
    val state: StateFlow<Shell> = _state

    private val _voiceRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fired when the wheel's voice key is pressed; the screen answers it the way "Where to?" does. */
    val voiceRequests: SharedFlow<Unit> = _voiceRequests

    fun requestVoice() {
        _voiceRequests.tryEmit(Unit)
    }

    /** Home, with every overlay and edit mode closed. Also what the unit's Home button does. */
    fun home() = _state.update { Shell() }

    fun open(screen: Screen) = _state.update { Shell(screen = screen) }

    fun appsPage(page: Int) = _state.update { it.copy(screen = Screen.Apps, page = page.coerceAtLeast(0)) }

    /** Turns to a page of Setup. */
    fun setupPage(page: Int) = _state.update {
        if (it.screen == Screen.Setup) it.copy(page = page.coerceIn(0, SETUP_PAGES - 1)) else it
    }

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

    /** Opens the app chooser for [slot], remembering where it was opened from. */
    fun pickApp(slot: Slot) = _state.update {
        Shell(screen = Screen.Pick, pickSlot = slot, pickFrom = it.screen, pickFromPage = it.page, picked = it.picked)
    }

    /**
     * Leaves the app chooser, back to the page it was opened from. From arranging sources it returns
     * there, with the same source still tapped.
     */
    fun pickDone() = _state.update { if (it.screen == Screen.Pick) afterPick(it) else it }

    private fun afterPick(shell: Shell): Shell = when (shell.pickFrom) {
        Screen.Sources -> Shell(screen = Screen.Sources, edit = EditKind.Sources, picked = shell.picked)
        // Only Setup has pages worth returning to; the Apps grid starts again from its first.
        Screen.Setup -> Shell(screen = Screen.Setup, page = shell.pickFromPage)
        else -> Shell(screen = shell.pickFrom)
    }

    /**
     * One finger swiped left: Home leads to the first page of apps, and a page of apps, of the app
     * chooser or of Setup to the next of [pages]. Nothing moves while an overlay is up.
     */
    fun swipeLeft(pages: Int) = _state.update {
        when {
            it.overlay != Overlay.None -> it
            it.screen == Screen.Home -> Shell(screen = Screen.Apps)
            it.screen.paged && it.page + 1 < pages -> it.copy(page = it.page + 1)
            else -> it
        }
    }

    /**
     * One finger swiped right: back a page, from the first page of apps to Home, and from any other
     * screen to Home. While arranging, choosing an app or in Setup it only pages, so a stray swipe
     * loses nothing.
     */
    fun swipeRight() = _state.update {
        when {
            it.overlay != Overlay.None || it.screen == Screen.Home -> it
            it.screen.paged && it.page > 0 -> it.copy(page = it.page - 1)
            it.edit != null || it.screen == Screen.Pick || it.screen == Screen.Setup -> it
            else -> Shell()
        }
    }

    private val Screen.paged: Boolean get() = this == Screen.Apps || this == Screen.Pick || this == Screen.Setup

    fun show(overlay: Overlay) = _state.update { it.copy(overlay = overlay) }

    fun closeOverlay() = _state.update { it.copy(overlay = Overlay.None) }

    /** One step back: close an overlay, finish arranging, leave a sub-screen. Home has nothing behind it. */
    fun back() = _state.update {
        when {
            it.overlay != Overlay.None -> it.copy(overlay = Overlay.None)
            it.screen == Screen.Pick -> afterPick(it)
            it.edit == EditKind.Sources -> Shell(screen = Screen.Settings)
            it.edit == EditKind.Apps -> Shell(screen = Screen.Apps, page = it.page)
            // The second page of Setup leads back to its first.
            it.screen == Screen.Setup && it.page > 0 -> it.copy(page = it.page - 1)
            it.screen == Screen.Keys || it.screen == Screen.Gestures || it.screen == Screen.Sources ||
                it.screen == Screen.Setup -> Shell(screen = Screen.Settings)
            it.screen == Screen.Settings -> Shell(screen = Screen.Apps)
            else -> Shell()
        }
    }
}
