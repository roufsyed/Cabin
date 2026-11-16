package com.roufsyed.cabin.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
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

    /** Moves the level that is showing, day's or night's, by [delta] steps. */
    fun step(delta: Int)
    fun up() = step(1)
    fun down() = step(-1)

    /** Auto, then Day, then Night, then Auto again. */
    fun cycleMode()

    /** The tray's Night mode switch: the other look from the one showing. See [BrightnessRules.toggled]. */
    fun toggleNight()

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

    /**
     * Whether Auto should treat it as dark outside. The headlights decide while the unit reports them
     * and the user has left that on, as in the design; otherwise sunrise and sunset do.
     */
    fun dark(settings: CabinSettings, headlights: Boolean?, sunIsDown: Boolean): Boolean =
        if (settings.followHeadlights && headlights != null) headlights else sunIsDown

    /** Whether the night level applies: forced by Day or Night, otherwise by whether it is [dark]. */
    fun night(mode: BrightnessMode, dark: Boolean): Boolean = when (mode) {
        BrightnessMode.Auto -> dark
        BrightnessMode.Day -> false
        BrightnessMode.Night -> true
    }

    /**
     * Night mode switched by one button, as in the tray: the look changes to the other one, night to
     * day or day to night, every time. Where Auto would give the wanted look by itself the mode goes
     * back to Auto; otherwise the look is forced. So a switch thrown by hand does not outlast its
     * reason: forced on in the afternoon and switched off the next morning, it leaves Auto in charge.
     */
    fun toggled(settings: CabinSettings, dark: Boolean): CabinSettings {
        val wantNight = !night(settings.brightnessMode, dark)
        val mode = when {
            wantNight == dark -> BrightnessMode.Auto
            wantNight -> BrightnessMode.Night
            else -> BrightnessMode.Day
        }
        return settings.copy(brightnessMode = mode)
    }

    fun ui(settings: CabinSettings, dark: Boolean): BrightnessUi {
        val night = night(settings.brightnessMode, dark)
        return BrightnessUi(
            level = if (night) settings.nightLevel else settings.dayLevel,
            night = night,
            mode = settings.brightnessMode,
        )
    }

    /** Changes the level of whichever of day and night is showing, leaving the other alone. */
    fun step(settings: CabinSettings, dark: Boolean, delta: Int): CabinSettings =
        if (night(settings.brightnessMode, dark)) {
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

/**
 * The rules applied to the saved settings, the headlights and the sun. It is the whole of brightness
 * apart from actually setting the screen, and is shared by the unit's control and the in-memory one.
 */
private class BrightnessCore(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val headlights: HeadlightSignal,
    private val sunIsDown: StateFlow<Boolean>,
) {
    private fun dark(settings: CabinSettings): Boolean =
        BrightnessRules.dark(settings, headlights.on.value, sunIsDown.value)

    val state: StateFlow<BrightnessUi> =
        combine(settings.settings, headlights.on, sunIsDown) { saved, lights, sun ->
            BrightnessRules.ui(saved, BrightnessRules.dark(saved, lights, sun))
        }.stateIn(scope, SharingStarted.Eagerly, settings.settings.value.let { BrightnessRules.ui(it, dark(it)) })

    fun step(delta: Int) = change { BrightnessRules.step(it, dark(it), delta) }

    fun cycleMode() = change { it.copy(brightnessMode = BrightnessRules.nextMode(it.brightnessMode)) }

    fun toggleNight() = change { BrightnessRules.toggled(it, dark(it)) }

    private fun change(transform: (CabinSettings) -> CabinSettings) {
        scope.launch { settings.update(transform) }
    }
}

/** Brightness that sets no real screen: for the demo launch and for tests. */
class FixedBrightness(
    settings: SettingsStore = InMemorySettings(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    headlights: HeadlightSignal = NoHeadlightSignal,
    sunIsDown: Boolean = false,
) : BrightnessControl {
    private val core = BrightnessCore(settings, scope, headlights, MutableStateFlow(sunIsDown))
    override val state: StateFlow<BrightnessUi> = core.state
    override val canWrite: StateFlow<Boolean> = MutableStateFlow(true)
    override val windowBrightness: StateFlow<Float?> = MutableStateFlow(null)

    override fun step(delta: Int) = core.step(delta)
    override fun cycleMode() = core.cycleMode()
    override fun toggleNight() = core.toggleNight()
}

/**
 * Sets Android's brightness setting, which needs the "modify system settings" permission. Auto goes
 * by the car's headlights when [headlights] reports them, and otherwise by sunrise and sunset at the
 * unit's last known position.
 */
class AndroidBrightness(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    clock: ClockSource,
    headlights: HeadlightSignal,
) : BrightnessControl {

    private val sunIsDown = MutableStateFlow(readSunIsDown(settings.settings.value))
    private val core = BrightnessCore(settings, scope, headlights, sunIsDown)
    private val writable = MutableStateFlow(Settings.System.canWrite(context))
    override val canWrite: StateFlow<Boolean> = writable

    override val state: StateFlow<BrightnessUi> = core.state

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

    override fun step(delta: Int) = core.step(delta)
    override fun cycleMode() = core.cycleMode()
    override fun toggleNight() = core.toggleNight()

    override fun refresh() {
        writable.value = Settings.System.canWrite(context)
        refreshSun()
        scope.launch { rememberPosition() }
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
