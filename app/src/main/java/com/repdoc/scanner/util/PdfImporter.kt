package com.repdoc.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PdfImporter {

    fun import(
        context: Context,
        uri: Uri,
        maxPages: Int = 100,
        maxDimension: Int = 2800,
        maxBytes: Long = 384L * 1024 * 1024
    ): List<Bitmap> {
        return runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            descriptor.use { fileDescriptor ->
                PdfRenderer(fileDescriptor).use { renderer ->
                    val count = min(renderer.pageCount, maxPages)
                    val bitmaps = ArrayList<Bitmap>(count)
                    var totalBytes = 0L
                    var budgetExceeded = false
                    for (index in 0 until count) {
                        if (budgetExceeded) break
                        renderer.openPage(index).use { page ->
                            val longest = max(page.width, page.height).coerceAtLeast(1)
                            val scale = min(2.5f, maxDimension.toFloat() / longest)
                            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                            val bytes = width.toLong() * height * 4
                            if (bitmaps.isNotEmpty() && totalBytes + bytes > maxBytes) {
                                budgetExceeded = true
                            } else {
                                val bitmap = Bitmap.createBitmap(
                                    width,
                                    height,
                                    Bitmap.Config.ARGB_8888
                                )
                                try {
                                    Canvas(bitmap).drawColor(Color.WHITE)
                                    page.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )
                                    bitmaps.add(bitmap)
                                    totalBytes += bytes
                                } catch (error: Throwable) {
                                    bitmap.recycle()
                                    throw error
                                }
                            }
                        }
                    }
                    bitmaps
                }
            }
        }.getOrDefault(emptyList())
    }
}
