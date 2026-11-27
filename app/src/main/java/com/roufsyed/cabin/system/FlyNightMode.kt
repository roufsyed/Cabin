package com.roufsyed.cabin.system

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class FlyNightMode(context: Context, private val scope: CoroutineScope) : HeadlightSignal {
    private val resolver: ContentResolver = context.contentResolver
    private val state = MutableStateFlow(read())
    override val on: StateFlow<Boolean?> = state

    init {
        resolver.registerContentObserver(
            Settings.Global.getUriFor(KEY),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    state.value = read()
                }
            },
        )
    }

    fun sync(brightness: BrightnessControl) {
        if (read() == null) return
        brightness.state
            .map { it.night }
            .distinctUntilChanged()
            .onEach { write(it) }
            .launchIn(scope)
    }

    private fun read(): Boolean? = try {
        Settings.Global.getInt(resolver, KEY) != 0
    } catch (e: Settings.SettingNotFoundException) {
        null
    }

    private fun write(night: Boolean) {
        try {
            Settings.Global.putInt(resolver, KEY, if (night) 1 else 0)
        } catch (e: SecurityException) {
        }
    }

    private companion object {
        const val KEY = "fly_night_mode_enable"
    }
}
