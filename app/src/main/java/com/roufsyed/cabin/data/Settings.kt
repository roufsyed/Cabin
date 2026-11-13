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
import com.roufsyed.cabin.model.BrightnessMode
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What one finger swiping left or right does. Only one action exists so far; the setting is the hook for more. */
enum class SwipeAction { Pages }

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
    val brightnessMode: BrightnessMode = BrightnessMode.Auto,
    val dayLevel: Int = 8,
    val nightLevel: Int = 3,
    val setupDone: Boolean = false,
    val swipe: SwipeAction = SwipeAction.Pages,
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
    private val brightnessMode = stringPreferencesKey("brightness_mode")
    private val dayLevel = intPreferencesKey("brightness_day")
    private val nightLevel = intPreferencesKey("brightness_night")
    private val setupDone = booleanPreferencesKey("setup_done")
    private val swipe = stringPreferencesKey("swipe_action")
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
            brightnessMode = prefs[brightnessMode]?.let { enumOrNull<BrightnessMode>(it) } ?: defaults.brightnessMode,
            dayLevel = (prefs[dayLevel] ?: defaults.dayLevel).coerceIn(1, 10),
            nightLevel = (prefs[nightLevel] ?: defaults.nightLevel).coerceIn(1, 10),
            setupDone = prefs[setupDone] ?: false,
            swipe = prefs[swipe]?.let { enumOrNull<SwipeAction>(it) } ?: defaults.swipe,
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
        prefs[brightnessMode] = settings.brightnessMode.name
        prefs[dayLevel] = settings.dayLevel
        prefs[nightLevel] = settings.nightLevel
        prefs[setupDone] = settings.setupDone
        prefs[swipe] = settings.swipe.name
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
