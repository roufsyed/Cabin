package com.roufsyed.cabin.system

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
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
        )
    }
}

object UnitRules {
    const val PAGE_LINES = 12

    private val ACTIONS = listOf(
        AccessibilityService.GLOBAL_ACTION_BACK to "back",
        AccessibilityService.GLOBAL_ACTION_RECENTS to "recent apps",
        AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN to "split screen",
        AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT to "screenshot",
        AccessibilityService.GLOBAL_ACTION_POWER_DIALOG to "power menu",
    )

    fun report(facts: UnitFacts): List<ReportLine> = listOf(
        ReportLine("Android", android(facts)),
        ReportLine("Build", facts.build.ifBlank { "Not given" }),
        ReportLine("Device", device(facts), lines = 2),
        ReportLine("Screen", "${facts.width} x ${facts.height} at ${facts.dpi} dpi"),
        ReportLine("Memory", memory(facts)),
        ReportLine("Split screen", split(facts), lines = 3),
        ReportLine("Accessibility", accessibility(facts), lines = 3),
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

    private fun switch(value: Boolean?): String = when (value) {
        true -> "on"
        false -> "off"
        null -> "not said"
    }
}
