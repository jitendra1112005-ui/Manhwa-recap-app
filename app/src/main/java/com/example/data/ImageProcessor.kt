package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

object ImageProcessor {

    /**
     * Decodes and downscales a panel image so that inline multimodal payloads
     * remain fast and well below the 20MB Firebase AI request limit while keeping
     * speech bubbles and artwork sharp.
     */
    suspend fun decodePanelBitmap(
        context: Context,
        uri: Uri,
        totalPanels: Int
    ): Bitmap = withContext(Dispatchers.IO) {
        val maxDimension = when {
            totalPanels > 15 -> 768
            totalPanels > 8 -> 896
            else -> 1080
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longest = max(width, height)
                if (longest > maxDimension) {
                    val scale = maxDimension.toFloat() / longest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                maxDimension
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw IllegalStateException("Unable to read selected image.")

            scaleDownIfNeeded(decoded, maxDimension)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while ((longest / inSampleSize) > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }
}
