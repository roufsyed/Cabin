package com.roufsyed.cabin.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The runtime permissions Cabin can use, each optional: without one, the feature it serves is simply off. */
enum class Grant(val permission: String) {
    /** Sunrise and sunset for Auto brightness. */
    Location(Manifest.permission.ACCESS_COARSE_LOCATION),

    /** "Where to?" by voice. */
    Microphone(Manifest.permission.RECORD_AUDIO),

    /** The phone-connected mark beside the clock. Android 12 and later; older versions need nothing. */
    NearbyDevices("android.permission.BLUETOOTH_CONNECT"),
}

interface PermissionsSource {
    val granted: StateFlow<Set<Grant>>

    /** Re-reads them, after the user has answered a system dialog or come back from settings. */
    fun refresh() = Unit
}

class FixedPermissions(initial: Set<Grant> = Grant.entries.toSet()) : PermissionsSource {
    private val state = MutableStateFlow(initial)
    override val granted: StateFlow<Set<Grant>> = state

    fun set(value: Set<Grant>) {
        state.value = value
    }
}

class AndroidPermissions(private val context: Context) : PermissionsSource {
    private val state = MutableStateFlow(read())
    override val granted: StateFlow<Set<Grant>> = state

    override fun refresh() {
        state.value = read()
    }

    private fun read(): Set<Grant> = Grant.entries.filterTo(HashSet()) {
        (it == Grant.NearbyDevices && Build.VERSION.SDK_INT < 31) ||
            context.checkSelfPermission(it.permission) == PackageManager.PERMISSION_GRANTED
    }
}
