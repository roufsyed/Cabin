package com.roufsyed.cabin.system

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
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
)

interface UnitSource {
    fun facts(): UnitFacts
}

class FixedUnit(private val facts: UnitFacts) : UnitSource {
    override fun facts(): UnitFacts = facts
}

class AndroidUnit(private val context: Context, private val split: SplitWay, private val sideBar: SideBarState) : UnitSource {
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
        return UnitFacts(
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
