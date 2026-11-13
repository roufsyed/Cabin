package com.roufsyed.cabin.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether Cabin is the unit's home app, and how to ask for that. */
interface HomeRole {
    val held: StateFlow<Boolean>

    /** Re-reads the role, for when the user comes back from the system's dialog or settings. */
    fun refresh()

    /** The system screen that makes Cabin the home app, or null if there is none. */
    fun requestIntent(): Intent?
}

class FixedHomeRole(isHeld: Boolean = true) : HomeRole {
    override val held: StateFlow<Boolean> = MutableStateFlow(isHeld)
    override fun refresh() = Unit
    override fun requestIntent(): Intent? = null
}

class AndroidHomeRole(private val context: Context) : HomeRole {
    private val roles: RoleManager? = context.getSystemService(RoleManager::class.java)
    private val state = MutableStateFlow(read())
    override val held: StateFlow<Boolean> = state

    override fun refresh() {
        state.value = read()
    }

    override fun requestIntent(): Intent? {
        val manager = roles
        return if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME) && !read()) {
            manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            // Already held, or the unit has no role dialog: the "Default home app" settings page.
            Intent(Settings.ACTION_HOME_SETTINGS).takeIf { it.resolveActivity(context.packageManager) != null }
        }
    }

    private fun read(): Boolean = try {
        roles?.isRoleHeld(RoleManager.ROLE_HOME) == true
    } catch (e: RuntimeException) {
        false
    }
}
