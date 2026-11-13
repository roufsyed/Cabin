package com.roufsyed.cabin.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.model.ClockUi
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * "Screen off": a black screen with a dim clock, for night driving. The whole screen is one button
 * that wakes it. The activity also turns the backlight right down while this is showing.
 */
@Composable
fun ScreenOffClock(clock: ClockUi, onWake: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pressable("Wake screen", onClick = onWake)
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(clock.time, 168, weight = Slate.Light, color = Slate.Disabled, lineHeight = 1f, tabular = true, singleLine = true)
        Txt("${clock.weekday} ${clock.dateLong}", 28, color = Slate.Disabled)
        Txt("Tap to wake", 24, color = Slate.Disabled)
    }
}
