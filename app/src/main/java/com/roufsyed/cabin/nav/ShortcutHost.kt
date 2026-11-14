package com.roufsyed.cabin.nav

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process

/**
 * The launcher shortcuts other apps publish: the entries on a long press of an app's icon. Android
 * lets only the unit's home app read and start them, which Cabin is.
 */
interface ShortcutHost {
    /** What [packageName] publishes. Empty when it has none, or when Cabin is not the home app. */
    fun shortcuts(packageName: String): List<PlaceShortcut>

    /** Starts one of them. False if it could not be started. */
    fun start(packageName: String, id: String): Boolean
}

/** Shortcuts as Android's launcher service gives them. Both calls ask the system, so keep them off the main thread. */
class LauncherShortcuts(context: Context) : ShortcutHost {
    private val launcher = context.getSystemService(LauncherApps::class.java)

    override fun shortcuts(packageName: String): List<PlaceShortcut> = try {
        if (launcher == null || !launcher.hasShortcutHostPermission()) {
            emptyList()
        } else {
            val query = LauncherApps.ShortcutQuery().setPackage(packageName).setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
            launcher.getShortcuts(query, Process.myUserHandle()).orEmpty()
                .filter { it.isEnabled }
                .map { PlaceShortcut(it.id, it.shortLabel?.toString().orEmpty()) }
        }
    } catch (e: RuntimeException) {
        emptyList()
    }

    override fun start(packageName: String, id: String): Boolean = try {
        launcher.startShortcut(packageName, id, null, null, Process.myUserHandle())
        true
    } catch (e: RuntimeException) {
        // The shortcut has gone, or Cabin has stopped being the home app.
        false
    }
}
