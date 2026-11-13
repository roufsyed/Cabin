package com.roufsyed.cabin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The fixed frame of every Cabin screen: a 40 px margin, a 1036-wide main column (600 of [content]
 * above the 96-high [bottomRow], 24 apart) and, 32 to its right, the 132 x 720 [rail].
 */
@Composable
fun CabinFrame(
    content: @Composable () -> Unit,
    bottomRow: @Composable () -> Unit,
    rail: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(Modifier.width(1036.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(1036.dp, 600.dp)) { content() }
            bottomRow()
        }
        rail()
    }
}
