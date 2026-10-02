package com.tazzzo.app.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes an encoded image (JPEG / PNG / WebP as the platform supports them) into a bitmap no larger than
 * [maxDimension] on its longest side, or null when the bytes are not a decodable image. Never throws: an
 * undecodable or hostile payload is an "image unavailable", not a crash in a product grid.
 *
 * Downscaling at decode time is what keeps a grid of fifty cards from retaining fifty full-resolution packshots.
 * Called off the main thread by [RemoteImageLoader].
 */
expect fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): ImageBitmap?
