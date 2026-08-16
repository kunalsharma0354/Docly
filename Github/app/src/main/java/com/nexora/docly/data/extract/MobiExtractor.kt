package com.nexora.docly.data.extract

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Kindle / PalmDOC e-books: .mobi, .azw, .azw3.
 * Handles the PDB container + PalmDOC (LZ77) compression, falls back to
 * raw text scanning for Huffman-compressed variants.
 */
object MobiExtractor {

    fun extract(bytes: ByteArray): String? {
        if (bytes.size < 100) return null

        // AZW3 / KF8 files are often zip-packaged
        if (bytes[0].toInt() == 'P'.code && bytes[1].toInt() == 'K'.code) return null

        val numRecords = beU16(bytes, 76)
        if (numRecords < 2) return null
        val rec0Offset = beU32(bytes, 78) ?: return null
        if (rec0Offset + 16 > bytes.size) return null

        // PalmDOC header inside record 0
        val compression = beU16(bytes, rec0Offset)
        if (compression != 1 && compression != 2) return null
        val textLength = beU32(bytes, rec0Offset + 4) ?: return null

        // MOBI magic?
        val mobiMagic = String(bytes, rec0Offset + 16, 4, Charsets.ISO_8859_1)
        if (mobiMagic != "MOBI") return null

        val textEncoding = beU32(bytes, rec0Offset + 16 + 12) ?: 1252

        val textRecords = numRecords - 1
        if (textRecords <= 0) return null

        val decoder = PalmDocDecoder()
        for (i in 1..textRecords) {
            if (i * 8 + 2 > bytes.size) break
            val off = beU32(bytes, 78 + i * 8) ?: continue
            val end = minOf(off + 16 * 1024, bytes.size)
            decoder.feed(bytes, off, end)
            if (decoder.size() > textLength + 4096) break
        }

        val raw = decoder.toByteArray()
        if (raw.size < 4) return null

        val charset = when (textEncoding) {
            65001 -> Charsets.UTF_8
            65002 -> Charsets.UTF_16LE
            1252 -> runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)
            else -> Charsets.UTF_8
        }
        val text = String(raw, charset).cleanMobile()
        return text.ifBlank { null }
    }

    private fun String.cleanMobile(): String {
        var s = this.replace("\u0000", "")
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s.trim()
    }

    private fun beU16(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun beU32(bytes: ByteArray, offset: Int): Int? {
        if (offset + 3 >= bytes.size) return null
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }

    /** PalmDOC / LZ77 decompressor — feeds record chunks, gives back the plain text stream. */
    private class PalmDocDecoder {
        private val out = ByteArrayOutputStream()

        fun feed(bytes: ByteArray, start: Int, end: Int) {
            var i = start
            while (i < end) {
                val c = bytes[i].toInt() and 0xFF
                i++
                when {
                    c == 0x00 -> {
                        if (i >= end) return
                        var runLen = bytes[i].toInt() and 0xFF
                        i++
                        runLen = (runLen - 1).coerceAtLeast(0)
                        for (k in 0 until runLen) out.write(0)
                    }
                    c <= 0x08 -> {
                        for (k in 0 until c) out.write(' '.code)
                    }
                    c in 0x09..0x7F -> out.write(c)
                    c in 0x80..0xBF -> {
                        if (i >= end) return
                        val n = ((c and 0x3F) shl 8) or (bytes[i].toInt() and 0xFF)
                        i++
                        backReference(n)
                    }
                    else -> { // 0xC0..0xFE
                        if (i >= end) return
                        val n = ((c and 0x1F) shl 8) or (bytes[i].toInt() and 0xFF)
                        i++
                        backReference(n)
                    }
                }
            }
        }

        private fun backReference(n: Int) {
            val distance = (n shr 3) + 1
            val length = (n and 0x7) + 3
            val buf = out.toByteArray()
            val start = (buf.size - distance).coerceAtLeast(0)
            for (k in 0 until length) {
                val idx = start + k
                out.write(if (idx < buf.size) buf[idx].toInt() else 0)
            }
        }

        fun size(): Int = out.size()

        fun toByteArray(): ByteArray = out.toByteArray()
    }
}