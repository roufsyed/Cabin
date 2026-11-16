package com.roufsyed.cabin.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager

/**
 * The unit's Bluetooth and Wi-Fi switches, thrown from Cabin's tray as far as Android allows.
 *
 * Android has taken both away from ordinary apps: Wi-Fi since Android 10, and Bluetooth since
 * Android 13 for apps built for it, as Cabin is. Each call here is the standard one and reports
 * whether Android took it. Where it did not, the tray hands over to the unit's own switch instead,
 * so the button is never a dead one, and a unit whose firmware is more lenient gets a real switch.
 */
interface UnitSwitches {
    /** Switches Bluetooth on or off. False if Android refused, and nothing has changed. */
    fun setBluetooth(on: Boolean): Boolean

    /** Likewise for Wi-Fi. */
    fun setWifi(on: Boolean): Boolean
}

/**
 * Switches that work at once on a [FixedStatus], or, with [allowed] false, refuse as Android 13
 * does: for the demo launch and for tests.
 */
class FixedSwitches(private val status: FixedStatus, private val allowed: Boolean = true) : UnitSwitches {
    /** What was asked for, in order, as "bluetooth off" or "wifi on". */
    val asked = mutableListOf<String>()

    override fun setBluetooth(on: Boolean): Boolean {
        asked += "bluetooth ${if (on) "on" else "off"}"
        if (allowed) status.setBluetooth(on)
        return allowed
    }

    override fun setWifi(on: Boolean): Boolean {
        asked += "wifi ${if (on) "on" else "off"}"
        if (allowed) status.set(status.status.value.copy(wifi = on))
        return allowed
    }
}

/**
 * Android's own calls. Both are marked as no longer for use, for the very reason given above: they
 * answer false to an app that may not switch, which is what the tray goes by.
 */
class AndroidSwitches(context: Context) : UnitSwitches {
    private val bluetooth: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    // The tray asks for the "nearby devices" permission first; without it Android throws, which is
    // caught here and reads as a refusal like any other.
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun setBluetooth(on: Boolean): Boolean = try {
        val adapter = bluetooth
        adapter != null && (if (on) adapter.enable() else adapter.disable())
    } catch (e: RuntimeException) {
        false
    }

    @Suppress("DEPRECATION")
    override fun setWifi(on: Boolean): Boolean = try {
        wifi?.setWifiEnabled(on) == true
    } catch (e: RuntimeException) {
        false
    }
}
