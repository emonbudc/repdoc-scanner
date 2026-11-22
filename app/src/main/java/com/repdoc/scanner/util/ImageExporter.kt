package com.repdoc.scanner.util

import android.content.Context
import com.repdoc.scanner.data.Document
import java.io.File

object ImageExporter {

    fun export(context: Context, document: Document): List<File> {
        if (document.pages.isEmpty()) return emptyList()
        val safeName = document.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "Document" }
        val dir = File(
            context.filesDir,
            "exports/${safeName}_images_${System.currentTimeMillis()}"
        ).apply { mkdirs() }
        val files = ArrayList<File>()
        document.pages.forEachIndexed { index, page ->
            val source = File(page.imagePath)
            if (!source.isFile) return@forEachIndexed
            val output = File(dir, "page_${index + 1}.jpg")
            runCatching { source.copyTo(output, overwrite = true) }
                .onSuccess { files.add(output) }
        }
        return files
    }
}
