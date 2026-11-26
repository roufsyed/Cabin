package com.roufsyed.cabin.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.BarSide
import com.roufsyed.cabin.model.BrightnessMode
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.model.HomePanel
import com.roufsyed.cabin.model.LayoutRules
import com.roufsyed.cabin.model.ScreenOffShows
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.ThemeId
import com.roufsyed.cabin.model.TuckAfter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What one finger swiping left or right on the Home screen does. */
enum class SideSwipe(val label: String) {
    /** Left opens the Apps grid, as the design has it. */
    Apps("Apps"),

    /** Left is the next track, right the previous one. */
    Tracks("Tracks"),
    Off("Off"),
}

/** What two fingers dragged up or down change. */
enum class TwoFingerDrag(val label: String) { Volume("Volume"), Brightness("Brightness"), Off("Off") }

/** The option after this one, wrapping round: what a tap on its settings tile selects. */
inline fun <reified E : Enum<E>> E.next(): E {
    val all = enumValues<E>()
    return all[(ordinal + 1) % all.size]
}

/** Everything Cabin remembers between runs. */
data class CabinSettings(
    /** False until the first read from disk has finished. */
    val loaded: Boolean = false,
    val appOrder: List<String> = emptyList(),
    val appHidden: Set<String> = emptySet(),
    /** The app list as last seen, so the grid can be drawn before the package manager is asked. */
    val appCache: List<AppEntry> = emptyList(),
    val sourceOrder: List<SourceId> = SourceId.entries,
    val sourceHidden: Set<SourceId> = setOf(SourceId.Aux),
    /** Which installed app each slot opens, by component id. */
    val bindings: Map<Slot, String> = emptyMap(),
    val activeSource: SourceId? = null,
    /**
     * The package of the app Cabin last saw playing. Android sends a media key to the app that played
     * last, so this says whether a key would reach the app chosen for music.
     */
    val lastPlayer: String? = null,
    val brightnessMode: BrightnessMode = BrightnessMode.Auto,
    val dayLevel: Int = 8,
    val nightLevel: Int = 3,
    /**
     * Whether Auto goes by the car's headlights when the unit reports them. Without that signal, and
     * when this is off, Auto goes by sunrise and sunset.
     */
    val followHeadlights: Boolean = true,
    val nightHours: Boolean = false,
    val nightStart: Int = 19 * 60,
    val dayStart: Int = 6 * 60,
    val setupDone: Boolean = false,
    /** One finger swiped down opens the notification tray. */
    val swipeDownTray: Boolean = true,
    val sideSwipe: SideSwipe = SideSwipe.Apps,
    val twoFingerDrag: TwoFingerDrag = TwoFingerDrag.Volume,
    /** Holding an app in the grid starts choosing a second one to show beside it. */
    val longPressSplit: Boolean = true,
    /** The colour theme. */
    val theme: ThemeId = ThemeId.Blue,
    val animations: Boolean = false,
    val dayColours: Boolean = true,
    /** Which lines the screen-off screen shows. */
    val screenOff: ScreenOffShows = ScreenOffShows(),
    /** The rail stays on screen, at half its width, while another app is open. */
    val sideBar: Boolean = true,
    val sideBarGranted: Boolean = false,
    /** That bar has a grip that tucks it away, leaving a handle at the screen's edge to bring it back. */
    val barHandle: Boolean = true,
    /** How soon that bar tucks itself away when it is left untouched. */
    val barTuck: TuckAfter = TuckAfter.FiveSeconds,
    /** Which edge of the screen the side bar is on, in Cabin and over other apps. */
    val barSide: BarSide = BarSide.Right,
    /** The Home screen's three panels, from left to right. */
    val homeOrder: List<HomePanel> = LayoutRules.DESIGN_ORDER,
    /** Last known position, kept so sunrise and sunset can be worked out without waiting for a fix. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val recentPlaces: List<String> = emptyList(),
)

interface SettingsStore {
    val settings: StateFlow<CabinSettings>
    suspend fun update(transform: (CabinSettings) -> CabinSettings)
}

/** Settings kept only in memory: for tests and for the design-comparison demo. */
class InMemorySettings(initial: CabinSettings = CabinSettings(loaded = true)) : SettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<CabinSettings> = state
    override suspend fun update(transform: (CabinSettings) -> CabinSettings) = state.update(transform)
}

/** Settings persisted with DataStore. Reads and writes happen on the store's own IO scope. */
class DataStoreSettings(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope,
) : SettingsStore {

    override val settings: StateFlow<CabinSettings> = store.data
        .catch { emit(emptyPreferences()) }
        .map(SettingsCodec::decode)
        .stateIn(scope, SharingStarted.Eagerly, CabinSettings())

    override suspend fun update(transform: (CabinSettings) -> CabinSettings) {
        store.edit { prefs -> SettingsCodec.encode(transform(SettingsCodec.decode(prefs)), prefs) }
    }
}

/** Maps [CabinSettings] to and from flat preferences. Unknown or damaged values fall back to defaults. */
object SettingsCodec {
    private val appOrder = stringPreferencesKey("app_order")
    private val appHidden = stringSetPreferencesKey("app_hidden")
    private val appCache = stringPreferencesKey("app_cache")
    private val sourceOrder = stringPreferencesKey("source_order")
    private val sourceHidden = stringSetPreferencesKey("source_hidden")
    private val activeSource = stringPreferencesKey("active_source")
    private val lastPlayer = stringPreferencesKey("last_player")
    private val brightnessMode = stringPreferencesKey("brightness_mode")
    private val dayLevel = intPreferencesKey("brightness_day")
    private val nightLevel = intPreferencesKey("brightness_night")
    private val followHeadlights = booleanPreferencesKey("brightness_headlights")
    private val nightHours = booleanPreferencesKey("night_hours")
    private val nightStart = intPreferencesKey("night_start")
    private val dayStart = intPreferencesKey("day_start")
    private val setupDone = booleanPreferencesKey("setup_done")
    private val swipeDownTray = booleanPreferencesKey("gesture_down")
    private val sideSwipe = stringPreferencesKey("gesture_side")
    private val twoFingerDrag = stringPreferencesKey("gesture_two_finger")
    private val longPressSplit = booleanPreferencesKey("gesture_long_press")
    private val theme = stringPreferencesKey("theme")
    private val animations = booleanPreferencesKey("animations")
    private val dayColours = booleanPreferencesKey("day_colours")
    private val sideBar = booleanPreferencesKey("side_bar")
    private val sideBarGranted = booleanPreferencesKey("side_bar_granted")
    private val barHandle = booleanPreferencesKey("side_bar_handle")
    private val barTuck = stringPreferencesKey("side_bar_tuck")
    private val barSide = stringPreferencesKey("side_bar_side")
    private val homeOrder = stringPreferencesKey("home_order")
    private val offClock = booleanPreferencesKey("off_clock")
    private val offDate = booleanPreferencesKey("off_date")
    private val offPlaying = booleanPreferencesKey("off_playing")
    private val offTurn = booleanPreferencesKey("off_turn")
    private val offStatus = booleanPreferencesKey("off_status")

    /** Written by earlier builds, when sideways swipes had a single fixed meaning. No longer read. */
    private val retiredSwipe = stringPreferencesKey("swipe_action")
    private val position = stringPreferencesKey("position")
    private val recentPlaces = stringPreferencesKey("recent_places")
    private fun binding(slot: Slot) = stringPreferencesKey("bind_${slot.name.lowercase()}")

    fun decode(prefs: Preferences): CabinSettings {
        val defaults = CabinSettings()
        val savedSources = prefs[sourceOrder]?.split(',')?.mapNotNull { enumOrNull<SourceId>(it) }.orEmpty()
        val coordinates = prefs[position]?.split(',')?.mapNotNull { it.toDoubleOrNull() }
        return CabinSettings(
            loaded = true,
            appOrder = prefs[appOrder]?.lines()?.filter { it.isNotEmpty() }.orEmpty(),
            appHidden = prefs[appHidden].orEmpty(),
            appCache = prefs[appCache]?.lines()?.mapNotNull(::decodeApp).orEmpty(),
            // Every source is always present; ones missing from a saved order are added at the end.
            sourceOrder = Arrange.ordered(savedSources, SourceId.entries),
            sourceHidden = prefs[sourceHidden]?.mapNotNull { enumOrNull<SourceId>(it) }?.toSet()
                ?: defaults.sourceHidden,
            bindings = Slot.entries.mapNotNull { slot -> prefs[binding(slot)]?.let { slot to it } }.toMap(),
            activeSource = prefs[activeSource]?.let { enumOrNull<SourceId>(it) },
            lastPlayer = prefs[lastPlayer]?.takeIf { it.isNotEmpty() },
            brightnessMode = prefs[brightnessMode]?.let { enumOrNull<BrightnessMode>(it) } ?: defaults.brightnessMode,
            dayLevel = (prefs[dayLevel] ?: defaults.dayLevel).coerceIn(1, 10),
            nightLevel = (prefs[nightLevel] ?: defaults.nightLevel).coerceIn(1, 10),
            followHeadlights = prefs[followHeadlights] ?: defaults.followHeadlights,
            nightHours = prefs[nightHours] ?: defaults.nightHours,
            nightStart = (prefs[nightStart] ?: defaults.nightStart).takeIf { it in 0 until 24 * 60 } ?: defaults.nightStart,
            dayStart = (prefs[dayStart] ?: defaults.dayStart).takeIf { it in 0 until 24 * 60 } ?: defaults.dayStart,
            setupDone = prefs[setupDone] ?: false,
            swipeDownTray = prefs[swipeDownTray] ?: defaults.swipeDownTray,
            sideSwipe = prefs[sideSwipe]?.let { enumOrNull<SideSwipe>(it) } ?: defaults.sideSwipe,
            twoFingerDrag = prefs[twoFingerDrag]?.let { enumOrNull<TwoFingerDrag>(it) } ?: defaults.twoFingerDrag,
            longPressSplit = prefs[longPressSplit] ?: defaults.longPressSplit,
            theme = prefs[theme]?.let { enumOrNull<ThemeId>(it) } ?: defaults.theme,
            animations = prefs[animations] ?: defaults.animations,
            dayColours = prefs[dayColours] ?: defaults.dayColours,
            screenOff = ScreenOffShows(
                clock = prefs[offClock] ?: defaults.screenOff.clock,
                date = prefs[offDate] ?: defaults.screenOff.date,
                playing = prefs[offPlaying] ?: defaults.screenOff.playing,
                turn = prefs[offTurn] ?: defaults.screenOff.turn,
                status = prefs[offStatus] ?: defaults.screenOff.status,
            ),
            sideBar = prefs[sideBar] ?: defaults.sideBar,
            sideBarGranted = prefs[sideBarGranted] ?: defaults.sideBarGranted,
            barHandle = prefs[barHandle] ?: defaults.barHandle,
            barTuck = prefs[barTuck]?.let { enumOrNull<TuckAfter>(it) } ?: defaults.barTuck,
            barSide = prefs[barSide]?.let { enumOrNull<BarSide>(it) } ?: defaults.barSide,
            homeOrder = LayoutRules.valid(
                prefs[homeOrder]?.split(',')?.mapNotNull { enumOrNull<HomePanel>(it) } ?: defaults.homeOrder,
            ),
            latitude = coordinates?.getOrNull(0)?.takeIf { coordinates.size == 2 },
            longitude = coordinates?.getOrNull(1)?.takeIf { coordinates.size == 2 },
            recentPlaces = prefs[recentPlaces]?.lines()?.filter { it.isNotEmpty() }.orEmpty(),
        )
    }

    fun encode(settings: CabinSettings, prefs: MutablePreferences) {
        prefs[appOrder] = settings.appOrder.joinToString("\n")
        prefs[appHidden] = settings.appHidden
        prefs[appCache] = settings.appCache.joinToString("\n", transform = ::encodeApp)
        prefs[sourceOrder] = settings.sourceOrder.joinToString(",") { it.name }
        prefs[sourceHidden] = settings.sourceHidden.map { it.name }.toSet()
        for (slot in Slot.entries) {
            val id = settings.bindings[slot]
            if (id == null) prefs.remove(binding(slot)) else prefs[binding(slot)] = id
        }
        if (settings.activeSource == null) prefs.remove(activeSource) else prefs[activeSource] = settings.activeSource.name
        if (settings.lastPlayer == null) prefs.remove(lastPlayer) else prefs[lastPlayer] = settings.lastPlayer
        prefs[brightnessMode] = settings.brightnessMode.name
        prefs[dayLevel] = settings.dayLevel
        prefs[nightLevel] = settings.nightLevel
        prefs[followHeadlights] = settings.followHeadlights
        prefs[nightHours] = settings.nightHours
        prefs[nightStart] = settings.nightStart
        prefs[dayStart] = settings.dayStart
        prefs[setupDone] = settings.setupDone
        prefs[swipeDownTray] = settings.swipeDownTray
        prefs[sideSwipe] = settings.sideSwipe.name
        prefs[twoFingerDrag] = settings.twoFingerDrag.name
        prefs[longPressSplit] = settings.longPressSplit
        prefs[theme] = settings.theme.name
        prefs[animations] = settings.animations
        prefs[dayColours] = settings.dayColours
        prefs[offClock] = settings.screenOff.clock
        prefs[offDate] = settings.screenOff.date
        prefs[offPlaying] = settings.screenOff.playing
        prefs[offTurn] = settings.screenOff.turn
        prefs[offStatus] = settings.screenOff.status
        prefs[sideBar] = settings.sideBar
        prefs[sideBarGranted] = settings.sideBarGranted
        prefs[barHandle] = settings.barHandle
        prefs[barTuck] = settings.barTuck.name
        prefs[barSide] = settings.barSide.name
        prefs[homeOrder] = settings.homeOrder.joinToString(",") { it.name }
        prefs.remove(retiredSwipe)
        if (settings.latitude != null && settings.longitude != null) {
            prefs[position] = "${settings.latitude},${settings.longitude}"
        } else {
            prefs.remove(position)
        }
        prefs[recentPlaces] = settings.recentPlaces.joinToString("\n")
    }

    private fun encodeApp(app: AppEntry): String =
        "${app.id}\t${app.glyph.name}\t${app.label.replace('\t', ' ').replace('\n', ' ')}"

    private fun decodeApp(line: String): AppEntry? {
        val parts = line.split('\t', limit = 3)
        if (parts.size != 3 || parts[0].isEmpty()) return null
        return AppEntry(parts[0], parts[2], enumOrNull<Glyph>(parts[1]) ?: Glyph.Letter)
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
