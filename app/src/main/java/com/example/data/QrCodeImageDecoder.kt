package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object QrCodeImageDecoder {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    /**
     * Decodes a QR code from a CameraX ImageProxy frame.
     */
    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = imageProxy.planes[0].rowStride

            val source = PlanarYUVLuminanceSource(
                bytes,
                rowStride,
                height,
                0,
                0,
                width,
                height,
                false
            )

            decodeLuminanceSource(source)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes a QR code from an Android Uri (Gallery image / screenshot).
     * Retries multiple scaled sizes and inverted colors as requested in spec.
     */
    fun decodeFromUri(context: Context, uri: Uri): String? {
        val originalBitmap = loadBitmapFromUri(context, uri) ?: return null
        return decodeWithRobustFallbacks(originalBitmap)
    }

    /**
     * Decodes a QR code from a Bitmap with robust retry passes:
     * - Hybrid binarizer & Global histogram binarizer
     * - Inverted colors
     * - Multi-scaling (scaled down if large, scaled up if small)
     */
    fun decodeWithRobustFallbacks(bitmap: Bitmap): String? {
        // Pass 1: Original Bitmap
        decodeBitmap(bitmap)?.let { return it }

        // Pass 2: Inverted Colors
        decodeBitmapInverted(bitmap)?.let { return it }

        // Pass 3: Downscale if too large (e.g. 4K camera screenshots)
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim > 1200) {
            val scale = 1200f / maxDim
            val scaledWidth = (bitmap.width * scale).toInt()
            val scaledHeight = (bitmap.height * scale).toInt()
            val downscaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            decodeBitmap(downscaled)?.let { return it }
            decodeBitmapInverted(downscaled)?.let { return it }
        }

        // Pass 4: Medium scale (800px)
        if (maxDim > 800) {
            val scale = 800f / maxDim
            val scaledWidth = (bitmap.width * scale).toInt()
            val scaledHeight = (bitmap.height * scale).toInt()
            val downscaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            decodeBitmap(downscaled)?.let { return it }
            decodeBitmapInverted(downscaled)?.let { return it }
        }

        // Pass 5: Upscale if small
        val minDim = min(bitmap.width, bitmap.height)
        if (minDim in 1..400) {
            val upscaled = Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, true)
            decodeBitmap(upscaled)?.let { return it }
        }

        return null
    }

    private fun decodeBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        return decodeLuminanceSource(source)
    }

    private fun decodeBitmapInverted(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = InvertedLuminanceSource(RGBLuminanceSource(width, height, pixels))
        return decodeLuminanceSource(source)
    }

    private fun decodeLuminanceSource(source: LuminanceSource): String? {
        // Attempt with HybridBinarizer
        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            reader.reset()
            if (result != null && result.text.isNotEmpty()) {
                return result.text
            }
        } catch (_: Exception) {
            reader.reset()
        }

        // Attempt with GlobalHistogramBinarizer (better for uneven lighting / shadows)
        try {
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            reader.reset()
            if (result != null && result.text.isNotEmpty()) {
                return result.text
            }
        } catch (_: Exception) {
            reader.reset()
        }

        // Attempt with Inverted source if not already inverted
        try {
            val invertedSource = InvertedLuminanceSource(source)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
            val result = reader.decodeWithState(binaryBitmap)
            reader.reset()
            if (result != null && result.text.isNotEmpty()) {
                return result.text
            }
        } catch (_: Exception) {
            reader.reset()
        }

        return null
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (_: Exception) {
            null
        }
    }
}
