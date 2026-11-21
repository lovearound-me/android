package me.lovearound.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.lovearound.android.Contact
import me.lovearound.android.Palette
import me.lovearound.android.tr

/** A button that gives a little under the finger. */
@Composable
fun Pressable(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, label: String? = null,
              content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(), label = "press")
    Box(modifier
        .scale(scale)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)) {
        content()
    }
}

/** The wide rounded buttons of the sign-in screen and the pages. */
@Composable
fun WideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, enabled: Boolean = true,
               icon: ImageVector? = null, iconTint: Color? = null, painter: androidx.compose.ui.graphics.painter.Painter? = null) {
    val shape = RoundedCornerShape(14.dp)
    Row(modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clip(shape)
        .background(if (filled) C.link else C.well)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (painter != null) {
            Icon(painter, null, tint = iconTint ?: Color.Unspecified, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = iconTint ?: if (filled) C.onLink else C.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (filled) C.onLink else C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
    }
}

/**
 * A person's circle: their color as a ring and a tint, the initial inside;
 * filled with a heart for a moment once a signal went.
 */
@Composable
fun PersonCircle(contact: Contact, sent: Boolean = false, size: Dp = 76.dp, named: Boolean = true) {
    val color = Palette.color(contact.color)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (sent) color else color.copy(alpha = 0.2f))
            .border(3.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            if (sent) {
                Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
            } else {
                Text(initial(contact.label), color = C.ink, fontSize = (size.value * 0.37f).sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (named) {
            Spacer(Modifier.size(7.dp))
            Text(contact.label, color = C.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun initial(label: String): String = label.trim().firstOrNull()?.uppercase() ?: "♥"

/** A dashed circle with a symbol: «＋ Добавить». */
@Composable
fun DashedCircle(size: Dp, content: @Composable () -> Unit) {
    val faint = C.faint
    Box(Modifier
        .size(size)
        .drawBehind {
            val stroke = 2.5.dp.toPx()
            drawCircle(faint.copy(alpha = 0.75f), radius = this.size.minDimension / 2 - stroke / 2,
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
        }, contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Banner(text: String, icon: ImageVector, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(C.well)
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Text(action, color = C.link, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(4.dp))
        }
    }
}

/** A grouped block of rows, as on a device card: a header, the rows, a footer. */
@Composable
fun Group(header: String? = null, footer: String? = null, footerColor: Color? = null, background: Color? = null,
          content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (header != null) {
            Text(header, color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        }
        Column(Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background ?: C.field)) {
            content()
        }
        if (footer != null) {
            Text(footer, color = footerColor ?: C.muted, fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

@Composable
fun RowDivider() = HorizontalDivider(color = C.ink.copy(alpha = 0.08f), modifier = Modifier.padding(start = 16.dp))

/** A row in a group: tappable, with whatever goes in it. */
@Composable
fun GroupRow(onClick: (() -> Unit)? = null, enabled: Boolean = true, padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
             content: @Composable RowScope.() -> Unit) {
    Row(Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
        .padding(padding),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

/** A row with a switch. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null, enabled: Boolean = true,
              tint: Color? = null) {
    GroupRow(onClick = { onChange(!checked) }, enabled = enabled) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) C.ink else C.faint, fontSize = 16.sp)
            if (sub != null) Text(sub, color = C.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(
            checkedTrackColor = tint ?: C.ok, checkedThumbColor = Color.White,
            uncheckedTrackColor = C.well, uncheckedBorderColor = C.faint.copy(alpha = 0.5f), uncheckedThumbColor = C.faint))
    }
}

/** A row like a device card's: a tinted symbol, a title, what it is now. */
@Composable
fun SettingsRow(icon: ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit, enabled: Boolean = true,
                trailing: @Composable (() -> Unit)? = null) {
    GroupRow(onClick = onClick, enabled = enabled) {
        Box(Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.ink, fontSize = 16.sp)
            Text(sub, color = C.muted, fontSize = 13.sp)
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.faint)
    }
}

/** The twelve colors in two rows of six. */
@Composable
fun ColorSwatches(selection: Int, onSelect: (Int) -> Unit, enabled: Boolean = true) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0..1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0..5) {
                    val index = row * 6 + col
                    val entry = Palette.all[index]
                    Box(Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(entry.color)
                        .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(index) }
                        .semantics { contentDescription = entry.name },
                        contentAlignment = Alignment.Center) {
                        if (selection == index) {
                            Box(Modifier.size(34.dp).border(BorderStroke(3.dp, C.paper), CircleShape))
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A page over the tabs: a bar with «назад» and a title, then the content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), tint = C.ink) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = C.paper, scrolledContainerColor = C.paper),
    )
}

/** The system's share sheet with `text`: Telegram, WhatsApp, copy… */
fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** A page of the site in a browser tab over the app, in the app's colors. */
fun openPage(context: Context, url: String, paper: Color) {
    val params = CustomTabColorSchemeParams.Builder().setToolbarColor(paper.toArgb()).build()
    CustomTabsIntent.Builder().setDefaultColorSchemeParams(params).setShowTitle(true).build()
        .launchUrl(context, Uri.parse(url))
}

@Composable
fun Note(text: String, color: Color = C.muted, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 13.sp, modifier = modifier)
}
