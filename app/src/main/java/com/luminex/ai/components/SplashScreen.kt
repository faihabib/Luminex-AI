package com.luminex.ai.components

import com.luminex.ai.R
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SplashFont = FontFamily(Font(R.font.luminex_script))

@Composable
fun SplashScreen() {
    val fullText = "Luminex"
    val haptic = LocalHapticFeedback.current
    var typedCount by remember { mutableStateOf(0) }

    // Typing animation + haptic tick for every letter
    LaunchedEffect(Unit) {
        delay(300)
        for (i in 1..fullText.length) {
            typedCount = i
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(160)
        }
        // Firmer buzz when the word is complete
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Blinking cursor that keeps running
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "blink"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            // Invisible full word keeps the layout steady while typing
            Text(
                text = fullText,
                color = Color.Transparent,
                fontSize = 56.sp,
                fontFamily = SplashFont,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Normal,
                letterSpacing = 0.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fullText.take(typedCount),
                    color = Color.White,
                    fontSize = 56.sp,
                    fontFamily = SplashFont,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Normal,
                    letterSpacing = 0.sp
                )
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(2.dp)
                        .height(52.dp)
                        .alpha(cursorAlpha)
                        .background(Color.White)
                )
            }
        }
    }
}
