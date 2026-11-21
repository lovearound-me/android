package me.lovearound.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.lovearound.android.AppModel
import me.lovearound.android.Emoji
import me.lovearound.android.tr
import kotlin.math.roundToInt

/** The screen's edges in `color`: a wide soft band and a thin bright line. */
fun DrawScope.edgeGlow(color: Color, band: Float, strength: Float) {
    val w = size.width
    val h = size.height
    val c = color.copy(alpha = 0.8f * strength)
    val clear = color.copy(alpha = 0f)
    drawRect(Brush.verticalGradient(listOf(c, clear), 0f, band), Offset.Zero, Size(w, band))
    drawRect(Brush.verticalGradient(listOf(clear, c), h - band, h), Offset(0f, h - band), Size(w, band))
    drawRect(Brush.horizontalGradient(listOf(c, clear), 0f, band), Offset.Zero, Size(band, h))
    drawRect(Brush.horizontalGradient(listOf(clear, c), w - band, w), Offset(w - band, 0f), Size(band, h))
    val line = band / 8
    drawRect(color.copy(alpha = 0.95f * strength), Offset(line / 2, line / 2), Size(w - line, h - line), style = Stroke(line))
}

/** Spread across the width without clumping: golden-ratio steps. */
private fun fraction(i: Int, seed: Int): Float {
    val v = i * 0.618034 + (seed % 100) / 100.0
    return (v - Math.floor(v)).toFloat()
}

/**
 * A signal just went: the edges of the whole app glow in the person's color
 * and emoji float up, as on the Mac and the iPhone. Nothing to tap: the
 * circles stay usable under it.
 */
@Composable
fun SendGlow(out: AppModel.Outgoing) {
    val fade = remember(out.id) { Animatable(1f) }
    LaunchedEffect(out.id) {
        delay(500)
        fade.animateTo(0f, tween(2600, easing = LinearOutSlowInEasing))
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier
        .fillMaxSize()
        .semantics { contentDescription = out.text }) {
        val band = with(density) { 44.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) { edgeGlow(out.color, band, fade.value) }
        val set = if (out.stars) Emoji.stars else Emoji.pink
        for (i in 0 until 12) {
            FloatingEmoji(set[(out.seed + i * 7) % set.size], maxWidth * (0.08f + 0.84f * fraction(i, out.seed)),
                (24 + (i * 5) % 12).dp, i * 120L, maxHeight, 2600, out.id)
        }
    }
}

/**
 * An incoming signal: the screen edges glow in my color, eight hearts float
 * up, and in the middle a heart says someone is thinking of you. Never who.
 * A tap closes it; it fades by itself after a few seconds.
 */
@Composable
fun SignalOverlay(color: Color, seed: Int, onClose: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val bright by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bright")
    val density = LocalDensity.current
    val paper = C.paper
    BoxWithConstraints(Modifier
        .fillMaxSize()
        .background(paper)
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        .semantics { contentDescription = tr("Кто-то думает о тебе") }) {
        val band = with(density) { 48.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) { edgeGlow(color, band, bright) }
        for (i in 0 until 8) {
            FloatingEmoji(Emoji.pink[(seed + i * 7) % Emoji.pink.size], maxWidth * (0.1f + 0.8f * fraction(i, seed)),
                (24 + (i * 5) % 12).dp, i * 300L, maxHeight, 3800, seed.toLong())
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            // The emoji pass behind the heart and the words, not over them.
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(420.dp)) {
                    drawCircle(Brush.radialGradient(listOf(paper, paper.copy(alpha = 0.92f), paper.copy(alpha = 0f)),
                        center = center, radius = size.minDimension / 2))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Favorite, null, tint = color, modifier = Modifier.size(110.dp).scale(0.94f + 0.06f * bright))
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Кто-то думает о тебе"), color = C.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, lineHeight = 34.sp, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(tr("только что"), color = C.muted, fontSize = 15.sp)
                }
            }
        }
        Text(tr("Нажми, чтобы закрыть"), color = C.faint, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp))
    }
}

@Composable
fun FloatingEmoji(char: String, x: Dp, size: Dp, delayMs: Long, height: Dp, duration: Int, key: Long) {
    val up = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs)
        up.animateTo(1f, tween(duration, easing = LinearOutSlowInEasing))
    }
    val density = LocalDensity.current
    val y = (height + size) - (height + size * 2) * up.value
    Text(char, fontSize = with(density) { size.toSp() },
        modifier = Modifier
            .offset { IntOffset(with(density) { (x - size / 2).toPx() }.roundToInt(), with(density) { (y - size / 2).toPx() }.roundToInt()) }
            .alpha(if (up.value == 0f) 0f else 1f - 0.8f * up.value))
}
