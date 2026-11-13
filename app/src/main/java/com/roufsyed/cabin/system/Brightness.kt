package com.roufsyed.cabin.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.SettingsStore
import com.roufsyed.cabin.model.BrightnessMode
import com.roufsyed.cabin.model.BrightnessUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Screen brightness in ten steps, with one level kept for day and one for night. */
interface BrightnessControl {
    val state: StateFlow<BrightnessUi>

    /** Whether Cabin may change the unit's brightness setting. Without it only Cabin's own screens dim. */
    val canWrite: StateFlow<Boolean>

    /**
     * The brightness Cabin's own window should use, 0..1, or null to follow the system. Set only while
     * Cabin is not allowed to change the system setting, so the stepper still does something.
     */
    val windowBrightness: StateFlow<Float?>

    fun up()
    fun down()

    /** Auto, then Day, then Night, then Auto again. */
    fun cycleMode()

    /** Re-checks the permission and whether it is day or night. */
    fun refresh() = Unit
}

/** The rules, kept apart from Android so they can be tested. */
object BrightnessRules {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 10

    fun nextMode(mode: BrightnessMode): BrightnessMode = when (mode) {
        BrightnessMode.Auto -> BrightnessMode.Day
        BrightnessMode.Day -> BrightnessMode.Night
        BrightnessMode.Night -> BrightnessMode.Auto
    }

    /** Whether the night level applies: forced by Day or Night, otherwise by [sunIsDown]. */
    fun night(mode: BrightnessMode, sunIsDown: Boolean): Boolean = when (mode) {
        BrightnessMode.Auto -> sunIsDown
        BrightnessMode.Day -> false
        BrightnessMode.Night -> true
    }

    fun ui(settings: CabinSettings, sunIsDown: Boolean): BrightnessUi {
        val night = night(settings.brightnessMode, sunIsDown)
        return BrightnessUi(
            level = if (night) settings.nightLevel else settings.dayLevel,
            night = night,
            mode = settings.brightnessMode,
        )
    }

    /** Changes the level of whichever of day and night is showing, leaving the other alone. */
    fun step(settings: CabinSettings, sunIsDown: Boolean, delta: Int): CabinSettings =
        if (night(settings.brightnessMode, sunIsDown)) {
            settings.copy(nightLevel = (settings.nightLevel + delta).coerceIn(MIN_LEVEL, MAX_LEVEL))
        } else {
            settings.copy(dayLevel = (settings.dayLevel + delta).coerceIn(MIN_LEVEL, MAX_LEVEL))
        }

    /**
     * A level as Android's 0..255 brightness setting. The steps are spaced on a square curve because
     * the eye judges brightness roughly that way; level 1 stays just above the point where many panels
     * switch their backlight off. Adjust here once the unit's panel has been seen.
     */
    fun systemValue(level: Int): Int {
        val share = (level.coerceIn(MIN_LEVEL, MAX_LEVEL) - MIN_LEVEL) / (MAX_LEVEL - MIN_LEVEL).toFloat()
        return (8 + 247 * share * share).roundToInt()
    }

    fun windowValue(level: Int): Float = systemValue(level) / 255f
}

/** Brightness that only exists in memory: for the demo launch and for tests. */
class FixedBrightness(
    private var settings: CabinSettings = CabinSettings(),
    private val sunIsDown: Boolean = false,
) : BrightnessControl {
    private val ui = MutableStateFlow(BrightnessRules.ui(settings, sunIsDown))
    override val state: StateFlow<BrightnessUi> = ui
    override val canWrite: StateFlow<Boolean> = MutableStateFlow(true)
    override val windowBrightness: StateFlow<Float?> = MutableStateFlow(null)

    override fun up() = change { BrightnessRules.step(it, sunIsDown, 1) }
    override fun down() = change { BrightnessRules.step(it, sunIsDown, -1) }
    override fun cycleMode() = change { it.copy(brightnessMode = BrightnessRules.nextMode(it.brightnessMode)) }

    private fun change(transform: (CabinSettings) -> CabinSettings) {
        settings = transform(settings)
        ui.value = BrightnessRules.ui(settings, sunIsDown)
    }
}

/**
 * Sets Android's brightness setting, which needs the "modify system settings" permission. Auto follows
 * sunrise and sunset at the unit's last known position: the headlight signal is specific to each
 * vendor and is not something an ordinary app can read.
 */
class AndroidBrightness(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    clock: ClockSource,
) : BrightnessControl {

    private val sunIsDown = MutableStateFlow(readSunIsDown(settings.settings.value))
    private val writable = MutableStateFlow(Settings.System.canWrite(context))
    override val canWrite: StateFlow<Boolean> = writable

    override val state: StateFlow<BrightnessUi> = combine(settings.settings, sunIsDown, BrightnessRules::ui)
        .stateIn(scope, SharingStarted.Eagerly, BrightnessRules.ui(settings.settings.value, sunIsDown.value))

    override val windowBrightness: StateFlow<Float?> = combine(state, writable) { ui, canWrite ->
        if (canWrite) null else BrightnessRules.windowValue(ui.level)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // Day or night is re-judged once a minute, on the clock's own tick: no timer of its own.
        scope.launch { clock.clock.collect { refreshSun() } }
        // The level is written whenever it changes, and only once the saved settings have been read.
        scope.launch {
            combine(state, writable, settings.settings.map { it.loaded }) { ui, canWrite, loaded ->
                if (canWrite && loaded) ui.level else null
            }.distinctUntilChanged().collect { level -> if (level != null) write(level) }
        }
        scope.launch { rememberPosition() }
    }

    override fun up() = change { BrightnessRules.step(it, sunIsDown.value, 1) }
    override fun down() = change { BrightnessRules.step(it, sunIsDown.value, -1) }
    override fun cycleMode() = change { it.copy(brightnessMode = BrightnessRules.nextMode(it.brightnessMode)) }

    override fun refresh() {
        writable.value = Settings.System.canWrite(context)
        refreshSun()
        scope.launch { rememberPosition() }
    }

    private fun change(transform: (CabinSettings) -> CabinSettings) {
        scope.launch { settings.update(transform) }
    }

    private fun refreshSun() {
        sunIsDown.value = readSunIsDown(settings.settings.value)
    }

    private fun readSunIsDown(settings: CabinSettings): Boolean =
        SunTimes.isNight(ZonedDateTime.now(), settings.latitude, settings.longitude)

    private suspend fun write(level: Int) = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, BrightnessRules.systemValue(level))
        } catch (e: SecurityException) {
            writable.value = false
        }
    }

    /**
     * Keeps the last position Android already knows, if Cabin has been allowed to see it. Cabin never
     * switches the GPS on for this; a position that is hours old is fine for sunrise and sunset.
     */
    private suspend fun rememberPosition() {
        val allowed = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val best = withContext(Dispatchers.IO) {
            val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
            try {
                manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
            } catch (e: SecurityException) {
                null
            }
        } ?: return
        // Rounded to about a kilometre: all that sunrise needs, and no more than that is stored.
        val latitude = (best.latitude * 100).roundToInt() / 100.0
        val longitude = (best.longitude * 100).roundToInt() / 100.0
        val saved = settings.settings.value
        if (saved.latitude != latitude || saved.longitude != longitude) {
            settings.update { it.copy(latitude = latitude, longitude = longitude) }
            refreshSun()
        }
    }
}
