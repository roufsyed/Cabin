package com.roufsyed.cabin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.media.PlayerReport
import com.roufsyed.cabin.ui.components.Panel
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * One page of Players: what one of the unit's players tells Cabin about itself, as a table, or with
 * [report] null why there is nothing to show. What a unit's own players offer cannot be known until
 * they are seen running, and this is where they are seen; nothing on it can be changed.
 *
 * Fills the 1036 x 488 content area. The table has room for twelve lines of text, which is what
 * the rows of a report add up to at their longest.
 */
@Composable
fun PlayerPage(report: PlayerReport?, access: Boolean) {
    Panel(Modifier.size(1036.dp, 488.dp)) {
        if (report == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Txt(if (access) "No player is running" else "Cabin cannot see the players yet", 38, weight = Slate.Light)
                Txt(
                    if (access) "Start some music, then look here again." else "Allow notification access on Setup's first page.",
                    24, color = Slate.Muted,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (line in report.lines) {
                    Row(
                        // One thing to read out, and one for a test to find: "Length: 3:41".
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "${line.label}: ${line.value}"
                        },
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Txt(line.label, 24, Modifier.width(196.dp), color = Slate.Muted, singleLine = true)
                        Txt(line.value, 24, Modifier.weight(1f), maxLines = line.lines)
                    }
                }
            }
        }
    }
}
