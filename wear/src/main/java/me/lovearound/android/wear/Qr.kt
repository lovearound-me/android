package me.lovearound.android.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.nayuki.qrcodegen.QrCode

/** A QR code drawn module by module, black on white with a quiet zone. */
@Composable
fun QrView(text: String, size: Dp, modifier: Modifier = Modifier) {
    val qr = remember(text) { QrCode.encodeText(text, QrCode.Ecc.MEDIUM) }
    Box(modifier.size(size).background(Color.White, RoundedCornerShape(10.dp)).padding(size * 0.07f)) {
        Canvas(Modifier.size(size * 0.86f)) {
            val cell = this.size.width / qr.size
            for (y in 0 until qr.size) for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
                    // A hair wider than the cell, so no seams show between modules.
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
