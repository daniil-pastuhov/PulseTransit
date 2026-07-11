package com.example.pulsetransit.ui.pixelshift

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.BurnInProtectionLevel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

fun Modifier.pixelShift(level: BurnInProtectionLevel): Modifier = composed {
    if (level == BurnInProtectionLevel.OFF) return@composed this

    var targetOffset by remember { mutableStateOf(Offset.Zero) }
    val animatedOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = tween(durationMillis = 5000), // Very slow 5-second transition
        label = "pixelShift"
    )

    LaunchedEffect(level) {
        while (true) {
            val maxOffsetDp = level.maxOffsetDp
            val xShiftDp = Random.nextFloat() * (maxOffsetDp * 2) - maxOffsetDp
            val yShiftDp = Random.nextFloat() * (maxOffsetDp * 2) - maxOffsetDp
            
            targetOffset = Offset(xShiftDp, yShiftDp)
            
            delay(60000.milliseconds) // Shift every minute
        }
    }

    this.offset { 
        IntOffset(
            x = animatedOffset.x.dp.toPx().roundToInt(),
            y = animatedOffset.y.dp.toPx().roundToInt()
        )
    }
}
