package com.roufsyed.cabin.ui.chrome

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.model.ClockUi
import com.roufsyed.cabin.model.NowPlayingUi
import com.roufsyed.cabin.model.StatusUi
import com.roufsyed.cabin.ui.components.Panel
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.home.StatusIcons
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * The 1036 x 96 strip at the top of every screen but Home: time, date and status on the left and,
 * when [mini] is given, the mini player on the right.
 */
@Composable
fun TopStrip(clock: ClockUi, status: StatusUi, mini: (@Composable () -> Unit)?) {
    Row(
        modifier = Modifier.size(1036.dp, 96.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(
                clock.time, 84,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Time ${clock.time}" },
                weight = Slate.Light, lineHeight = 1f, tabular = true, letterSpacingEm = -0.01f, singleLine = true,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(clock.dateShort, 26, color = Slate.Muted, lineHeight = 1f, singleLine = true)
                StatusIcons(status)
            }
        }
        mini?.invoke()
    }
}

/** The 600 x 96 mini player: what is playing, which opens the Music screen, and the transport. */
@Composable
fun MiniPlayer(
    nowPlaying: NowPlayingUi,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Panel(Modifier.size(600.dp, 96.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .size(310.dp, 94.dp)
                    .pressable("Open music", onClick = onOpen)
                    .padding(start = 24.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SlateIcons.Bars, 34.dp, Slate.Accent, 1.75f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt(nowPlaying.title, 30, weight = Slate.SemiBold, lineHeight = 1.15f, ellipsis = true)
                    Txt(nowPlaying.artist, 24, color = Slate.Muted, ellipsis = true)
                }
            }
            Box(
                Modifier.size(96.dp, 94.dp).pressable("Previous", onClick = onPrevious),
                contentAlignment = Alignment.Center,
            ) { Icon(SlateIcons.Previous, 42.dp, Slate.Text, 1.5f) }
            Box(
                Modifier.size(96.dp, 94.dp).pressable("Play or pause", onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(76.dp).border(1.5.dp, Slate.Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (nowPlaying.playing) SlateIcons.Pause else SlateIcons.Play, 40.dp, Slate.Text, 1.75f)
                }
            }
            Box(
                Modifier.size(96.dp, 94.dp).pressable("Next", onClick = onNext),
                contentAlignment = Alignment.Center,
            ) { Icon(SlateIcons.Next, 42.dp, Slate.Text, 1.5f) }
        }
    }
}
