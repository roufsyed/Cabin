package com.roufsyed.cabin.system

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.roufsyed.cabin.apps.AppsSource
import com.roufsyed.cabin.sources.SourceRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * "Clear memory" in the tray: closes what other apps have left running in the background.
 *
 * It is the one thing Android ever let an ordinary app do here, and newer Android does not let it
 * do even that (see [MemoryRules.allowed]). Where it is allowed, Android only closes an app's
 * processes that are doing nothing the user can see or hear, so whatever is playing or guiding
 * carries on, and so does the app in front. The list of recent apps is not touched, and an app
 * closed this way simply starts afresh the next time it is opened.
 */
interface MemoryControl {
    /** Whether this unit's Android lets an app close other apps' processes at all. */
    val allowed: Boolean

    /**
     * Closes the background processes of the apps the user has installed, all but the packages in
     * [keep], and answers with the memory the unit then has free, in bytes.
     */
    suspend fun clear(keep: Set<String>): Long
}

/**
 * What Clear memory goes by: whether it can be done at all, and how its result is put into words.
 * Kept apart from Android so it can be tested.
 */
object MemoryRules {
    private const val MB = 1024L * 1024L
    private const val GB = 1024L * MB

    /** The first security patch level of Android 11, 12 and 13 that refuses, and what a level looks like. */
    private const val FIRST_REFUSING_PATCH = "2023-06-01"
    private val PATCH_LEVEL = Regex("\\d{4}-\\d{2}-\\d{2}")

    /**
     * Whether Android lets an app close other apps' processes. Android 14 took that away, and the
     * security patches of June 2023 took it away from Android 11, 12 and 13 as well: from then on
     * the request is accepted and then ignored for every app but the one that asks, so there is
     * nothing to go by afterwards. [patch] is the level the unit reports, as "2023-05-05". A unit
     * that reports none is taken to allow it. (Android's own source, `ActivityManagerService`, in
     * the release of each level.)
     */
    fun allowed(sdk: Int, patch: String): Boolean =
        sdk < 34 && (!PATCH_LEVEL.matches(patch) || patch < FIRST_REFUSING_PATCH)

    /** "3.4 GB free", or below a gigabyte "850 MB free". Short enough for a button in the tray. */
    fun label(freeBytes: Long): String {
        val bytes = freeBytes.coerceAtLeast(0)
        return if (bytes >= GB) {
            String.format(Locale.US, "%.1f GB free", bytes / GB.toDouble())
        } else {
            "${bytes / MB} MB free"
        }
    }
}

/** Closes nothing and always reports the same free memory: for the demo launch and for tests. */
class FixedMemory(private val free: Long = 2_576_980_378L, override val allowed: Boolean = true) : MemoryControl {
    /** What each call was told to keep. */
    val kept = mutableListOf<Set<String>>()

    override suspend fun clear(keep: Set<String>): Long {
        kept += keep
        return free
    }
}

/**
 * Asks Android to close the apps' background processes, one app at a time.
 *
 * Only apps the user has installed are closed. What came with the unit is left alone: its radio,
 * Bluetooth and phone-link apps may keep something running that looks idle to Android and is not,
 * and which of them do is not known. Cabin's own process is never one of them.
 */
class AndroidMemory(private val context: Context, private val apps: AppsSource) : MemoryControl {
    private val manager = context.getSystemService(ActivityManager::class.java)

    override val allowed: Boolean = MemoryRules.allowed(Build.VERSION.SDK_INT, Build.VERSION.SECURITY_PATCH.orEmpty())

    override suspend fun clear(keep: Set<String>): Long = withContext(Dispatchers.IO) {
        val packages = apps.installed.value.mapTo(LinkedHashSet()) { SourceRules.packageOf(it.id) }
        for (name in packages) {
            if (name == context.packageName || name in keep || !installedByUser(name)) continue
            try {
                manager.killBackgroundProcesses(name)
            } catch (e: RuntimeException) {
                // Not allowed for this one; the rest are still tried.
            }
        }
        // Android takes a moment to count what was closed as free.
        delay(SETTLE_MS)
        free()
    }

    private fun installedByUser(name: String): Boolean = try {
        context.packageManager.getApplicationInfo(name, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0
    } catch (e: Exception) {
        // Gone in the meantime.
        false
    }

    private fun free(): Long = try {
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo).availMem
    } catch (e: RuntimeException) {
        0L
    }

    private companion object {
        const val SETTLE_MS = 600L
    }
}
