package com.fliptofocus.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Offline QR-code generation (pure computation via ZXing; no network). Used to render the recovery
 * code as a QR the parent can scan with their phone camera to save it securely — the "scan with your
 * phone" feature. Never throws: returns null if encoding fails so the UI can fall back to text only.
 */
object QrGen {

    fun encode(content: String, sizePx: Int = 512): ImageBitmap? = runCatching {
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        val w = matrix.width
        val h = matrix.height
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix.get(x, y)) black else white
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            .apply { setPixels(pixels, 0, w, 0, 0, w, h) }
            .asImageBitmap()
    }.getOrNull()
}
