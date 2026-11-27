package com.roufsyed.cabin.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.roufsyed.cabin.ui.media.CoverArt
import com.roufsyed.cabin.ui.media.NoCover
import com.roufsyed.cabin.ui.media.ProgressRing
import com.roufsyed.cabin.model.ClockUi
import com.roufsyed.cabin.model.HomePanel
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.model.NowPlayingUi
import com.roufsyed.cabin.model.StatusUi
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * The three Home panels: 328 x 600 each, 26 apart, filling the 1036 x 600 content area. [order] is
 * which panel has which place, from left to right; the design's is clock, navigation, now playing.
 */
@Composable
fun HomePanels(
    order: List<HomePanel>,
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
        for (panel in order) {
            when (panel) {
                HomePanel.Clock -> ClockPanel(clock, status)
                HomePanel.Navigation -> NavigationPanel(nav, onOpenMaps, onWhereTo, onNavigateHome, onNavigateOffice)
                HomePanel.NowPlaying -> NowPlayingPanel(nowPlaying, onOpenNowPlaying, onPrevious, onPlayPause, onNext)
            }
        }
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
                Txt(clock.weekday, 24, Modifier.weight(1f).padding(end = 4.dp), color = Slate.Muted, ellipsis = true)
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

/**
 * Phone, Wi-Fi, hotspot and GPS marks. A mark that is off is a faded grey. [on] and [off] are
 * the design's two greys unless the marks sit somewhere with greys of its own.
 */
@Composable
fun StatusIcons(status: StatusUi, on: Color = Slate.Connected, off: Color = Slate.Disabled) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = status.description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SlateIcons.StatusBluetooth, 26.dp, if (status.phone) on else off, 1.5f)
        Icon(SlateIcons.StatusWifi, 26.dp, if (status.wifi) on else off, 1.5f)
        Icon(SlateIcons.StatusHotspot, 26.dp, if (status.hotspot) on else off, 1.5f)
        Icon(SlateIcons.StatusGps, 26.dp, if (status.gps) on else off, 1.5f)
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
                    Txt(nav.road, 34, color = Slate.TextSecondary, maxLines = 3)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(nav.arrive, 26, weight = Slate.SemiBold, ellipsis = true)
                    Txt(nav.remaining, 26, color = Slate.Muted, ellipsis = true)
                }
            }

            NavUi.Idle -> Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .size(326.dp, 374.dp)
                        .pressable("Open Maps", onClick = onOpenMaps)
                        .padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Txt("Navigation", 24, color = Slate.Muted)
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .requiredSize(108.dp)
                                    .pressable("Where to?", onClick = onWhereTo),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .border(1.5.dp, Slate.Accent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(SlateIcons.Mic, 42.dp, Slate.Text, 1.4f)
                                }
                            }
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
                Modifier
                    .size(326.dp, 483.dp)
                    .pressable("Open music", onClick = onOpen)
                    .padding(start = 32.dp, top = 28.dp, end = 32.dp),
            ) {
                Txt(nowPlaying.caption, 24, color = Slate.Muted)
                Spacer(Modifier.height(30.dp))
                // The playing track's own picture, where its player supplies one, and a record drawn
                // in its place where it does not: nothing moves when a picture comes and goes.
                if (nowPlaying.art != null) {
                    CoverArt(nowPlaying.art, 262.dp, 12.dp)
                } else {
                    NoCover(262.dp, 12.dp)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Txt(nowPlaying.title, 32, weight = Slate.SemiBold, lineHeight = 1.1f, maxLines = 2)
                        if (nowPlaying.artist.isNotBlank()) {
                            Txt(nowPlaying.artist, 24, color = Slate.Muted, ellipsis = !nowPlaying.needsAccess, maxLines = 2)
                        }
                    }
                }
            }
            Row(Modifier.size(326.dp, 115.dp)) {
                val skip = if (nowPlaying.canSkip) Slate.Text else Slate.Disabled
                Box(
                    Modifier
                        .size(113.dp, 115.dp)
                        .pressable("Previous", enabled = nowPlaying.canSkip, onClick = onPrevious)
                        .padding(start = 13.dp, bottom = 21.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(SlateIcons.Previous, 44.dp, skip, 1.5f) }
                Box(
                    Modifier
                        .size(100.dp, 115.dp)
                        .pressable("Play or pause", onClick = onPlayPause)
                        .padding(bottom = 21.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ProgressRing(nowPlaying.progress, Modifier.size(94.dp))
                    Icon(if (nowPlaying.playing) SlateIcons.Pause else SlateIcons.Play, 40.dp, Slate.Text, 1.75f)
                }
                Box(
                    Modifier
                        .size(113.dp, 115.dp)
                        .pressable("Next", enabled = nowPlaying.canSkip, onClick = onNext)
                        .padding(end = 13.dp, bottom = 21.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(SlateIcons.Next, 44.dp, skip, 1.5f) }
            }
        }
    }
}
