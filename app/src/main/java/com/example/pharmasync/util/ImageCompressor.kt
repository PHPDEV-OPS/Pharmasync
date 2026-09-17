package com.example.pharmasync.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Downscales and re-encodes picked photos so uploads are small and fast. */
object ImageCompressor {

    @Throws(IOException::class)
    fun compressToJpeg(context: Context, uri: Uri, maxDimension: Int, quality: Int = 85): ByteArray {
        val bitmap = decode(context, uri, maxDimension)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun decode(context: Context, uri: Uri, maxDimension: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF rotation, so camera photos come out upright.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = maxDimension.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Unable to decode $uri")
    }
}
