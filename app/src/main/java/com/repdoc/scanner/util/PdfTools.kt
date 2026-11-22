package com.repdoc.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PdfTools {

    fun displayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)
                        else null
                    } else null
                }
        }.getOrNull() ?: "document.pdf"
    }

    fun pageCount(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { it.pageCount }
            } ?: 0
        }.getOrDefault(0)
    }

    fun saveImages(context: Context, bitmaps: List<Bitmap>, baseName: String): Int {
        val safe = baseName.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "PDF" }
        var saved = 0
        bitmaps.forEachIndexed { index, bitmap ->
            val temp = File(context.cacheDir, "pdf_image_${System.currentTimeMillis()}_${index + 1}.jpg")
            try {
                if (ImageProcessor.saveJpeg(bitmap, temp, 94)) {
                    val uri = ExportUtils.saveToDownloads(
                        context,
                        temp,
                        "${safe}_page_${index + 1}.jpg",
                        "image/jpeg"
                    )
                    if (uri != null) saved++
                }
            } finally {
                temp.delete()
            }
        }
        return saved
    }

    fun createPdf(bitmaps: List<Bitmap>, output: File): Boolean {
        if (bitmaps.isEmpty()) return false
        val pdf = PdfDocument()
        return try {
            bitmaps.forEachIndexed { index, bitmap ->
                val pageWidth = if (bitmap.width >= bitmap.height) 842 else 595
                val pageHeight = (pageWidth.toFloat() * bitmap.height / bitmap.width)
                    .roundToInt()
                    .coerceAtLeast(1)
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdf.startPage(info)
                page.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat()),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                pdf.finishPage(page)
            }
            output.outputStream().use { pdf.writeTo(it) }
            true
        } catch (_: Throwable) {
            output.delete()
            false
        } finally {
            pdf.close()
        }
    }

    fun merge(context: Context, uris: List<Uri>, output: File, maxPages: Int = 300): Int {
        if (uris.isEmpty()) return 0
        val pdf = PdfDocument()
        var pageIndex = 0
        return try {
            for (uri in uris) {
                if (pageIndex >= maxPages) break
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        for (index in 0 until renderer.pageCount) {
                            if (pageIndex >= maxPages) break
                            renderer.openPage(index).use { page ->
                                val longest = max(page.width, page.height).coerceAtLeast(1)
                                val scale = min(2.0f, 2200f / longest)
                                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                Canvas(bitmap).drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pageWidth = if (width >= height) 842 else 595
                                val pageHeight = (pageWidth.toFloat() * height / width)
                                    .roundToInt()
                                    .coerceAtLeast(1)
                                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                                val outPage = pdf.startPage(info)
                                outPage.canvas.drawBitmap(
                                    bitmap,
                                    null,
                                    RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat()),
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                )
                                pdf.finishPage(outPage)
                                bitmap.recycle()
                                pageIndex++
                            }
                        }
                    }
                }
            }
            if (pageIndex == 0) {
                output.delete()
                0
            } else {
                output.outputStream().use { pdf.writeTo(it) }
                pageIndex
            }
        } catch (_: Throwable) {
            output.delete()
            0
        } finally {
            pdf.close()
        }
    }
}
