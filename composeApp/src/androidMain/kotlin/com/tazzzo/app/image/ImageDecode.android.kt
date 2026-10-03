package com.tazzzo.app.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): ImageBitmap? {
    if (bytes.isEmpty() || maxDimension <= 0) return null
    return try {
        // Pass 1: bounds only, so the sample size is chosen before any pixel memory is allocated.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / (sample * 2) >= maxDimension || h / (sample * 2) >= maxDimension) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        null   // OutOfMemory, corrupt stream, unsupported codec: all "unavailable"
    }
}
