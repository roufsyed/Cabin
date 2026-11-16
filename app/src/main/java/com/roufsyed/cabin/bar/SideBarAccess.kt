package com.roufsyed.cabin.bar

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

interface SideBarAccess {
    fun enabled(): Boolean

    fun restore(): Boolean

    fun stoppedAt(): Long?
}

data class SideBarLoss(val stoppedAtMs: Long?)

class FixedSideBarAccess(
    var isEnabled: Boolean = true,
    private val canRestore: Boolean = false,
    var stopped: Long? = null,
) : SideBarAccess {
    var restored = 0
        private set

    override fun enabled(): Boolean = isEnabled

    override fun restore(): Boolean {
        if (!canRestore) return false
        restored++
        isEnabled = true
        return true
    }

    override fun stoppedAt(): Long? = stopped
}

class AndroidSideBarAccess(private val context: Context) : SideBarAccess {
    private val service = ComponentName(context, SideBarService::class.java)

    private fun switchedOn(): List<String> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').filter { it.isNotBlank() }

    override fun enabled(): Boolean = try {
        switchedOn().any { ComponentName.unflattenFromString(it) == service }
    } catch (e: RuntimeException) {
        false
    }

    override fun restore(): Boolean {
        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val resolver = context.contentResolver
            val others = switchedOn().filter { ComponentName.unflattenFromString(it) != service }
            Settings.Secure.putString(
                resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                (others + service.flattenToString()).joinToString(":"),
            )
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    override fun stoppedAt(): Long? {
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(null, 0, 1)
                .firstOrNull()
                ?.takeIf { it.reason == ApplicationExitInfo.REASON_USER_REQUESTED }
                ?.timestamp
        } catch (e: RuntimeException) {
            null
        }
    }

    private companion object {
        const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    }
}
