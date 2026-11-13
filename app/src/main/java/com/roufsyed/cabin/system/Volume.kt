package com.roufsyed.cabin.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.roufsyed.cabin.model.VolumeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The unit's media volume. One call is one step, so one tap is one step. */
interface VolumeControl {
    val volume: StateFlow<VolumeUi>

    /** The highest level the unit has. */
    val max: Int

    fun up()
    fun down()
    fun toggleMute()

    /** Jumps to [level], unmuting. Used by the two-finger drag. */
    fun set(level: Int)

    /** Re-reads the level, for when something outside Cabin may have changed it. */
    fun refresh() = Unit
}

/** A volume that only exists in memory: for the demo launch and for tests. */
class FixedVolume(level: Int = 12, override val max: Int = 30) : VolumeControl {
    private val state = MutableStateFlow(VolumeUi(level, muted = false))
    override val volume: StateFlow<VolumeUi> = state

    override fun up() = set(state.value.level + 1)
    override fun down() = set(state.value.level - 1)
    override fun toggleMute() = state.update { it.copy(muted = !it.muted) }
    override fun set(level: Int) = state.update { VolumeUi(level.coerceIn(0, max), muted = false) }
}

/**
 * The standard Android media stream, which is what a head unit's amplifier follows unless the vendor
 * routes volume elsewhere. Any change made outside Cabin (the wheel keys, another app) is picked up
 * from the system's own change broadcast, so the number on the rail is always the real one.
 */
class AndroidVolume(context: Context) : VolumeControl {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val stream = AudioManager.STREAM_MUSIC

    /** Android reports a muted stream as level 0, so the level to show while muted is remembered here. */
    private var lastAudible = audio.getStreamVolume(stream)

    override val max: Int = audio.getStreamMaxVolume(stream)

    private val state = MutableStateFlow(read())
    override val volume: StateFlow<VolumeUi> = state

    /** True when the unit refuses volume changes from apps: its volume is then handled outside Android. */
    val fixed: Boolean get() = audio.isVolumeFixed

    init {
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED)
            addAction(MUTE_CHANGED)
        }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = refresh()
            },
            filter,
        )
    }

    override fun up() = set(state.value.level + 1)

    override fun down() = set(state.value.level - 1)

    override fun set(level: Int) {
        val target = level.coerceIn(0, max)
        try {
            if (audio.isStreamMute(stream)) audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            audio.setStreamVolume(stream, target, 0)
        } catch (e: SecurityException) {
            // Do-not-disturb can forbid volume changes; the level simply stays where it is.
        }
        refresh()
    }

    override fun toggleMute() {
        val direction = if (audio.isStreamMute(stream)) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
        try {
            audio.adjustStreamVolume(stream, direction, 0)
        } catch (e: SecurityException) {
            // As above.
        }
        refresh()
    }

    override fun refresh() {
        state.value = read()
    }

    private fun read(): VolumeUi {
        val muted = audio.isStreamMute(stream)
        if (!muted) lastAudible = audio.getStreamVolume(stream)
        return VolumeUi(level = lastAudible, muted = muted)
    }

    private companion object {
        // Sent by Android's audio service on every volume or mute change. The names are stable but are
        // not public constants.
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"
    }
}
