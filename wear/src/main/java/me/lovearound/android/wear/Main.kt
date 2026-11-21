package me.lovearound.android.wear

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.withTimeoutOrNull
import me.lovearound.android.Palette
import me.lovearound.android.tr
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The people, the way Settings → «Вид» says: honeycomb bubbles (the
 * default), circles, circles with names, or a list. The first is always
 * «Кому-нибудь на свете», then the close people, then everyone else.
 * A tap sends. A held finger shows the whole name: let go and it goes,
 * slide away and it doesn't.
 */
@Composable
fun MainScreen(model: WatchModel, openSettings: () -> Unit) {
    LaunchedEffect(Unit) { model.refresh() }
    Box(Modifier.fillMaxSize()) {
        when (model.layout) {
            WatchLayout.Hive -> HiveView(model, openSettings)
            WatchLayout.Circles -> GridView(model, named = false, openSettings)
            WatchLayout.Named -> GridView(model, named = true, openSettings)
            WatchLayout.List -> ListView(model, openSettings)
        }
        // What just happened, over the bottom edge: seen wherever the list is scrolled.
        AnimatedVisibility(model.caption != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, start = 22.dp, end = 22.dp)) {
            Text(model.caption ?: "", fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
        }
        AnimatedVisibility(model.peeking != null, enter = fadeIn(), exit = fadeOut()) {
            model.peeking?.let { PeekView(model, it) }
        }
    }
}

val Target.color: Color get() = when (this) {
    Target.Anyone -> W.star
    is Target.Person -> Palette.color(contact.color)
}

/** «О ком думаешь?» over the people. */
@Composable
private fun Heading() {
    Text(tr("О ком думаешь?"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth())
}

/** Under the people: why there are none, and the way to Settings. */
@Composable
private fun Footer(model: WatchModel, openSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (model.sendingOff) {
            Text(tr("Отправка с часов выключена"), fontSize = 13.sp, color = W.muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp))
        } else if (model.loaded && model.contacts.isEmpty()) {
            Text(tr("Пока никого нет"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Text(tr("Добавь людей в Love Around на телефоне"), fontSize = 12.sp, color = W.muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        Box(Modifier.padding(top = 10.dp).size(44.dp).background(W.card, CircleShape).clickable(onClick = openSettings),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Settings, tr("Настройки"), tint = W.muted, modifier = Modifier.size(22.dp))
        }
    }
}

// Tap and hold

/**
 * A tap sends at once. Held for a moment, the whole name shows over the
 * screen (PeekView); letting go sends, sliding the finger away doesn't.
 */
fun Modifier.sends(model: WatchModel, target: Target): Modifier = pointerInput(target) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        model.pressStarted()
        var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
        val quick = withTimeoutOrNull(450) { up = waitForUpOrCancellation(); true } != null
        if (quick) {
            // Up within the moment: a tap. Cancelled: the list scrolled.
            up?.let { it.consume(); model.send(target) }
            return@awaitEachGesture
        }
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                model.released(target)
                break
            }
            // Held: the list doesn't scroll under the finger.
            change.consume()
            model.holding(target, (change.position - down.position).getDistance() / density)
        }
    }
}

/** The held one: big, with the whole name. */
@Composable
fun PeekView(model: WatchModel, target: Target) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))
        // If the finger's own gesture was lost: a tap here sends, a swipe cancels.
        .pointerInput(Unit) { detectDragGestures(onDragEnd = { model.cancelPeek() }) { _, _ -> } }
        .clickable { model.sendPeeked() },
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 18.dp)) {
            Mark(target, 66.dp, sent = false, filled = false)
            Text(target.label, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            Text(tr("Отпусти, и улетит. Смахни, чтобы отменить."), fontSize = 12.sp, color = W.muted, textAlign = TextAlign.Center)
        }
    }
}

// The marks

/**
 * A person's circle or bubble: the first letter in their color, a heart for
 * a moment once sent. «Кому-нибудь на свете» has sparkles in gold.
 * `filled`: like an app's icon (bubbles, list); else a ring around a dark fill.
 */
@Composable
fun Mark(target: Target, size: Dp, sent: Boolean, filled: Boolean, modifier: Modifier = Modifier) {
    val color = target.color
    val fill = when {
        filled -> Modifier.background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color)), CircleShape)
        sent -> Modifier.background(color, CircleShape)
        else -> Modifier.background(color.copy(alpha = 0.18f), CircleShape)
    }
    val ring = when {
        !filled -> Modifier.border(maxOf(2.dp, size * 0.06f), color, CircleShape)
        sent -> Modifier.border(maxOf(2.dp, size * 0.06f), Color.White, CircleShape)
        else -> Modifier
    }
    Box(modifier.size(size).then(fill).then(ring), contentAlignment = Alignment.Center) {
        when {
            sent -> Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
            target is Target.Anyone -> Icon(Icons.Filled.AutoAwesome, null, tint = if (filled) Color.White else color,
                modifier = Modifier.size(size * 0.42f))
            else -> Text(target.initial, color = Color.White, fontWeight = FontWeight.SemiBold,
                fontSize = with(LocalDensity.current) { (size * 0.4f).toSp() })
        }
    }
}

// Соты

/** 3, 4, 3, 4…, the star in the middle of the first row. */
fun hiveRows(targets: List<Target>): List<List<Target>> {
    val rows = mutableListOf<MutableList<Target>>()
    var rest = targets
    while (rest.isNotEmpty()) {
        val n = if (rows.size % 2 == 0) 3 else 4
        rows.add(rest.take(n).toMutableList())
        rest = rest.drop(n)
    }
    rows.firstOrNull()?.takeIf { it.size >= 2 }?.let { val a = it[0]; it[0] = it[1]; it[1] = a }
    return rows
}

/**
 * Full size near the middle of the screen, down to under half at the round
 * edge: sooner than on the square Apple Watch.
 */
fun hiveScale(cx: Float, cy: Float, width: Float, height: Float): Float {
    val dx = cx - width / 2
    val dy = (cy - height / 2) * 0.85f
    val far = sqrt(dx * dx + dy * dy) - width * 0.22f
    return (1 - far / (width * 0.45f)).coerceIn(0.45f, 1f)
}

/**
 * Bubbles in rows of three and four, offset like a honeycomb; the ones
 * towards the screen's edges shrink, as on the watch's own app list.
 */
@Composable
private fun HiveView(model: WatchModel, openSettings: () -> Unit) {
    val config = LocalConfiguration.current
    val width = config.screenWidthDp.dp
    val height = config.screenHeightDp.dp
    val size = floor(width.value / 4.5f).dp
    val gap = size * 0.1f
    val pitch = size * 0.95f
    val top = height * 0.13f + 26.dp
    val rows = hiveRows(model.targets)
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    ScreenScaffold(scrollState = scroll) {
        Column(Modifier.fillMaxSize()
            .rotaryScrollable(RotaryScrollableDefaults.behavior(scroll), focus)
            .verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(top + pitch * rows.size + size * 0.1f)) {
                Box(Modifier.padding(top = height * 0.13f)) { Heading() }
                rows.forEachIndexed { r, row ->
                    val rowWidth = size * row.size + gap * (row.size - 1)
                    row.forEachIndexed { i, target ->
                        val x = (width - rowWidth) / 2 + (size + gap) * i
                        val y = top + pitch * r
                        val cx = with(density) { (x + size / 2).toPx() }
                        val cy = with(density) { (y + size / 2).toPx() }
                        Mark(target, size, sent = model.justSent == target.id, filled = true,
                            modifier = Modifier
                                .offset(x, y)
                                .graphicsLayer {
                                    val s = hiveScale(cx, cy - scroll.value, widthPx, heightPx)
                                    scaleX = s; scaleY = s
                                }
                                .sends(model, target))
                    }
                }
            }
            Footer(model, openSettings)
            // Room for the last rows to come to the middle, where they're biggest.
            Spacer(Modifier.height(height * 0.3f))
        }
    }
}

// Кружочки

@Composable
private fun GridView(model: WatchModel, named: Boolean, openSettings: () -> Unit) {
    val state = rememberScalingLazyListState()
    val size = if (named) 42.dp else 46.dp
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (named) 4.dp else 6.dp)) {
            item { Heading() }
            model.targets.chunked(3).forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()) {
                        row.forEach { target ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(size + 12.dp).sends(model, target)) {
                                Mark(target, size, sent = model.justSent == target.id, filled = false)
                                if (named) Text(target.short, fontSize = 11.sp, color = W.muted, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            item { Footer(model, openSettings) }
        }
    }
}

// Список

@Composable
private fun ListView(model: WatchModel, openSettings: () -> Unit) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            item { Heading() }
            model.targets.forEach { target ->
                item(key = target.id) {
                    val sent = model.justSent == target.id
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth()
                            .background(if (sent) target.color.copy(alpha = 0.4f) else W.card, RoundedCornerShape(22.dp))
                            .sends(model, target)
                            .padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Mark(target, 28.dp, sent = false, filled = true)
                        Text(target.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (sent) Icon(Icons.Filled.Favorite, null, tint = W.link, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { Footer(model, openSettings) }
        }
    }
}
