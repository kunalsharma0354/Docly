package com.nexora.docly.data.extract

/**
 * Raw-text recovery for legacy binary formats (.doc, .xls, .ppt and
 * unsupported e-book variants). Scans printable text runs — both ASCII
 * and UTF-16 encoded Word strings.
 */
object BinaryScan {

    fun extract(bytes: ByteArray, maxChars: Int = 120_000): String {
        val text = if (looksUtf16(bytes)) scanUtf16(bytes) else scanAscii(bytes)
        return text.take(maxChars).trim()
    }

    private fun looksUtf16(bytes: ByteArray): Boolean {
        if (bytes.size < 128) return false
        val pairs = bytes.size / 2
        var printablePairs = 0
        var highZero = 0
        var i = 0
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            if (lo in 0x20..0x7E && hi == 0) printablePairs++
            if (hi == 0) highZero++
            i += 2
        }
        return printablePairs > pairs / 3
    }

    private fun scanAscii(bytes: ByteArray): String {
        val out = StringBuilder()
        val run = StringBuilder()
        var lastWasLineBreak = false
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            when {
                c == '\n'.code -> {
                    flushRun(run, out, lastWasLineBreak)
                    lastWasLineBreak = true
                }
                c in 0x20..0x7E || c == '\t'.code -> {
                    run.append(c.toChar())
                    lastWasLineBreak = false
                }
                c == 0 -> Unit
                else -> {
                    flushRun(run, out, lastWasLineBreak)
                    lastWasLineBreak = false
                }
            }
        }
        flushRun(run, out, false)
        return out.toString()
    }

    private fun scanUtf16(bytes: ByteArray): String {
        val out = StringBuilder()
        val run = StringBuilder()
        var i = 0
        var lastWasLineBreak = false
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            i += 2
            when {
                lo == '\n'.code && hi == 0 -> {
                    flushRun(run, out, lastWasLineBreak)
                    lastWasLineBreak = true
                }
                lo in 0x20..0x7E || lo == '\t'.code -> {
                    if (hi != 0 && hi != 0xFF) {
                        flushRun(run, out, false)
                        continue
                    }
                    run.append(lo.toChar())
                    lastWasLineBreak = false
                }
                else -> {
                    flushRun(run, out, false)
                    lastWasLineBreak = false
                }
            }
        }
        flushRun(run, out, false)
        return out.toString()
    }

    private fun flushRun(run: StringBuilder, out: StringBuilder, forceLine: Boolean) {
        if (run.isBlank()) {
            run.clear()
            return
        }
        run.append('\n')
        out.append(run)
        run.clear()
    }
}