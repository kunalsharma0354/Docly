package com.nexora.docly.data.extract

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.imaging.Imaging
import java.io.ByteArrayInputStream
import java.awt.image.BufferedImage

/**
 * On-device OCR via Google ML Kit (Latin scripts). Images decode through
 * the Android decoder; TIFF is handled by Apache Commons Imaging.
 */
object OcrEngine {

    private const val MAX_DIM = 2400
    private const val MAX_IMAGE_BYTES = 96 * 1024 * 1024

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not open the image.")
        if (bytes.size > MAX_IMAGE_BYTES) throw IllegalStateException("Image is too large.")
        val bitmap = decode(bytes) ?: throw IllegalStateException("Unsupported image format.")
        try {
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            val text = result.text.trim()
            if (text.isBlank()) throw IllegalStateException("No text found in this image.")
            text
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun extractText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        result.text.trim()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sample = 1
            while (bounds.outWidth / sample > MAX_DIM || bounds.outHeight / sample > MAX_DIM) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
        // TIFF and other decoders BitmapFactory cannot handle
        return runCatching {
            val source = Imaging.getBufferedImage(ByteArrayInputStream(bytes))
            toBitmap(source)
        }.getOrNull()
    }

    private fun toBitmap(source: BufferedImage): Bitmap {
        val w = source.width
        val h = source.height
        var scale = 1
        while (w / scale > MAX_DIM || h / scale > MAX_DIM) scale *= 2
        val nw = w / scale
        val nh = h / scale
        val pixels = IntArray(nw * nh)
        if (scale == 1) {
            source.getRGB(0, 0, nw, nh, pixels, 0, nw)
        } else {
            val scaled = BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB)
            val g = scaled.createGraphics()
            g.drawImage(source, 0, 0, nw, nh, null)
            g.dispose()
            scaled.getRGB(0, 0, nw, nh, pixels, 0, nw)
        }
        return Bitmap.createBitmap(pixels, nw, nh, Bitmap.Config.ARGB_8888)
    }
}