package com.nexora.docly.data.extract

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF extraction: reads the embedded text layer first; when a PDF is
 * scanned / image-only, every page is rendered and passed through OCR.
 */
object PdfExtractor {

    private const val MAX_PDF_BYTES = 256 * 1024 * 1024
    private const val MIN_TEXT_BEFORE_OCR = 40
    private const val OCR_MAX_PAGES = 60

    @Volatile
    private var initialized = false

    private fun ensureInit(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    suspend fun extract(context: Context, uri: Uri): Result {
        ensureInit(context)
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.Failed("Could not open the PDF.")
            if (bytes.size > MAX_PDF_BYTES) {
                return@withContext Result.Failed("PDF is too large to process.")
            }
            try {
                PDDocument.load(bytes).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    val layerText = stripper.getText(doc).trim()

                    if (layerText.length >= MIN_TEXT_BEFORE_OCR) {
                        Result.Success(layerText)
                    } else {
                        val renderer = PDFRenderer(doc)
                        val pages = StringBuilder()
                        var pageIndex = 0
                        for (page in doc.pages) {
                            pageIndex++
                            if (pageIndex > OCR_MAX_PAGES) {
                                pages.append("\n\n[Remaining pages skipped - OCR limit reached]")
                                break
                            }
                            val bitmap = renderer.renderImageWithDPI(pageIndex - 1, 300f)
                            val pageText = try {
                                OcrEngine.extractText(bitmap)
                            } catch (_: Exception) {
                                ""
                            } finally {
                                bitmap.recycle()
                            }
                            if (pageText.isNotBlank()) {
                                pages.append("--- Page $pageIndex ---\n")
                                pages.append(pageText).append("\n\n")
                            }
                        }
                        val ocrText = pages.toString().trim()
                        when {
                            ocrText.isBlank() ->
                                Result.Pending("This PDF has no readable text layer and no text was detected in its pages.")
                            else -> Result.Success(ocrText)
                        }
                    }
                }
            } catch (e: Exception) {
                Result.Failed("Could not read this PDF: ${e.message}")
            }
        }
    }

    sealed interface Result {
        data class Success(val text: String) : Result
        data class Pending(val message: String) : Result
        data class Failed(val message: String) : Result
    }
}