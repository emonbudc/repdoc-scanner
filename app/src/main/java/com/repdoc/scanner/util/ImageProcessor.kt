package com.repdoc.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max

object ImageProcessor {

    fun decodeBitmap(context: Context, uri: Uri, maxDimension: Int = 2800): Bitmap? {
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            applyOrientation(bitmap, orientation)
        }.getOrNull()
    }

    fun decodeFile(path: String, maxDimension: Int = 2800): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
            val orientation = ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            applyOrientation(bitmap, orientation)
        }.getOrNull()
    }

    fun scaled(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun cropPerspective(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        if (corners.size != 4) return bitmap
        val top = distance(corners[0], corners[1])
        val bottom = distance(corners[3], corners[2])
        val left = distance(corners[0], corners[3])
        val right = distance(corners[1], corners[2])
        val outWidth = max(top, bottom).toInt().coerceAtLeast(1)
        val outHeight = max(left, right).toInt().coerceAtLeast(1)
        val src = floatArrayOf(
            corners[0].x, corners[0].y,
            corners[1].x, corners[1].y,
            corners[2].x, corners[2].y,
            corners[3].x, corners[3].y
        )
        val dst = floatArrayOf(
            0f, 0f,
            outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(),
            0f, outHeight.toFloat()
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return bitmap
        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        return out
    }

    fun defaultCorners(bitmap: Bitmap): List<PointF> {
        val insetX = bitmap.width * 0.04f
        val insetY = bitmap.height * 0.04f
        return listOf(
            PointF(insetX, insetY),
            PointF(bitmap.width - insetX, insetY),
            PointF(bitmap.width - insetX, bitmap.height - insetY),
            PointF(insetX, bitmap.height - insetY)
        )
    }

    fun fullCorners(bitmap: Bitmap): List<PointF> {
        return listOf(
            PointF(0f, 0f),
            PointF(bitmap.width.toFloat(), 0f),
            PointF(bitmap.width.toFloat(), bitmap.height.toFloat()),
            PointF(0f, bitmap.height.toFloat())
        )
    }

    fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return out
    }

    fun trimLightBorder(bitmap: Bitmap, threshold: Int = 245): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 8 || height < 8) return bitmap
        val maxX = (width * 0.25f).toInt().coerceAtLeast(2)
        val maxY = (height * 0.25f).toInt().coerceAtLeast(2)
        val stepX = (width / 160).coerceAtLeast(1)
        val stepY = (height / 160).coerceAtLeast(1)

        fun rowLight(y: Int): Boolean {
            var light = 0
            var total = 0
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luma >= threshold) light++
                total++
                x += stepX
            }
            return total > 0 && light * 100 >= total * 99
        }

        fun columnLight(x: Int): Boolean {
            var light = 0
            var total = 0
            var y = 0
            while (y < height) {
                val pixel = bitmap.getPixel(x, y)
                val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luma >= threshold) light++
                total++
                y += stepY
            }
            return total > 0 && light * 100 >= total * 99
        }

        var left = 0
        while (left < maxX && columnLight(left)) left++
        var top = 0
        while (top < maxY && rowLight(top)) top++
        var right = 0
        while (right < maxX && columnLight(width - 1 - right)) right++
        var bottom = 0
        while (bottom < maxY && rowLight(height - 1 - bottom)) bottom++
        if (left + right >= width * 0.7f || top + bottom >= height * 0.7f) return bitmap
        if (left == 0 && top == 0 && right == 0 && bottom == 0) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, width - left - right, height - top - bottom)
    }

    fun whitenEdgeDark(bitmap: Bitmap, threshold: Int = 60): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return bitmap
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        fun isDark(index: Int): Boolean {
            val pixel = pixels[index]
            val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
            return luma < threshold
        }

        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        var head = 0
        var tail = 0
        fun seed(index: Int) {
            if (!visited[index] && isDark(index)) {
                visited[index] = true
                queue[tail++] = index
            }
        }
        for (x in 0 until width) {
            seed(x)
            seed((height - 1) * width + x)
        }
        for (y in 0 until height) {
            seed(y * width)
            seed(y * width + width - 1)
        }
        var changed = false
        while (head < tail) {
            val index = queue[head++]
            pixels[index] = Color.WHITE
            changed = true
            val x = index % width
            val y = index / width
            if (x > 0) seed(index - 1)
            if (x + 1 < width) seed(index + 1)
            if (y > 0) seed(index - width)
            if (y + 1 < height) seed(index + width)
        }
        if (!changed) return bitmap
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    fun trimDarkBorder(bitmap: Bitmap, threshold: Int = 48): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 8 || height < 8) return bitmap
        val maxX = (width * 0.35f).toInt().coerceAtLeast(2)
        val maxY = (height * 0.35f).toInt().coerceAtLeast(2)
        val stepX = (width / 120).coerceAtLeast(1)
        val stepY = (height / 120).coerceAtLeast(1)

        fun rowIsDark(y: Int): Boolean {
            var dark = 0
            var total = 0
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luma < threshold) dark++
                total++
                x += stepX
            }
            return total > 0 && dark * 100 >= total * 90
        }

        fun columnIsDark(x: Int): Boolean {
            var dark = 0
            var total = 0
            var y = 0
            while (y < height) {
                val pixel = bitmap.getPixel(x, y)
                val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luma < threshold) dark++
                total++
                y += stepY
            }
            return total > 0 && dark * 100 >= total * 90
        }

        var left = 0
        while (left < maxX && columnIsDark(left)) left++
        var top = 0
        while (top < maxY && rowIsDark(top)) top++
        var right = 0
        while (right < maxX && columnIsDark(width - 1 - right)) right++
        var bottom = 0
        while (bottom < maxY && rowIsDark(height - 1 - bottom)) bottom++
        if (left + right >= width * 0.5f || top + bottom >= height * 0.5f) return bitmap
        if (left == 0 && top == 0 && right == 0 && bottom == 0) return bitmap
        val out = Bitmap.createBitmap(bitmap, left, top, width - left - right, height - top - bottom)
        return out
    }

    fun trimToContent(bitmap: Bitmap, threshold: Int = 80): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 16 || height < 16) return bitmap
        val stepX = (width / 160).coerceAtLeast(1)
        val stepY = (height / 160).coerceAtLeast(1)

        fun rowBright(y: Int): Boolean {
            var bright = 0
            var total = 0
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luma >= threshold) bright++
                total++
                x += stepX
            }
            return total > 0 && bright * 100 >= total * 55
        }

        fun columnBright(x: Int): Boolean {
            var bright = 0
            var total = 0
            var y = 0
            while (y < height) {
                val pixel = bitmap.getPixel(x, y)
                val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luma >= threshold) bright++
                total++
                y += stepY
            }
            return total > 0 && bright * 100 >= total * 55
        }

        fun longestRun(size: Int, predicate: (Int) -> Boolean): IntRange? {
            var bestStart = -1
            var bestEnd = -1
            var start = -1
            var index = 0
            while (index < size) {
                if (predicate(index)) {
                    if (start < 0) start = index
                    if (index - start > bestEnd - bestStart) {
                        bestStart = start
                        bestEnd = index
                    }
                } else {
                    start = -1
                }
                index++
            }
            if (bestStart < 0) return null
            return bestStart..bestEnd
        }

        val rows = longestRun(height, ::rowBright) ?: return bitmap
        val top = rows.first
        val bottom = rows.last
        if (bottom - top < height * 0.35f) return bitmap
        val cols = longestRun(width) { x ->
            var bright = 0
            var total = 0
            var y = top
            while (y <= bottom) {
                val pixel = bitmap.getPixel(x, y)
                val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luma >= threshold) bright++
                total++
                y += stepY
            }
            total > 0 && bright * 100 >= total * 55
        } ?: return bitmap
        val left = cols.first
        val right = cols.last
        if (right - left < width * 0.35f) return bitmap
        if (left == 0 && top == 0 && right == width - 1 && bottom == height - 1) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    fun cleanCardImage(bitmap: Bitmap): Bitmap =
        trimToContent(
            trimDarkBorder(
                whitenEdgeDark(
                    trimLightBorder(flattenOnWhite(bitmap))
                )
            )
        )

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 94): Boolean {
        return runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        }.getOrDefault(false)
    }

    private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > maxDimension || height / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun distance(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
