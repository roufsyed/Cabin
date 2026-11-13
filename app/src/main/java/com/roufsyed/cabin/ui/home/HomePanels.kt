package com.roufsyed.cabin.ui.home

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.Panel
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.media.ProgressBlock
import com.roufsyed.cabin.model.ClockUi
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.model.NowPlayingUi
import com.roufsyed.cabin.model.StatusUi
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/** The three Home panels: 328 x 600 each, 26 apart, filling the 1036 x 600 content area. */
@Composable
fun HomePanels(
    clock: ClockUi,
    status: StatusUi,
    nav: NavUi,
    nowPlaying: NowPlayingUi,
    onOpenMaps: () -> Unit,
    onWhereTo: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateOffice: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        ClockPanel(clock, status)
        NavigationPanel(nav, onOpenMaps, onWhereTo, onNavigateHome, onNavigateOffice)
        NowPlayingPanel(nowPlaying, onOpenNowPlaying, onPrevious, onPlayPause, onNext)
    }
}

@Composable
private fun ClockPanel(clock: ClockUi, status: StatusUi) {
    Panel(Modifier.size(328.dp, 600.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(29.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(clock.weekday, 24, color = Slate.Muted)
                StatusIcons(status)
            }
            Column(Modifier.clearAndSetSemantics { contentDescription = "Time ${clock.time}" }) {
                Txt(clock.hour, 192, weight = Slate.Light, lineHeight = 0.88f, tabular = true, letterSpacingEm = -0.02f)
                Txt(
                    clock.minute, 192, weight = Slate.Light, color = Slate.Accent, lineHeight = 0.88f,
                    tabular = true, letterSpacingEm = -0.02f,
                )
            }
            Txt(clock.dateLong, 26)
        }
    }
}

/** Phone, Wi-Fi and GPS marks. A mark that is off is dimmer and struck through. */
@Composable
fun StatusIcons(status: StatusUi) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = status.description },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (status.phone) SlateIcons.StatusPhone else SlateIcons.StatusPhoneOff, 26.dp,
            if (status.phone) Slate.Muted else Slate.Disabled, 1.5f,
        )
        Icon(
            if (status.wifi) SlateIcons.StatusWifi else SlateIcons.StatusWifiOff, 26.dp,
            if (status.wifi) Slate.Muted else Slate.Disabled, 1.5f,
        )
        Icon(
            if (status.gps) SlateIcons.StatusGps else SlateIcons.StatusGpsOff, 26.dp,
            if (status.gps) Slate.Muted else Slate.Disabled, 1.5f,
        )
    }
}

@Composable
private fun NavigationPanel(
    nav: NavUi,
    onOpenMaps: () -> Unit,
    onWhereTo: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateOffice: () -> Unit,
) {
    Panel(Modifier.size(328.dp, 600.dp)) {
        when (nav) {
            is NavUi.Active -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pressable("Open Maps", onClick = onOpenMaps)
                    .padding(horizontal = 32.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Txt("Navigation", 24, color = Slate.Muted)
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (nav.glyph != null) {
                        // The maps app's own manoeuvre arrow, in the accent, at the bottom left of the
                        // space the design gives its arrow.
                        Image(
                            bitmap = nav.glyph,
                            contentDescription = null,
                            modifier = Modifier.size(84.dp, 108.dp),
                            alignment = Alignment.BottomStart,
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(Slate.Accent),
                        )
                    } else {
                        Icon(SlateIcons.TurnRight, 84.dp, Slate.Accent, 0.5f)
                    }
                    Txt(nav.distance, 76, weight = Slate.Light, lineHeight = 1f, singleLine = true)
                    Txt(nav.road, 34, color = Slate.TextSecondary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(nav.arrive, 26, weight = Slate.SemiBold)
                    Txt(nav.remaining, 26, color = Slate.Muted)
                }
            }

            NavUi.Idle -> Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .size(326.dp, 374.dp)
                        .pressable("Where to?", onClick = onWhereTo)
                        .padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Txt("Navigation", 24, color = Slate.Muted)
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .border(1.5.dp, Slate.Accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(SlateIcons.Mic, 42.dp, Slate.Text, 1.4f)
                        }
                        Txt("Where to?", 50, weight = Slate.Light, lineHeight = 1.1f)
                    }
                }
                Shortcut("Navigate home", "Home", SlateIcons.House, onNavigateHome)
                Shortcut("Navigate to office", "Office", SlateIcons.Briefcase, onNavigateOffice)
            }
        }
    }
}

@Composable
private fun Shortcut(
    description: String,
    label: String,
    icon: com.roufsyed.cabin.ui.icons.SlateIcon,
    onClick: () -> Unit,
) {
    Box(Modifier.size(326.dp, 112.dp).pressable(description, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, top = 1.dp, end = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, 38.dp, Slate.Ink, 1.3f)
            Txt(label, 32, modifier = Modifier.weight(1f))
            Icon(SlateIcons.ChevronRight, 28.dp, Slate.Muted, 1.4f)
        }
    }
}

@Composable
private fun NowPlayingPanel(
    nowPlaying: NowPlayingUi,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Panel(Modifier.size(328.dp, 600.dp)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .size(326.dp, 470.dp)
                    .pressable("Open music", onClick = onOpen)
                    .padding(start = 32.dp, top = 28.dp, end = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Txt(nowPlaying.caption, 24, color = Slate.Muted)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(nowPlaying.title, 46, weight = Slate.SemiBold, lineHeight = 1.1f, maxLines = 3)
                    Txt(nowPlaying.artist, 28, color = Slate.Muted, maxLines = 2)
                }
                ProgressBlock(nowPlaying.progress, gap = 14.dp)
            }
            Row(Modifier.size(326.dp, 128.dp)) {
                Box(
                    Modifier.size(108.dp, 128.dp).pressable("Previous", onClick = onPrevious),
                    contentAlignment = Alignment.Center,
                ) { Icon(SlateIcons.Previous, 44.dp, Slate.Text, 1.5f) }
                Box(
                    Modifier.size(110.dp, 128.dp).pressable("Play or pause", onClick = onPlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(84.dp).border(1.5.dp, Slate.Accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (nowPlaying.playing) SlateIcons.Pause else SlateIcons.Play, 44.dp, Slate.Text, 1.75f)
                    }
                }
                Box(
                    Modifier.size(108.dp, 128.dp).pressable("Next", onClick = onNext),
                    contentAlignment = Alignment.Center,
                ) { Icon(SlateIcons.Next, 44.dp, Slate.Text, 1.5f) }
            }
        }
    }
}
