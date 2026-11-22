package com.repdoc.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.repdoc.scanner.data.Document
import java.io.File

object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 24f

    fun export(context: Context, document: Document): File? {
        if (document.pages.isEmpty()) return null
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeName = document.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "Document" }
        val output = File(dir, "${safeName}_${System.currentTimeMillis()}.pdf")
        val pdf = PdfDocument()
        try {
            document.pages.forEachIndexed { index, page ->
                val bitmap = BitmapFactory.decodeFile(page.imagePath) ?: return@forEachIndexed
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val pdfPage = pdf.startPage(info)
                drawPage(pdfPage.canvas, bitmap)
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

    private fun drawPage(canvas: Canvas, bitmap: Bitmap) {
        canvas.drawColor(Color.WHITE)
        val availableWidth = PAGE_WIDTH - MARGIN * 2
        val availableHeight = PAGE_HEIGHT - MARGIN * 2
        val scale = minOf(availableWidth / bitmap.width, availableHeight / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = (PAGE_WIDTH - width) / 2f
        val top = (PAGE_HEIGHT - height) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
    }
}
