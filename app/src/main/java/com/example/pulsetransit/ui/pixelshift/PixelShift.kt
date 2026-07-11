package com.example.pulsetransit.ui.pixelshift

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.BurnInProtectionLevel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

fun Modifier.pixelShift(level: BurnInProtectionLevel): Modifier = composed {
    if (level == BurnInProtectionLevel.OFF) return@composed this

    val density = LocalDensity.current
    var offset by remember { mutableStateOf(IntOffset.Zero) }

    LaunchedEffect(level) {
        while (true) {
            delay(30000) // Shift every 30 seconds
            val maxOffsetDp = level.maxOffsetDp
            val xShiftDp = Random.nextFloat() * (maxOffsetDp * 2) - maxOffsetDp
            val yShiftDp = Random.nextFloat() * (maxOffsetDp * 2) - maxOffsetDp
            
            val xPx = with(density) { xShiftDp.dp.toPx().roundToInt() }
            val yPx = with(density) { yShiftDp.dp.toPx().roundToInt() }
            
            offset = IntOffset(xPx, yPx)
        }
    }

    this.offset { offset }
}
