package com.roufsyed.cabin.apps

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.AlarmClock
import android.provider.Settings
import com.roufsyed.cabin.MainActivity
import com.roufsyed.cabin.data.SettingsStore
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.Glyph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface AppsSource {
    /** Every launchable app, sorted by name. */
    val installed: StateFlow<List<AppEntry>>

    /** Cabin's own tile. Tapping it opens Cabin's settings instead of launching anything. */
    val ownId: String

    /** Opens the app full screen. False if it could not be started. */
    fun launch(id: String): Boolean

    /**
     * Opens the app in the same half of a split screen as one of Cabin's own activities. [start] is
     * that activity starting what it is given. False if it could not be started.
     */
    fun launchBeside(id: String, start: (Intent) -> Unit): Boolean

    /** How the unit can show two apps side by side, if at all. */
    val split: SplitWay

    /**
     * Opens two apps side by side, [leftId] on the left. False if they could not be started.
     *
     * [open] has to carry out the pair's steps from Cabin's activity. Android only puts one app
     * beside another when an activity asks; asked by the application alone, it opens the app full
     * screen instead.
     */
    fun launchPair(leftId: String, rightId: String, open: (SplitPair) -> Boolean): Boolean
}

/**
 * The installed apps, read from the system off the main thread. The list seen last time is published
 * first, from the settings cache, so the grid can be drawn before the package manager has answered.
 */
class AndroidAppsSource(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : AppsSource {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val state = MutableStateFlow<List<AppEntry>>(emptyList())
    private val refreshing = Mutex()

    override val installed: StateFlow<List<AppEntry>> = state
    override val ownId: String = ComponentName(context, MainActivity::class.java).flattenToShortString()
    override val split: SplitWay = SplitScreen.way()

    init {
        scope.launch {
            val cached = settings.settings.first { it.loaded }.appCache
            if (state.value.isEmpty() && cached.isNotEmpty()) state.value = cached
            refresh()
        }
        val onChange = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
            private fun changed() {
                scope.launch { refresh() }
            }
        }
        launcherApps.registerCallback(onChange, Handler(Looper.getMainLooper()))
    }

    override fun launch(id: String): Boolean {
        val component = ComponentName.unflattenFromString(id) ?: return false
        return try {
            // No transition animation: the design is flat and the unit should not spend frames on it.
            val options = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            launcherApps.startMainActivity(component, Process.myUserHandle(), null, options)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    override fun launchBeside(id: String, start: (Intent) -> Unit): Boolean {
        val intent = SplitScreen.inSameHalf(id) ?: return false
        return try {
            start(intent)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    override fun launchPair(leftId: String, rightId: String, open: (SplitPair) -> Boolean): Boolean {
        val pair = SplitScreen.pair(leftId, rightId) ?: return false
        return open(pair)
    }

    private suspend fun refresh() = refreshing.withLock {
        val fresh = withContext(Dispatchers.IO) { query() }
        if (fresh != state.value) {
            state.value = fresh
            settings.update { it.copy(appCache = fresh) }
        }
    }

    private fun query(): List<AppEntry> {
        val classifier = Classifier(context.packageManager, context.packageName)
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .map { info ->
                AppEntry(
                    id = info.componentName.flattenToShortString(),
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    glyph = classifier.glyphFor(info),
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** Decides a tile's line icon from what the system knows about the app, then from its name. */
    private class Classifier(private val pm: PackageManager, private val ownPackage: String) {
        private val settings = handlers(Intent(Settings.ACTION_SETTINGS))
        private val dialers = handlers(Intent(Intent.ACTION_DIAL))
        private val maps = handlers(Intent(Intent.ACTION_VIEW, "geo:0,0".toUri()))
        private val browsers = handlers(
            Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
        )
        private val clocks = handlers(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        private val files = handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES))
        private val music = handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC))
        private val gallery = handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))
        private val weather = if (Build.VERSION.SDK_INT >= 33) {
            handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_WEATHER))
        } else {
            emptySet()
        }

        private fun handlers(intent: Intent): Set<String> = try {
            pm.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
        } catch (e: RuntimeException) {
            emptySet()
        }

        fun glyphFor(info: LauncherActivityInfo): Glyph {
            val pkg = info.componentName.packageName
            val category = info.applicationInfo.category
            return when {
                pkg == ownPackage -> Glyph.Settings
                pkg in settings -> Glyph.Settings
                pkg in dialers -> Glyph.Phone
                pkg in maps || category == ApplicationInfo.CATEGORY_MAPS -> Glyph.Maps
                pkg in browsers -> Glyph.Browser
                pkg in clocks -> Glyph.Clock
                pkg in files -> Glyph.Files
                pkg in music || category == ApplicationInfo.CATEGORY_AUDIO -> Glyph.Music
                category == ApplicationInfo.CATEGORY_VIDEO -> Glyph.Video
                pkg in gallery || category == ApplicationInfo.CATEGORY_IMAGE -> Glyph.Gallery
                pkg in weather -> Glyph.Weather
                else -> GlyphRules.fromLabel(info.label?.toString().orEmpty())
            }
        }
    }
}

/** A fixed list of apps: the fifteen the design is drawn with. For the demo launch and for tests. */
class FixedApps(
    apps: List<AppEntry> = designApps,
    override val ownId: String = id("Settings"),
    override val split: SplitWay = SplitWay.Adjacent,
) : AppsSource {
    override val installed: StateFlow<List<AppEntry>> = MutableStateFlow(apps)
    val launched = mutableListOf<String>()

    /** The pairs opened side by side, left app first. */
    val pairs = mutableListOf<Pair<String, String>>()

    override fun launch(id: String): Boolean {
        launched += id
        return true
    }

    override fun launchBeside(id: String, start: (Intent) -> Unit): Boolean {
        launched += id
        return true
    }

    override fun launchPair(leftId: String, rightId: String, open: (SplitPair) -> Boolean): Boolean {
        pairs += leftId to rightId
        return true
    }

    companion object {
        val designApps: List<AppEntry> = listOf(
            "Browser" to Glyph.Browser, "Clock" to Glyph.Clock, "Dashcam" to Glyph.Letter, "Files" to Glyph.Files,
            "Gallery" to Glyph.Gallery, "Maps" to Glyph.Maps, "Music" to Glyph.Music, "Phone" to Glyph.Phone,
            "Phone link" to Glyph.Link, "Radio" to Glyph.Radio, "Settings" to Glyph.Settings, "Sound" to Glyph.Sound,
            "Video" to Glyph.Video, "Voice" to Glyph.Voice, "Weather" to Glyph.Weather,
        ).map { (label, glyph) -> AppEntry(id(label), label, glyph) }

        /** The component id a design app is given, e.g. "design.maps/.Main". */
        fun id(label: String): String = "design.${label.lowercase().replace(" ", "")}/.Main"
    }
}
