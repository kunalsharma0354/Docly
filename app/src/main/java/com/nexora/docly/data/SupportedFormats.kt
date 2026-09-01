package com.nexora.docly.data

/**
 * Catalog of every format Docly supports, grouped for the UI.
 * Covers PDF, Office, E-books, data files and images (OCR-ready later).
 */
object SupportedFormats {

    enum class Category(
        val label: String,
        val description: String,
        val extensions: List<String>
    ) {
        PDF("PDF", "Scanned & digital PDFs", listOf("pdf")),
        WORD("Word", "Microsoft Word documents", listOf("docx", "doc", "rtf", "odt")),
        TEXT("Text", "Plain text documents", listOf("txt", "md")),
        EXCEL("Excel", "Spreadsheets", listOf("xlsx", "xls", "csv", "tsv")),
        POWERPOINT("PowerPoint", "Presentations", listOf("pptx", "ppt")),
        EBOOK("E-books", "Digital books", listOf("epub", "mobi", "azw", "azw3")),
        IMAGE("Images", "Text in images (OCR)", listOf("jpg", "jpeg", "png", "webp", "tiff", "tif"));

        val icon: String get() = name
    }

    val allExtensions: Set<String> = Category.entries.flatMap { it.extensions }.toSet()

    fun categoryOf(fileName: String): Category? {
        val ext = extensionOf(fileName) ?: return null
        return Category.entries.firstOrNull { ext in it.extensions }
    }

    fun extensionOf(fileName: String): String? {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.length - 1) return null
        return fileName.substring(dot + 1).lowercase()
    }

    fun isSupported(fileName: String): Boolean {
        val ext = extensionOf(fileName) ?: return false
        return ext in allExtensions
    }

    fun supportedCount(): Int = allExtensions.size
}