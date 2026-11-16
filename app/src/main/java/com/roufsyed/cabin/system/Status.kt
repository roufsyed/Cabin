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

    /** Whether the unit's Bluetooth is switched on, for the Settings tile and the tray. */
    val bluetoothOn: StateFlow<Boolean>

    /** Whether the unit's own Wi-Fi hotspot is on, for the tray. Null until Android has said. */
    val hotspotOn: StateFlow<Boolean?>

    /** Re-reads everything, for when a permission may have just been granted. */
    fun refresh() = Unit
}

class FixedStatus(value: StatusUi, bluetooth: Boolean = true, hotspot: Boolean? = false) : StatusSource {
    private val state = MutableStateFlow(value)
    private val radio = MutableStateFlow(bluetooth)
    override val status: StateFlow<StatusUi> = state
    override val bluetoothOn: StateFlow<Boolean> = radio
    override val hotspotOn: StateFlow<Boolean?> = MutableStateFlow(hotspot)

    fun set(value: StatusUi) {
        state.value = value
    }

    fun setBluetooth(on: Boolean) {
        radio.value = on
    }
}

/** Reading what Android says about the unit's hotspot. Kept apart from Android so it can be tested. */
object HotspotRules {
    /**
     * Sent by Android's Wi-Fi service whenever the hotspot changes state, with the state as a number.
     * The names are stable but are not public constants.
     */
    const val CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    const val STATE = "wifi_state"

    /**
     * What one of those says: on, off, or nothing settled yet (null) while the hotspot is still on
     * its way up or down. The numbers are Android's: 10 going down, 11 off, 12 coming up, 13 on,
     * 14 failed to start.
     */
    fun on(state: Int): Boolean? = when (state) {
        13 -> true
        11, 14 -> false
        else -> null
    }
}

/**
 * Reads the marks from Android and re-reads them only when Android announces a change.
 *
 * "Phone" means a phone is connected over Bluetooth through Android's own Bluetooth stack, in any of
 * the profiles a head unit uses. A unit whose Bluetooth is handled by a separate vendor module will
 * always read as not connected here; that has to be checked on the unit.
 *
 * The hotspot is different: Android gives an ordinary app nothing to ask, only an announcement when
 * the hotspot changes. Older versions keep the last one for whoever starts listening, so there the
 * state is known at once; newer ones do not, and it stays unknown until the hotspot is next switched.
 */
class AndroidStatus(private val context: Context) : StatusSource {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)
    private val bluetooth: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val state = MutableStateFlow(read())
    override val status: StateFlow<StatusUi> = state

    private val radio = MutableStateFlow(bluetoothEnabled())
    override val bluetoothOn: StateFlow<Boolean> = radio

    private val hotspot = MutableStateFlow<Boolean?>(null)
    override val hotspotOn: StateFlow<Boolean?> = hotspot

    init {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(HotspotRules.CHANGED)
        }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == HotspotRules.CHANGED) {
                        HotspotRules.on(intent.getIntExtra(HotspotRules.STATE, -1))?.let { hotspot.value = it }
                    } else {
                        refresh()
                    }
                }
            },
            filter,
        )
    }

    override fun refresh() {
        state.value = read()
        radio.value = bluetoothEnabled()
    }

    private fun bluetoothEnabled(): Boolean = try {
        bluetooth?.isEnabled == true
    } catch (e: RuntimeException) {
        false
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
                adapter.getProfileConnectionState(it) == BluetoothAdapter.STATE_CONNECTED
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
