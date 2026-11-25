package com.roufsyed.cabin.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import java.io.IOException

/** Cover art as Cabin keeps it: square, and no larger than it is ever drawn. */
object Covers {

    /** The largest size cover art is drawn at is 84 design pixels; twice that covers any scale. */
    const val SIDE_PX = 168

    /**
     * The picture a player sent with its track, cropped square and scaled down. The result is always
     * a bitmap of Cabin's own, so the player can recycle the one it sent; [ours] says [source] already
     * is one and need not be copied.
     */
    fun scaled(source: Bitmap, ours: Boolean = false): ImageBitmap {
        val side = minOf(source.width, source.height)
        val square = if (source.width == source.height) {
            source
        } else {
            Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        }
        val scaled = if (side > SIDE_PX) square.scale(SIDE_PX, SIDE_PX) else square
        val owned = if (scaled === source && !ours) source.copy(Bitmap.Config.ARGB_8888, false) else scaled
        return owned.asImageBitmap()
    }

    /**
     * The picture at an address a player gave instead of sending the picture itself, or null when it
     * cannot be read: the player keeps it private, or it is gone. Decoded small, never at full size.
     * Not for the main thread.
     */
    fun read(resolver: ContentResolver, address: Uri): ImageBitmap? = try {
        val picture = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, address)) { decoder, info, _ ->
            // A plain bitmap in memory, which is what cropping and scaling need.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val shorter = minOf(info.size.width, info.size.height)
            if (shorter > SIDE_PX) decoder.setTargetSampleSize(shorter / SIDE_PX)
        }
        scaled(picture, ours = true)
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        // Not allowed to read it, or not a picture.
        null
    }
}
