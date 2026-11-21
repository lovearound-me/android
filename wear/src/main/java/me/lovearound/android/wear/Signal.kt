package me.lovearound.android.wear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import me.lovearound.android.Emoji
import me.lovearound.android.tr
import kotlin.math.roundToInt

/**
 * An incoming heart: the screen's edge glows in my color, hearts float up,
 * and in the middle a heart says someone is thinking of you. Never who.
 * A tap closes it.
 */
@Composable
fun SignalOverlay(color: Color, seed: Int, onClose: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "glow")
    val bright by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bright")
    val round = LocalConfiguration.current.isScreenRound
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose),
    ) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize().alpha(bright)) {
            val r = size.minDimension / 2
            if (round) {
                drawCircle(Brush.radialGradient(
                    0.72f to Color.Transparent, 0.9f to color.copy(alpha = 0.55f), 1f to color,
                    center = center, radius = r), r, center)
            } else {
                val band = size.minDimension * 0.12f
                drawRect(Brush.verticalGradient(listOf(color, Color.Transparent), 0f, band))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, color), size.height - band, size.height))
                drawRect(Brush.horizontalGradient(listOf(color, Color.Transparent), 0f, band))
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color), size.width - band, size.width))
            }
        }
        // Eight: a few hearts that each get seen, not a crowd.
        for (i in 0 until 8) {
            val v = i * 0.618034f + (seed % 100) / 100f
            val f = v - kotlin.math.floor(v)
            FloatingHeart(
                char = Emoji.pink[(seed + i * 7) % Emoji.pink.size],
                x = w * (0.14f + 0.72f * f), size = (15 + (i * 5) % 6).dp,
                delayMs = i * 320L, height = h, key = seed,
            )
        }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Favorite, null, tint = color, modifier = Modifier.size(40.dp))
            Text(tr("Кто-то думает о тебе"), textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    shadow = Shadow(Color.Black, blurRadius = 8f)))
        }
    }
}

@Composable
private fun FloatingHeart(char: String, x: Dp, size: Dp, delayMs: Long, height: Dp, key: Int) {
    val up = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs)
        up.animateTo(1f, tween(3400, easing = LinearOutSlowInEasing))
    }
    val density = LocalDensity.current
    val y = (height + size) - (height + size * 2) * up.value
    Text(char, fontSize = with(density) { size.toSp() },
        modifier = Modifier
            .offset { IntOffset(with(density) { (x - size / 2).toPx() }.roundToInt(), with(density) { (y - size / 2).toPx() }.roundToInt()) }
            .alpha(if (up.value == 0f) 0f else 1f - 0.85f * up.value))
}
