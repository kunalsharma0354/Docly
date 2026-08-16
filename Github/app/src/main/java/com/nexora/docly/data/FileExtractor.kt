package com.nexora.docly.data

import android.content.Context
import android.net.Uri
import com.nexora.docly.data.extract.BinaryScan
import com.nexora.docly.data.extract.MobiExtractor
import com.nexora.docly.data.extract.OcrEngine
import com.nexora.docly.data.extract.PdfExtractor
import com.nexora.docly.data.extract.ZipText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Master extractor — routes every supported format to its engine:
 * PDF (text layer + OCR), Office, E-books, delimited data, images.
 */
object FileExtractor {

    const val MAX_CHARS = 400_000

    private val PLAIN_TEXT = setOf("txt", "md", "csv", "tsv")
    private val IMAGES = setOf("jpg", "jpeg", "png", "webp", "tiff", "tif")
    private val LEGACY_BINARY = setOf("doc", "xls", "ppt")
    private val MOBI = setOf("mobi", "azw", "azw3")

    sealed interface Result {
        data class Success(val text: String) : Result
        data class Pending(val message: String) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun extract(context: Context, uri: Uri, fileName: String): Result =
        withContext(Dispatchers.IO) {
            val ext = SupportedFormats.extensionOf(fileName)
                ?: return@withContext Result.Failed("Could not detect the file format.")

            val outcome = try {
                when {
                    ext in PLAIN_TEXT -> readPlainText(context, uri)
                    ext == "rtf" -> readRtf(context, uri)
                    ext == "pdf" -> fromPdf(context, uri)
                    ext == "docx" -> ZipText.docx(context, uri)
                        ?.let { Result.Success(it) } ?: Result.Failed("Could not read this Word document.")
                    ext == "odt" -> ZipText.odt(context, uri)
                        ?.let { Result.Success(it) } ?: Result.Failed("Could not read this OpenDocument file.")
                    ext == "xlsx" -> ZipText.xlsx(context, uri)
                        ?.let { Result.Success(it) } ?: Result.Failed("Could not read this spreadsheet.")
                    ext == "pptx" -> ZipText.pptx(context, uri)
                        ?.let { Result.Success(it) } ?: Result.Failed("Could not read this presentation.")
                    ext == "epub" -> ZipText.epub(context, uri)
                        ?.let { Result.Success(it) } ?: Result.Failed("Could not read this e-book.")
                    ext in LEGACY_BINARY -> readLegacyBinary(context, uri)
                    ext in MOBI -> readMobi(context, uri)
                    ext in IMAGES -> Result.Success(OcrEngine.extractText(context, uri))
                    else -> Result.Pending("Extraction for .$ext isn't available yet.")
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Result.Failed("Could not extract text: ${e.message}")
            }

            when (outcome) {
                is Result.Pending -> outcome
                is Result.Failed -> outcome
                is Result.Success -> {
                    val trimmed = outcome.text.trim()
                    when {
                        trimmed.isBlank() -> Result.Pending("No readable text found in this file.")
                        trimmed.length > MAX_CHARS ->
                            Result.Success(trimmed.take(MAX_CHARS) +
                                    "\n\n[Output truncated - file contains more text]")
                        else -> Result.Success(trimmed)
                    }
                }
            }
        }

    private suspend fun fromPdf(context: Context, uri: Uri): Result =
        when (val r = PdfExtractor.extract(context, uri)) {
            is PdfExtractor.Result.Success -> Result.Success(r.text)
            is PdfExtractor.Result.Pending -> Result.Pending(r.message)
            is PdfExtractor.Result.Failed -> Result.Failed(r.message)
        }

    private fun readPlainText(context: Context, uri: Uri): Result {
        val bytes = readBytes(context, uri) ?: return Result.Failed("Could not open the file.")
        return Result.Success(decodeText(bytes))
    }

    private fun readRtf(context: Context, uri: Uri): Result {
        val bytes = readBytes(context, uri) ?: return Result.Failed("Could not open the file.")
        return Result.Success(stripRtf(decodeText(bytes)))
    }

    private fun readLegacyBinary(context: Context, uri: Uri): Result {
        val bytes = readBytes(context, uri) ?: return Result.Failed("Could not open the file.")
        val text = BinaryScan.extract(bytes)
        return if (text.length >= 6) Result.Success(text)
        else Result.Pending("No readable text found in this legacy document.")
    }

    private fun readMobi(context: Context, uri: Uri): Result {
        val bytes = readBytes(context, uri) ?: return Result.Failed("Could not open the file.")
        val viaKf8 = ZipText.epub(context, uri)
        if (!viaKf8.isNullOrBlank()) return Result.Success(viaKf8)
        val viaMobi = MobiExtractor.extract(bytes)
        if (!viaMobi.isNullOrBlank()) return Result.Success(viaMobi)
        val scanned = BinaryScan.extract(bytes)
        return if (scanned.length >= 6) Result.Success(scanned)
        else Result.Pending("No readable text found in this e-book.")
    }

    private fun decodeText(bytes: ByteArray): String {
        val zeroCount = bytes.count { it == 0.toByte() }
        if (bytes.size >= 16 && zeroCount * 2 >= bytes.size - 8) {
            val hasBom = bytes.size >= 2 &&
                    (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xFE
            return if (hasBom) {
                String(bytes.drop(2).toByteArray(), Charsets.UTF_16LE)
            } else {
                runCatching { String(bytes, Charsets.UTF_16LE) }.getOrNull()
                    ?: String(bytes, Charsets.UTF_16)
            }
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.count { it == '\uFFFD' } > utf8.length / 40) {
            runCatching { String(bytes, Charsets.ISO_8859_1) }.getOrDefault(utf8)
        } else utf8
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    /** Lightweight RTF cleanup — control words out, plain text in. */
    private fun stripRtf(raw: String): String {
        var out = raw
            .replace(Regex("\\{\\*?\\\\[^}\\n]*}"), " ")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
            .replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ")
            .replace(Regex("[{}]"), "")
        out = out.replace(Regex("\\\\par[ }]?"), "\n").replace(Regex("\\\\tab"), "  ")
        out = out.replace(Regex("\\s+"), " ").replace(Regex(" ?\n ?"), "\n")
        return out.trim()
    }
}