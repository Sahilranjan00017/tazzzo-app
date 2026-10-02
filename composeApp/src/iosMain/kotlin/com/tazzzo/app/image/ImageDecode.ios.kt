package com.tazzzo.app.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): ImageBitmap? {
    if (bytes.isEmpty() || maxDimension <= 0) return null
    return try {
        val image = Image.makeFromEncoded(bytes)
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return null
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(w, h))
        if (scale >= 1f) return image.toComposeImageBitmap()
        // Skia decodes at full size; re-rasterise once at the bounded size so the retained bitmap is small.
        val tw = (w * scale).toInt().coerceAtLeast(1)
        val th = (h * scale).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(tw, th)
        surface.canvas.drawImageRect(
            image, Rect.makeWH(w.toFloat(), h.toFloat()), Rect.makeWH(tw.toFloat(), th.toFloat()), SamplingMode.LINEAR, null, true
        )
        surface.makeImageSnapshot().toComposeImageBitmap()
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        null
    }
}
