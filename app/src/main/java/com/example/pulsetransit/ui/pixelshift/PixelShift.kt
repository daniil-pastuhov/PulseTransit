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

fun Modifier.pixelShift(level: BurnInProtectionLevel): Modifier =
    composed {
        if (level == BurnInProtectionLevel.OFF) return@composed this

        var targetOffset by remember { mutableStateOf(Offset.Zero) }
        val animatedOffset by animateOffsetAsState(
            targetValue = targetOffset,
            animationSpec = tween(durationMillis = PixelShiftSchedule.transition.inWholeMilliseconds.toInt()),
            label = "pixelShift",
        )

        LaunchedEffect(level) {
            while (true) {
                val next = PixelShiftSchedule.nextOffset(level)
                targetOffset = Offset(next.xDp, next.yDp)
                delay(PixelShiftSchedule.interval)
            }
        }

        this.offset {
            IntOffset(
                x =
                    animatedOffset.x.dp
                        .toPx()
                        .roundToInt(),
                y =
                    animatedOffset.y.dp
                        .toPx()
                        .roundToInt(),
            )
        }
    }
