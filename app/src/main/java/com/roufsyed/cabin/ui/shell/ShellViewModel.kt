package com.roufsyed.cabin.ui.shell

import androidx.lifecycle.ViewModel
import com.roufsyed.cabin.model.Slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class Screen {
    Home, Apps, Settings, Keys, Preferences, Theme, Night,

    /** The page that turns the screen off and chooses what the dark screen shows. */
    ScreenOff,
    Sources, Pick, Split, Setup,

    /** What each of the unit's players tells Cabin, a player to a page. Reached from Setup. */
    Players,
    ThisUnit,
}

/** Setup has more tiles than fit at once: what Cabin may do on the first page, the apps it opens on the second. */
const val SETUP_PAGES = 2

/** So has "Theme and gestures": the theme and what the gestures do, then where things are on screen. */
const val PREFERENCES_PAGES = 2

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
    /** On the Pick screen: the slot an app is being chosen for. */
    val pickSlot: Slot? = null,
    /** On the Pick and Split screens: where to go back to. */
    val pickFrom: Screen = Screen.Home,
    val pickFromPage: Int = 0,
    /**
     * On the Split screen: the app for the left, for which a neighbour is being chosen. It is the app
     * that was held in the grid, or, when the chooser was opened from the tray, null until the first
     * of the two taps has chosen it.
     */
    val splitFirst: String? = null,
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

    /** Turns to a page of "Theme and gestures". */
    fun preferencesPage(page: Int) = _state.update {
        if (it.screen == Screen.Preferences) it.copy(page = page.coerceIn(0, PREFERENCES_PAGES - 1)) else it
    }

    /** Turns to a page of Players. How many there are is the caller's to know: one for each player running. */
    fun playersPage(page: Int) = _state.update {
        if (it.screen == Screen.Players) it.copy(page = page.coerceAtLeast(0)) else it
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
     * Starts choosing the app to show beside [appId] in split screen. Only from the Apps grid itself:
     * not while arranging, and not under an overlay.
     */
    fun splitWith(appId: String) = _state.update {
        if (it.screen == Screen.Apps && it.edit == null && it.overlay == Overlay.None) {
            Shell(screen = Screen.Split, splitFirst = appId, pickFrom = Screen.Apps, pickFromPage = it.page)
        } else {
            it
        }
    }

    /**
     * Starts choosing both apps of a split screen, the left one first: the tray's Split screen
     * button. It closes the tray, and leaving the chooser leads back to the screen the tray was over.
     */
    fun split() = _state.update {
        // There is no way back into the middle of arranging or of another chooser, so from those
        // the chooser is left for Home.
        val plain = it.edit == null && it.screen != Screen.Pick && it.screen != Screen.Split
        Shell(
            screen = Screen.Split,
            pickFrom = if (plain) it.screen else Screen.Home,
            pickFromPage = if (plain) it.page else 0,
        )
    }

    /** In the chooser opened by [split]: [appId] is the app for the left, and the right one comes next. */
    fun splitLeft(appId: String) = _state.update {
        if (it.screen == Screen.Split && it.splitFirst == null) it.copy(splitFirst = appId, page = 0) else it
    }

    /** Leaves the split-screen chooser for the screen, and the page, it was opened from. */
    fun splitDone() = _state.update { if (it.screen == Screen.Split) afterSplit(it) else it }

    private fun afterSplit(shell: Shell): Shell = Shell(screen = shell.pickFrom, page = shell.pickFromPage)

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
            it.edit != null || it.screen == Screen.Pick || it.screen == Screen.Split || it.screen == Screen.Setup -> it
            else -> Shell()
        }
    }

    private val Screen.paged: Boolean
        get() = this == Screen.Apps || this == Screen.Pick || this == Screen.Split || this == Screen.Setup ||
            this == Screen.Preferences || this == Screen.Players

    fun show(overlay: Overlay) = _state.update { it.copy(overlay = overlay) }

    fun closeOverlay() = _state.update { it.copy(overlay = Overlay.None) }

    /** One step back: close an overlay, finish arranging, leave a sub-screen. Home has nothing behind it. */
    fun back() = _state.update {
        when {
            it.overlay != Overlay.None -> it.copy(overlay = Overlay.None)
            it.screen == Screen.Pick -> afterPick(it)
            it.screen == Screen.Split -> afterSplit(it)
            it.edit == EditKind.Sources -> Shell(screen = Screen.Settings)
            it.edit == EditKind.Apps -> Shell(screen = Screen.Apps, page = it.page)
            // The second page of Setup, or of Theme and gestures, leads back to its first.
            (it.screen == Screen.Setup || it.screen == Screen.Preferences) && it.page > 0 -> it.copy(page = it.page - 1)
            // The theme chooser and the night mode page are reached from the "Theme and gestures" screen.
            it.screen == Screen.Theme || it.screen == Screen.Night -> Shell(screen = Screen.Preferences)
            // Players is reached from Setup's second page, whichever of its own pages is showing.
            it.screen == Screen.Players || it.screen == Screen.ThisUnit -> Shell(screen = Screen.Setup, page = SETUP_PAGES - 1)
            it.screen == Screen.Keys || it.screen == Screen.Preferences || it.screen == Screen.ScreenOff ||
                it.screen == Screen.Sources || it.screen == Screen.Setup -> Shell(screen = Screen.Settings)
            it.screen == Screen.Settings -> Shell(screen = Screen.Apps)
            else -> Shell()
        }
    }
}
