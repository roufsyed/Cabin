package com.roufsyed.cabin.ui.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.apps.SplitChoices
import com.roufsyed.cabin.gesture.Gesture
import com.roufsyed.cabin.gesture.cabinGestures
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.ui.apps.GlyphIcon
import com.roufsyed.cabin.ui.apps.GridTile
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

private val ButtonShape = RoundedCornerShape(20.dp)

/**
 * The chooser for a second app, drawn to fit whatever part of the screen it is given: half of it,
 * beside the app that was in front when Split was pressed. The same tiles as the Apps grid, as many
 * across and down as fit, with further pages a swipe away.
 *
 * [barRoom] is left clear on the right, where the side bar lies over this half's edge.
 */
@Composable
fun SplitChooser(
    apps: List<AppEntry>,
    barRoom: Dp,
    onPick: (AppEntry) -> Unit,
    onCancel: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Slate.Ground)
            // 16 all round: with the heading that leaves room for four rows on a screen 800 high.
            .padding(start = 16.dp, top = 16.dp, end = 16.dp + barRoom, bottom = 16.dp),
    ) {
        // Inside Cabin one dp is one design pixel, so these are the design's numbers.
        val grid = SplitChoices.grid(maxWidth.value, maxHeight.value - 96f - 16f)
        val pages = grid.pages(apps.size)
        val shown = page.coerceIn(0, pages - 1)
        val tiles = grid.page(apps, shown)
        Column(
            modifier = Modifier.fillMaxSize().cabinGestures { gesture ->
                when (gesture) {
                    Gesture.SwipeLeft -> page = (shown + 1).coerceAtMost(pages - 1)
                    Gesture.SwipeRight -> page = (shown - 1).coerceAtLeast(0)
                    else -> Unit
                }
            },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).padding(start = 4.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Txt("Split screen", 38, weight = Slate.Light, lineHeight = 1f, ellipsis = true)
                    Txt(
                        if (pages > 1) "Page ${shown + 1} of $pages. Swipe for more." else "Tap the app to show here",
                        24, color = Slate.Muted, lineHeight = 1f, ellipsis = true,
                    )
                }
                Box(
                    Modifier
                        .size(150.dp, 96.dp)
                        .pressable("Cancel split screen", onClick = onCancel)
                        .background(Slate.Text, ButtonShape),
                    contentAlignment = Alignment.Center,
                ) { Txt("Back", 26, weight = Slate.SemiBold, color = Slate.Ground, lineHeight = 1f) }
            }
            for (row in 0 until grid.rows) {
                Row(
                    Modifier.fillMaxWidth().height(SplitChoices.TILE_HEIGHT.dp),
                    horizontalArrangement = Arrangement.spacedBy(SplitChoices.GAP.dp),
                ) {
                    for (column in 0 until grid.columns) {
                        val app = tiles.getOrNull(row * grid.columns + column)
                        if (app == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            GridTile(
                                label = app.label,
                                dimmed = false,
                                outlined = false,
                                onClick = { onPick(app) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) { GlyphIcon(app, 46.dp) }
                        }
                    }
                }
            }
        }
    }
}
