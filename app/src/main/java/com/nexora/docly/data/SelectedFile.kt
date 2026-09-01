package com.nexora.docly.data

import android.net.Uri
import androidx.compose.runtime.saveable.listSaver

data class SelectedFile(
    val uri: Uri,
    val name: String
) {
    val extension: String? get() = SupportedFormats.extensionOf(name)
    val category: SupportedFormats.Category? get() = SupportedFormats.categoryOf(name)
}

val SelectedFilesSaver = listSaver<List<SelectedFile>, String>(
    save = { files -> files.flatMap { listOf(it.uri.toString(), it.name) } },
    restore = { values ->
        values.chunked(2).map { SelectedFile(Uri.parse(it[0]), it[1]) }
    }
)