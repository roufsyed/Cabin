package com.roufsyed.cabin.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.media.QueueItem
import com.roufsyed.cabin.model.NowPlayingUi
import com.roufsyed.cabin.ui.components.Panel
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.media.ProgressBlock
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * The Music screen: a remote control for whatever app is playing. On the left the track, its progress
 * and the transport; on the right the current item and the ones after it.
 */
@Composable
fun MusicScreen(
    now: NowPlayingUi,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueItem: (Long) -> Unit,
) {
    Row(Modifier.size(1036.dp, 488.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Panel(Modifier.size(480.dp, 488.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(now)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Txt(now.source, 24, color = Slate.Muted, ellipsis = true)
                        Txt(now.title, 38, weight = Slate.SemiBold, lineHeight = 1.1f, maxLines = 2)
                        Txt(now.artist, 26, color = Slate.Muted, ellipsis = true)
                    }
                }
                ProgressBlock(now.progress, gap = 12.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(96.dp).pressable("Previous", onClick = onPrevious),
                        contentAlignment = Alignment.Center,
                    ) { Icon(SlateIcons.Previous, 46.dp, Slate.Text, 1.5f) }
                    Box(
                        Modifier
                            .size(96.dp)
                            .pressable("Play or pause", onClick = onPlayPause)
                            .border(1.5.dp, Slate.Accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (now.playing) SlateIcons.Pause else SlateIcons.Play, 46.dp, Slate.Text, 1.75f) }
                    Box(
                        Modifier.size(96.dp).pressable("Next", onClick = onNext),
                        contentAlignment = Alignment.Center,
                    ) { Icon(SlateIcons.Next, 46.dp, Slate.Text, 1.5f) }
                }
            }
        }
        Panel(Modifier.size(543.dp, 488.dp)) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Txt(now.queueTitle, 24, color = Slate.Muted, ellipsis = true) }
                for (item in now.queue) QueueRow(item) { onQueueItem(item.id) }
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .pressable("Play ${item.title}", onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, top = 1.dp, end = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(
                    item.title, 28,
                    weight = Slate.SemiBold, color = if (item.current) Slate.Accent else Slate.Text, ellipsis = true,
                )
                Txt(item.subtitle, 24, color = Slate.Muted, ellipsis = true)
            }
            if (item.duration.isNotEmpty()) Txt(item.duration, 24, color = Slate.Muted, tabular = true)
        }
    }
}

/** The cover the playing app supplies, or the design's plain stand-in when it supplies none. */
@Composable
private fun Cover(now: NowPlayingUi) {
    val shape = RoundedCornerShape(14.dp)
    if (now.art != null) {
        Image(
            bitmap = now.art,
            contentDescription = "Cover art",
            modifier = Modifier.size(150.dp).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Canvas(
            Modifier
                .size(150.dp)
                .clip(shape)
                .semantics { contentDescription = "Cover art placeholder" },
        ) {
            // Drawn on the design's 232-unit square.
            val unit = size.width / 232f
            drawRect(Color(0xFF1D2A46))
            drawCircle(Color(0xFF35508C), radius = 104f * unit, center = Offset(76f * unit, 170f * unit))
            drawCircle(Color(0xFFF2B56B), radius = 38f * unit, center = Offset(168f * unit, 72f * unit))
        }
    }
}
