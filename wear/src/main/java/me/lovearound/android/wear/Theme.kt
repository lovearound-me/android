package me.lovearound.android.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** The phone app's dark colors, on the watch's black. */
object W {
    val link = Color(0xFFF28C9B)
    val heart = Color(0xFFEC6F80)
    val muted = Color(0xFFC7B0A2)
    val star = Color(0xFFE8B54F)
    val card = Color(0xFF242424)
    val danger = Color(0xFFFF8A80)
}

@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = W.link,
            onPrimary = Color(0xFF2A211D),
            primaryContainer = Color(0xFF4A2B31),
            onPrimaryContainer = Color(0xFFFDE3E6),
            error = W.danger,
        ),
        content = content,
    )
}
