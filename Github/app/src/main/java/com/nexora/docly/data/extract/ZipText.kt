package com.nexora.docly.data.extract

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream

/**
 * Extracts text from zip-based document formats:
 * DOCX, ODT, XLSX, PPTX and EPUB — via pure zip + XML parsing (no heavy deps).
 */
object ZipText {

    data class Entry(val name: String, val bytes: ByteArray)

    private const val MAX_ENTRY_BYTES = 48 * 1024 * 1024

    fun readEntries(context: Context, uri: Uri): List<Entry>? = try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            val entries = ArrayList<Entry>()
            val zip = ZipInputStream(stream)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (entry.size > MAX_ENTRY_BYTES) {
                    zip.closeEntry()
                    continue
                }
                entries.add(Entry(entry.name, zip.readBytes()))
                zip.closeEntry()
            }
            zip.close()
            entries
        }
    } catch (_: Exception) {
        null
    }

    // --------------------------------------------------------------- DOCX
    fun docx(context: Context, uri: Uri): String? {
        val entries = readEntries(context, uri) ?: return null
        val docs = entries
            .filter { it.name.startsWith("word/document") && it.name.endsWith(".xml") }
            .sortedBy { it.name }
        if (docs.isEmpty()) return null
        val sb = StringBuilder()
        for (doc in docs) {
            val text = xmlToText(
                String(doc.bytes, Charsets.UTF_8),
                snippetMarkerTag = "w:p",
                tab = "<w:tab[^>]*/>",
                breakTag = "<w:br[^>]*/>"
            )
            if (text.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(text)
            }
        }
        return sb.toString().ifBlank { null }
    }

    // --------------------------------------------------------------- ODT
    fun odt(context: Context, uri: Uri): String? {
        val entries = readEntries(context, uri) ?: return null
        val content = entries.firstOrNull { it.name == "content.xml" } ?: return null
        var s = String(content.bytes, Charsets.UTF_8)
        s = s.replace(Regex("<text:p[^>]*>"), "\n")
        s = s.replace(Regex("<text:h[^>]*>"), "\n")
        s = s.replace(Regex("<text:line-break[^>]*/>"), "\n")
        s = s.replace(Regex("<text:tab[^>]*/>"), "\t")
        s = s.replace(Regex("<text:s[^>]*/>"), " ")
        s = stripTags(s)
        s = s.replace(Regex("<[^>]+>"), "")
        return unescapeEntity(s).cleanLines().ifBlank { null }
    }

    // --------------------------------------------------------------- PPTX
    fun pptx(context: Context, uri: Uri): String? {
        val entries = readEntries(context, uri) ?: return null
        val slides = entries
            .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { entry ->
                entry.name.substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: 0
            }
        if (slides.isEmpty()) return null
        val sb = StringBuilder()
        for (slide in slides) {
            var s = String(slide.bytes, Charsets.UTF_8)
            s = s.replace(Regex("<a:p[^>]*>"), "\n")
            s = s.replace(Regex("<a:br[^>]*/>"), "\n")
            s = s.replace(Regex("<[^>]+>"), "")
            val text = unescapeEntity(s).cleanLines()
            if (text.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(text)
            }
        }
        return sb.toString().ifBlank { null }
    }

    // --------------------------------------------------------------- XLSX
    fun xlsx(context: Context, uri: Uri): String? {
        val entries = readEntries(context, uri) ?: return null

        val shared = ArrayList<String>()
        val sharedXml = entries.firstOrNull { it.name == "xl/sharedStrings.xml" }
        sharedXml?.let { entry ->
            val xml = String(entry.bytes, Charsets.UTF_8)
            val siRegex = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
            for (match in siRegex.findAll(xml)) {
                val tRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                shared.add(
                    tRegex.findAll(match.groupValues[1]).joinToString("") { it.groupValues[1] }
                )
            }
        }

        val sheets = entries
            .filter { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sortedBy { entry ->
                entry.name.substringAfter("sheet").substringBefore(".xml").toIntOrNull() ?: 0
            }

        val sb = StringBuilder()
        for (sheet in sheets) {
            val xml = String(sheet.bytes, Charsets.UTF_8)
            val rowsText = Regex("<sheetData>(.*?)</sheetData>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1) ?: continue
            val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
            val rowList = ArrayList<String>()
            for (row in rowRegex.findAll(rowsText)) {
                val cells = ArrayList<String>()
                val cellRegex = Regex("<c([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
                for (cell in cellRegex.findAll(row.groupValues[1])) {
                    val attrs = cell.groupValues[1]
                    val inner = cell.groupValues.getOrElse(2) { "" }
                    val type = Regex("t=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                    val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                        .find(inner)?.groupValues?.get(1)
                        ?.replace(",", "").orEmpty().trim()
                    if (v.isEmpty()) {
                        // inline string
                        cells.add(
                            Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                                .findAll(inner).joinToString("") { it.groupValues[1] }
                        )
                    } else if (type == "s") {
                        val idx = v.toIntOrNull()
                        cells.add(if (idx != null && idx in shared.indices) shared[idx] else "")
                    } else {
                        cells.add(v)
                    }
                }
                val line = cells.joinToString("\t").trimEnd('\t')
                if (line.isNotBlank()) rowList.add(line)
            }
            if (rowList.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(rowList.joinToString("\n"))
            }
        }
        return sb.toString().ifBlank { null }
    }

    // --------------------------------------------------------------- EPUB
    fun epub(context: Context, uri: Uri): String? {
        val entries = readEntries(context, uri) ?: return null
        val docs = entries
            .filter { entry ->
                val name = entry.name.lowercase()
                !name.startsWith("meta-inf") &&
                        !name.startsWith("mimetype") &&
                        (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"))
            }
            .sortedBy { it.name }
        if (docs.isEmpty()) return null
        val sb = StringBuilder()
        for (doc in docs) {
            var s = String(doc.bytes, Charsets.UTF_8)
            s = s.replace(Regex("<head>.*?</head>", RegexOption.DOT_MATCHES_ALL), "\n")
            s = s.replace(Regex("<style.*?</style>", RegexOption.DOT_MATCHES_ALL), "\n")
            s = s.replace(Regex("<script.*?</script>", RegexOption.DOT_MATCHES_ALL), "\n")
            s = s.replace(Regex("<br[^>]*>"), "\n")
            s = s.replace(Regex("</(p|div|h[1-6]|li|tr|section)>"), "\n")
            s = s.replace(Regex("<[^>]+>"), "")
            val text = unescapeEntity(s).cleanLines()
            if (text.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(text)
            }
        }
        return sb.toString().ifBlank { null }
    }

    // ------------------------------------------------------------------

    private fun xmlToText(xml: String, snippetMarkerTag: String, tab: String, breakTag: String): String {
        var s = xml
        s = s.replace(Regex(tab), "\t")
        s = s.replace(Regex(breakTag), "\n")
        s = s.replace(Regex("</$snippetMarkerTag>"), "\n")
        s = s.replace(Regex("<w:tbl[^>]*>"), "\n")
        s = stripTags(s)
        return unescapeEntity(s).cleanLines()
    }

    private fun stripTags(s: String): String = s.replace(Regex("<[^>]+>"), "")

    fun unescapeEntity(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { it.toChar().toString() } ?: m.value
        }
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.let { it.toChar().toString() } ?: m.value
        }

    fun String.cleanLines(): String {
        var s = replace("\u0000", "")
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        val lines = s.split("\n").map { it.trim() }
        return lines.joinToString("\n").trim('\n').trim()
    }
}