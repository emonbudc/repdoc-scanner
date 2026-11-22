package com.repdoc.scanner.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.repdoc.scanner.data.Document
import java.io.File

object PdfExporter {

    private const val PORTRAIT_WIDTH = 595
    private const val LANDSCAPE_WIDTH = 842

    fun export(context: Context, document: Document): File? {
        if (document.pages.isEmpty()) return null
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeName = document.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "Document" }
        val output = File(dir, "${safeName}_${System.currentTimeMillis()}.pdf")
        val pdf = PdfDocument()
        try {
            document.pages.forEachIndexed { index, page ->
                val bitmap = BitmapFactory.decodeFile(page.imagePath) ?: return@forEachIndexed
                val pageWidth = if (bitmap.width >= bitmap.height) LANDSCAPE_WIDTH else PORTRAIT_WIDTH
                val pageHeight = (pageWidth.toFloat() * bitmap.height / bitmap.width)
                    .toInt()
                    .coerceAtLeast(1)
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdf.startPage(info)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                pdfPage.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat()),
                    paint
                )
                pdf.finishPage(pdfPage)
            }
            output.outputStream().use { pdf.writeTo(it) }
            return output
        } catch (_: Throwable) {
            output.delete()
            return null
        } finally {
            pdf.close()
        }
    }
}
