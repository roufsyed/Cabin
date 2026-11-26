package com.roufsyed.cabin.system

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import com.roufsyed.cabin.apps.SplitScreen
import com.roufsyed.cabin.apps.SplitWay
import com.roufsyed.cabin.bar.SideBarState
import com.roufsyed.cabin.media.ReportLine
import java.util.Locale

data class UnitFacts(
    val release: String,
    val sdk: Int,
    val patch: String,
    val build: String,
    val maker: String,
    val model: String,
    val board: String,
    val hardware: String,
    val width: Int,
    val height: Int,
    val dpi: Int,
    val memoryBytes: Long,
    val lowMemory: Boolean,
    val multiWindow: Boolean?,
    val splitScreen: Boolean?,
    val split: SplitWay,
    val serviceOn: Boolean,
    val offered: List<Int>?,
    val wifiAddress: String? = null,
    val debugging: Boolean? = null,
    val microphone: Boolean? = null,
    val bluetooth: Boolean? = null,
    val volumeFixed: Boolean? = null,
    val wifi: Boolean? = null,
    val wifiOn: Boolean? = null,
    val gps: Boolean? = null,
    val gpsOn: Boolean? = null,
    val touchPoints: Int? = null,
    val cameras: Int? = null,
    val usbHost: Boolean? = null,
    val telephony: Boolean? = null,
    val lightSensor: Boolean? = null,
    val pictureInPicture: Boolean? = null,
    val freeform: Boolean? = null,
    val rotary: List<String>? = null,
)

data class CabinFacts(
    val home: Boolean = false,
    val notifications: Boolean = false,
    val brightness: Boolean = false,
    val barLost: Boolean = false,
    val granted: Set<Grant> = emptySet(),
    val speech: Boolean = false,
    val positioned: Boolean = false,
    val bluetoothOn: Boolean = false,
    val headlights: Boolean? = null,
    val keys: List<Int> = emptyList(),
)

interface UnitSource {
    fun facts(): UnitFacts
}

class FixedUnit(private val facts: UnitFacts) : UnitSource {
    override fun facts(): UnitFacts = facts
}

class AndroidUnit(private val context: Context, private val split: SplitWay, private val sideBar: SideBarState) : UnitSource {
    private val built: UnitFacts by lazy {
        UnitFacts(
            release = "", sdk = 0, patch = "", build = "", maker = "", model = "", board = "", hardware = "",
            width = 0, height = 0, dpi = 0, memoryBytes = 0, lowMemory = false, multiWindow = null, splitScreen = null,
            split = split, serviceOn = false, offered = null,
            microphone = has(PackageManager.FEATURE_MICROPHONE),
            bluetooth = has(PackageManager.FEATURE_BLUETOOTH),
            wifi = has(PackageManager.FEATURE_WIFI),
            gps = has(PackageManager.FEATURE_LOCATION_GPS),
            touchPoints = touchPoints(),
            cameras = attempt { context.getSystemService(CameraManager::class.java).cameraIdList.size },
            usbHost = has(PackageManager.FEATURE_USB_HOST),
            telephony = has(PackageManager.FEATURE_TELEPHONY),
            lightSensor = attempt {
                context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_LIGHT) != null
            },
            pictureInPicture = has(PackageManager.FEATURE_PICTURE_IN_PICTURE),
            freeform = has(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
        )
    }

    private fun <T> attempt(read: () -> T): T? = try {
        read()
    } catch (e: Exception) {
        null
    }

    private fun has(feature: String): Boolean? = attempt { context.packageManager.hasSystemFeature(feature) }

    private fun touchPoints(): Int? = when {
        has(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) == true -> 5
        has(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) == true -> 2
        has(PackageManager.FEATURE_TOUCHSCREEN) == true -> 1
        has(PackageManager.FEATURE_TOUCHSCREEN) == false -> 0
        else -> null
    }

    override fun facts(): UnitFacts {
        val metrics = DisplayMetrics()
        try {
            @Suppress("DEPRECATION")
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        } catch (e: RuntimeException) {
            metrics.setTo(context.resources.displayMetrics)
        }
        val activity = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }
        return built.copy(
            release = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            patch = Build.VERSION.SECURITY_PATCH.orEmpty(),
            build = Build.DISPLAY.orEmpty(),
            maker = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            dpi = metrics.densityDpi,
            memoryBytes = memory.totalMem,
            lowMemory = activity.isLowRamDevice,
            multiWindow = SplitScreen.multiWindow(),
            splitScreen = SplitScreen.splitScreen(),
            split = split,
            serviceOn = sideBar.allowed.value,
            offered = sideBar.offered?.invoke(),
            wifiAddress = wifiAddress(),
            debugging = debugging(),
            volumeFixed = attempt { context.getSystemService(AudioManager::class.java).isVolumeFixed },
            wifiOn = attempt { context.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled },
            gpsOn = attempt {
                context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)
            },
            rotary = attempt {
                InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
                    .filter { it.sources and InputDevice.SOURCE_ROTARY_ENCODER == InputDevice.SOURCE_ROTARY_ENCODER }
                    .map { it.name }
            },
        )
    }

    private fun wifiAddress(): String? = try {
        @Suppress("DEPRECATION")
        val raw = context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo.ipAddress
        if (raw == 0) null else UnitRules.dotted(raw)
    } catch (e: RuntimeException) {
        null
    }

    private fun debugging(): Boolean? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED) == 1
    } catch (e: Exception) {
        null
    }
}

object UnitRules {
    const val PAGE_LINES = 12
    const val PAGE_ROWS = 11
    const val ADB_PORT = 5555

    private val ACTIONS = listOf(
        AccessibilityService.GLOBAL_ACTION_BACK to "back",
        AccessibilityService.GLOBAL_ACTION_RECENTS to "recent apps",
        AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN to "split screen",
        AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT to "screenshot",
        AccessibilityService.GLOBAL_ACTION_POWER_DIALOG to "power menu",
    )

    fun report(facts: UnitFacts): List<ReportLine> = listOfNotNull(
        ReportLine("Android", android(facts)),
        ReportLine("Build", facts.build.ifBlank { "Not given" }),
        ReportLine("Device", device(facts), lines = 2),
        ReportLine("Screen", "${facts.width} x ${facts.height} at ${facts.dpi} dpi"),
        ReportLine("Memory", memory(facts)),
        ReportLine("Split screen", split(facts), lines = 2),
        ReportLine("Accessibility", accessibility(facts), lines = 2),
        network(facts)?.let { ReportLine("Network", it, lines = 2) },
    )

    fun ready(facts: UnitFacts, cabin: CabinFacts): List<ReportLine> = listOf(
        ReportLine("Home button", if (cabin.home) "Ready: Cabin is the home app" else "Not yet: another app is the home app"),
        ReportLine(
            "Notifications",
            if (cabin.notifications) "Ready: now playing, navigation and the tray can be read"
            else "Not yet: allow notification access in Setup",
        ),
        ReportLine(
            "Brightness",
            if (cabin.brightness) "Ready: Cabin sets the unit's brightness" else "Not yet: allow brightness control in Setup",
        ),
        ReportLine("Side bar", sideBar(facts, cabin)),
        ReportLine("Split screen", splitReady(facts)),
        ReportLine("Voice", voice(facts, cabin)),
        ReportLine("Position", position(cabin)),
        ReportLine("Bluetooth", bluetooth(facts, cabin)),
        ReportLine("Volume", volume(facts)),
        ReportLine(
            "Headlights",
            if (cabin.headlights == null) "No signal known for this unit yet" else "Ready: the unit reports them",
        ),
        ReportLine("Keys and knob", keys(cabin.keys)),
    )

    fun hardware(facts: UnitFacts): List<ReportLine> = listOf(
        ReportLine("Wi-Fi", switched(facts.wifi, facts.wifiOn)),
        ReportLine("GPS", switched(facts.gps, facts.gpsOn)),
        ReportLine("Touch", touch(facts.touchPoints)),
        ReportLine("Knob", knob(facts.rotary)),
        ReportLine("Cameras", count(facts.cameras)),
        ReportLine("USB host", said(facts.usbHost, "Yes", "No")),
        ReportLine("Phone modem", said(facts.telephony, "Yes", "None")),
        ReportLine("Light sensor", said(facts.lightSensor, "Yes", "None")),
        ReportLine("Picture in picture", said(facts.pictureInPicture, "Yes", "No")),
        ReportLine("Free windows", said(facts.freeform, "Yes", "No")),
    )

    private val KEYS = mapOf(
        KeyEvent.KEYCODE_VOLUME_UP to "volume up",
        KeyEvent.KEYCODE_VOLUME_DOWN to "volume down",
        KeyEvent.KEYCODE_VOLUME_MUTE to "mute",
        KeyEvent.KEYCODE_MEDIA_NEXT to "next",
        KeyEvent.KEYCODE_MEDIA_PREVIOUS to "previous",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "play",
        KeyEvent.KEYCODE_MEDIA_PLAY to "play",
        KeyEvent.KEYCODE_MEDIA_PAUSE to "play",
        KeyEvent.KEYCODE_HEADSETHOOK to "play",
        KeyEvent.KEYCODE_VOICE_ASSIST to "voice",
        KeyEvent.KEYCODE_SEARCH to "voice",
        KeyEvent.KEYCODE_ASSIST to "voice",
        KeyEvent.KEYCODE_BUTTON_MODE to "mode",
        KeyEvent.KEYCODE_BACK to "back",
        KeyEvent.KEYCODE_MENU to "menu",
        KeyEvent.KEYCODE_ENTER to "enter",
        KeyEvent.KEYCODE_DPAD_CENTER to "select",
        KeyEvent.KEYCODE_DPAD_UP to "up",
        KeyEvent.KEYCODE_DPAD_DOWN to "down",
        KeyEvent.KEYCODE_DPAD_LEFT to "left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "right",
    )

    fun keys(seen: List<Int>): String {
        val names = seen.map { KEYS[it] ?: "key $it" }.distinct()
        return if (names.isEmpty()) "None seen yet: press a wheel key or turn the knob" else "Seen: ${names.joinToString()}"
    }

    private fun knob(rotary: List<String>?): String = when {
        rotary == null -> "Not said"
        rotary.isEmpty() -> "Not listed by Android. Its turns may still arrive as keys"
        else -> "Android lists a rotary control: ${rotary.joinToString()}"
    }

    private fun sideBar(facts: UnitFacts, cabin: CabinFacts): String = when {
        facts.serviceOn -> "Ready: Cabin is allowed under Accessibility"
        cabin.barLost -> "Not yet: the unit switched it off. Allow it again in Setup"
        else -> "Not yet: allow Cabin under Accessibility in Setup"
    }

    private fun splitReady(facts: UnitFacts): String = when (facts.split) {
        SplitWay.Adjacent -> "Ready: Android's own"
        SplitWay.None -> "None: this unit's Android has no split screen"
        SplitWay.Toggle -> when {
            !facts.serviceOn -> "Not yet: allow Cabin under Accessibility in Setup"
            facts.offered?.contains(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) == false ->
                "None: this Android does not offer it to Cabin"
            else -> "Can be asked for. Try it: some units do not finish it"
        }
    }

    private fun voice(facts: UnitFacts, cabin: CabinFacts): String = when {
        facts.microphone == false -> "None: this unit has no microphone"
        !cabin.speech -> "None: this unit has no speech service"
        Grant.Microphone !in cabin.granted -> "Not yet: allow the microphone in Setup"
        else -> "Ready: \"Where to?\" listens"
    }

    private fun position(cabin: CabinFacts): String = when {
        cabin.positioned -> "Ready: a position is known, for sunrise and sunset"
        Grant.Location !in cabin.granted -> "Not yet: allow location in Setup"
        else -> "Allowed, but Android has no position yet"
    }

    private fun bluetooth(facts: UnitFacts, cabin: CabinFacts): String = when {
        facts.bluetooth == false -> "None on this unit"
        !cabin.bluetoothOn -> "Off"
        Grant.NearbyDevices !in cabin.granted -> "On. Allow nearby devices in Setup to show the phone"
        else -> "On, and Cabin can show a connected phone"
    }

    private fun volume(facts: UnitFacts): String = when (facts.volumeFixed) {
        true -> "Fixed by the unit: Cabin cannot change it"
        false -> "Ready: Cabin can change it"
        null -> "Not said"
    }

    private fun switched(has: Boolean?, on: Boolean?): String = when {
        has == false -> "None"
        on == true -> "On"
        on == false -> "Off"
        has == true -> "Yes"
        else -> "Not said"
    }

    private fun touch(points: Int?): String = when {
        points == null -> "Not said"
        points >= 5 -> "5 or more fingers at once"
        points >= 2 -> "2 or more fingers at once"
        points == 1 -> "One finger at a time: two-finger drags will not work"
        else -> "None declared"
    }

    private fun count(number: Int?): String = when (number) {
        null -> "Not said"
        0 -> "None"
        else -> number.toString()
    }

    private fun said(value: Boolean?, yes: String, no: String): String = when (value) {
        true -> yes
        false -> no
        null -> "Not said"
    }

    fun android(facts: UnitFacts): String {
        val patch = if (facts.patch.isBlank()) "no security patch level given" else "security patch ${facts.patch}"
        return "${facts.release.ifBlank { "?" }} (API ${facts.sdk}), $patch"
    }

    fun device(facts: UnitFacts): String {
        val name = listOf(facts.maker, facts.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Not given" }
        val parts = listOfNotNull(
            facts.board.takeIf { it.isNotBlank() }?.let { "board $it" },
            facts.hardware.takeIf { it.isNotBlank() }?.let { "hardware $it" },
        )
        return (listOf(name) + parts).joinToString(", ")
    }

    fun memory(facts: UnitFacts): String {
        val gb = facts.memoryBytes / (1024.0 * 1024.0 * 1024.0)
        val size = String.format(Locale.US, "%.1f GB", gb)
        return if (facts.lowMemory) "$size, which Android counts as a low-memory device" else size
    }

    fun split(facts: UnitFacts): String {
        val way = when (facts.split) {
            SplitWay.Adjacent -> "Android's own: Cabin opens one app beside another"
            SplitWay.Toggle -> "Android's older kind: Cabin asks for it through Accessibility"
            SplitWay.None -> "None that Cabin can use"
        }
        return "$way (multi-window ${switch(facts.multiWindow)}, split screen ${switch(facts.splitScreen)})"
    }

    fun accessibility(facts: UnitFacts): String {
        if (!facts.serviceOn) return "Off: Cabin is not allowed under Accessibility just now"
        val offered = facts.offered ?: return "On. This Android does not say what it offers"
        val names = ACTIONS.filter { it.first in offered }.map { it.second }
        val missing = ACTIONS.filter { it.first !in offered }.map { it.second }
        val has = if (names.isEmpty()) "nothing Cabin uses" else names.joinToString()
        return if (missing.isEmpty()) "On. Android offers $has" else "On. Android offers $has; not ${missing.joinToString()}"
    }

    fun network(facts: UnitFacts): String? {
        val address = facts.wifiAddress ?: return null
        val connect = "adb connect $address:$ADB_PORT"
        return when (facts.debugging) {
            true -> "$address on Wi-Fi. USB debugging is on: try $connect"
            false -> "$address on Wi-Fi. USB debugging is off; once on, try $connect"
            null -> "$address on Wi-Fi. Try $connect"
        }
    }

    fun dotted(raw: Int): String = listOf(raw, raw shr 8, raw shr 16, raw shr 24).joinToString(".") { (it and 0xff).toString() }

    private fun switch(value: Boolean?): String = when (value) {
        true -> "on"
        false -> "off"
        null -> "not said"
    }
}
