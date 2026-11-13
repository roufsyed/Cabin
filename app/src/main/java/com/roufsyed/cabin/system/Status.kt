package com.roufsyed.cabin.system

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import com.roufsyed.cabin.model.StatusUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The three status marks beside the clock: phone connected, Wi-Fi on, GPS on. */
interface StatusSource {
    val status: StateFlow<StatusUi>

    /** Re-reads everything, for when a permission may have just been granted. */
    fun refresh() = Unit
}

class FixedStatus(value: StatusUi) : StatusSource {
    private val state = MutableStateFlow(value)
    override val status: StateFlow<StatusUi> = state

    fun set(value: StatusUi) {
        state.value = value
    }
}

/**
 * Reads the marks from Android and re-reads them only when Android announces a change.
 *
 * "Phone" means a phone is connected over Bluetooth through Android's own Bluetooth stack, in any of
 * the profiles a head unit uses. A unit whose Bluetooth is handled by a separate vendor module will
 * always read as not connected here; that has to be checked on the unit.
 */
class AndroidStatus(private val context: Context) : StatusSource {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)
    private val bluetooth: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val state = MutableStateFlow(read())
    override val status: StateFlow<StatusUi> = state

    init {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = refresh()
            },
            filter,
        )
    }

    override fun refresh() {
        state.value = read()
    }

    private fun read() = StatusUi(phone = phoneConnected(), wifi = wifiOn(), gps = gpsOn())

    private fun wifiOn(): Boolean = try {
        wifi?.isWifiEnabled == true
    } catch (e: RuntimeException) {
        false
    }

    private fun gpsOn(): Boolean = try {
        location?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    } catch (e: RuntimeException) {
        false
    }

    private fun phoneConnected(): Boolean {
        val adapter = bluetooth ?: return false
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            adapter.isEnabled && PROFILES.any {
                adapter.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED
            }
        } catch (e: SecurityException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }

    private companion object {
        /**
         * Hands-free and audio, in both directions: a head unit is the "client" and "sink" side
         * (Android's profile numbers 16 and 11), a phone or tablet the other.
         */
        val PROFILES = intArrayOf(16, 11, BluetoothProfile.HEADSET, BluetoothProfile.A2DP)
    }
}
