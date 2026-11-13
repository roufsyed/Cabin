package com.roufsyed.cabin.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import com.roufsyed.cabin.model.ClockUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

interface ClockSource {
    val clock: StateFlow<ClockUi>
}

/** A clock that never moves: for the demo launch and for tests. */
class FixedClock(value: ClockUi) : ClockSource {
    override val clock: StateFlow<ClockUi> = MutableStateFlow(value)
}

/**
 * The wall clock. It changes only when the system says the minute, the time or the zone has changed,
 * so nothing polls and nothing runs between minutes.
 */
class AndroidClock(private val context: Context) : ClockSource {
    private val state = MutableStateFlow(read())
    override val clock: StateFlow<ClockUi> = state

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    state.value = read()
                }
            },
            filter,
        )
    }

    private fun read(): ClockUi =
        clockUi(LocalDateTime.now(), DateFormat.is24HourFormat(context), Locale.getDefault())
}

/** Formats [now] the way the design shows it: "Thursday", "10", "10", "12 November", "Thu 12 Nov". */
fun clockUi(now: LocalDateTime, is24Hour: Boolean, locale: Locale): ClockUi {
    val hour = if (is24Hour) {
        "%02d".format(Locale.ROOT, now.hour)
    } else {
        (now.hour % 12).let { if (it == 0) 12 else it }.toString()
    }
    return ClockUi(
        weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
        hour = hour,
        minute = "%02d".format(Locale.ROOT, now.minute),
        dateLong = "${now.dayOfMonth} ${now.month.getDisplayName(TextStyle.FULL, locale)}",
        dateShort = "${now.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${now.dayOfMonth} " +
            now.month.getDisplayName(TextStyle.SHORT, locale),
    )
}
