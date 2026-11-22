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

    fun import(context: Context, uri: Uri, maxPages: Int = 100): List<Bitmap> {
        return runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            descriptor.use { fileDescriptor ->
                PdfRenderer(fileDescriptor).use { renderer ->
                    val count = min(renderer.pageCount, maxPages)
                    val bitmaps = ArrayList<Bitmap>(count)
                    for (index in 0 until count) {
                        renderer.openPage(index).use { page ->
                            val longest = max(page.width, page.height).coerceAtLeast(1)
                            val scale = min(2.5f, 2800f / longest)
                            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bitmap)
                        }
                    }
                    bitmaps
                }
            }
        }.getOrDefault(emptyList())
    }
}
