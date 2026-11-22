package com.repdoc.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.intsig.nativelib.ImageProcessor
import com.intsig.nativelib.MagicEnhancer
import com.intsig.nativelib.PageScanner
import com.intsig.scanner.ScannerEngine
import kotlin.math.pow
import kotlin.math.roundToInt

object NativeFilters {

    private const val LICENSE_KEY = "79421895ae6636767d7300455003-PnzFpnaare"

    private var scannerReady = false
    private var magicReady = false
    private var imageProcessorReady = false
    private var pageScannerReady = false
    private var threadId = -1
    private var attempted = false

    @Synchronized
    fun initialize(context: Context) {
        if (attempted) return
        attempted = true
        val app = context.applicationContext
        scannerReady = runCatching {
            System.loadLibrary("mainstone")
            System.loadLibrary("scannercs")
            ScannerEngine.setLogLevel(2)
            val init = ScannerEngine.initEngine(app, LICENSE_KEY)
            threadId = ScannerEngine.initThreadContext()
            init >= 0 && threadId >= 0
        }.getOrDefault(false)

        magicReady = runCatching {
            System.loadLibrary("opencv")
            System.loadLibrary("ncnn")
            System.loadLibrary("MNN")
            System.loadLibrary("magicenhancer")
            true
        }.getOrDefault(false)

        imageProcessorReady = runCatching {
            System.loadLibrary("opencv")
            System.loadLibrary("ncnn")
            System.loadLibrary("MNN")
            System.loadLibrary("imageprocessor")
            true
        }.getOrDefault(false)

        pageScannerReady = runCatching {
            System.loadLibrary("opencv")
            System.loadLibrary("MNN")
            System.loadLibrary("pagescanner")
            true
        }.getOrDefault(false)
    }

    fun isNativeAvailable(): Boolean = scannerReady

    fun isAutoCropAvailable(): Boolean = scannerReady && pageScannerReady

    fun detectBorders(imagePath: String): IntArray? {
        if (!scannerReady || threadId < 0) return null
        return runCatching {
            ScannerEngine.detectBoundLinesFromImageFile3(threadId, imagePath)
        }.getOrNull()
    }

    fun dewarp(bitmap: Bitmap, borders: IntArray): Bitmap? {
        if (!pageScannerReady || borders.size != 8) return null
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        return runCatching {
            if (PageScanner.CropDewarpImagePos(copy, borders, 0, false) >= 0) copy else null
        }.getOrNull()
    }

    fun apply(bitmap: Bitmap, filter: DocumentFilter): Bitmap {
        if (filter == DocumentFilter.ORIGINAL) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val native = applyNative(bitmap, filter)
        return native ?: applyFallback(bitmap, filter)
    }

    private fun applyNative(bitmap: Bitmap, filter: DocumentFilter): Bitmap? {
        if (!scannerReady && !magicReady && !imageProcessorReady) return null
        val work = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        return runCatching {
            when (filter) {
                DocumentFilter.INVERT -> {
                    if (!imageProcessorReady) return null
                    if (ImageProcessor.ReflectBinaryColorShiftBitmap(work, 1) >= 0) work else null
                }
                DocumentFilter.NO_SHADOW -> {
                    if (!magicReady) return null
                    val type = runCatching { MagicEnhancer.classifyShadowTypeBitmap(work) }
                        .getOrDefault(MagicEnhancer.SHADOW_TYPE_DEFAULT)
                        .coerceIn(1, 5)
                    if (MagicEnhancer.deshadowBitmap(work, type) >= 0) work else null
                }
                else -> {
                    if (!scannerReady || threadId < 0) return null
                    if (ScannerEngine.enhanceImage(threadId, work, filter.enhanceMode)) work else null
                }
            }
        }.getOrNull()
    }

    private fun applyFallback(bitmap: Bitmap, filter: DocumentFilter): Bitmap {
        val work = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        return when (filter) {
            DocumentFilter.ORIGINAL -> work
            DocumentFilter.GRAYSCALE -> grayscale(work)
            DocumentFilter.BLACK_AND_WHITE -> blackAndWhite(work)
            DocumentFilter.INVERT -> invert(work)
            DocumentFilter.LIGHTEN -> colorMatrix(
                work,
                ColorMatrix(
                    floatArrayOf(
                        1.08f, 0f, 0f, 0f, 22f,
                        0f, 1.08f, 0f, 0f, 22f,
                        0f, 0f, 1.08f, 0f, 22f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            DocumentFilter.MAGIC_COLOR -> colorMatrix(
                work,
                ColorMatrix(
                    floatArrayOf(
                        1.12f, 0f, 0f, 0f, 8f,
                        0f, 1.12f, 0f, 0f, 8f,
                        0f, 0f, 1.12f, 0f, 8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            DocumentFilter.NO_SHADOW -> colorMatrix(
                work,
                ColorMatrix(
                    floatArrayOf(
                        1.24f, 0f, 0f, 0f, 18f,
                        0f, 1.24f, 0f, 0f, 18f,
                        0f, 0f, 1.24f, 0f, 18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            DocumentFilter.NO_HANDWRITING -> blackAndWhite(grayscale(work), 170)
            DocumentFilter.NO_WATERMARK -> colorMatrix(
                work,
                ColorMatrix(
                    floatArrayOf(
                        0.72f, 0.14f, 0.14f, 0f, 12f,
                        0.14f, 0.72f, 0.14f, 0f, 12f,
                        0.14f, 0.14f, 0.72f, 0f, 12f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            DocumentFilter.OMNI_FIX -> colorMatrix(
                work,
                ColorMatrix(
                    floatArrayOf(
                        1.22f, 0f, 0f, 0f, 10f,
                        0f, 1.22f, 0f, 0f, 10f,
                        0f, 0f, 1.22f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            DocumentFilter.ECO -> blackAndWhite(work, 150)
        }
    }

    private fun colorMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(out).drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    private fun grayscale(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) }
            )
        }
        Canvas(out).drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    private fun invert(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            pixels[i] = Color.argb(
                Color.alpha(c),
                255 - Color.red(c),
                255 - Color.green(c),
                255 - Color.blue(c)
            )
        }
        out.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    private fun blackAndWhite(bitmap: Bitmap, fixedThreshold: Int? = null): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val threshold = fixedThreshold ?: otsu(pixels)
        for (i in pixels.indices) {
            val c = pixels[i]
            val lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).roundToInt()
            val v = if (lum >= threshold) 255 else 0
            pixels[i] = Color.rgb(v, v, v)
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun otsu(pixels: IntArray): Int {
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val lum = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).roundToInt()
            histogram[lum.coerceIn(0, 255)]++
        }
        val total = pixels.size
        var sum = 0.0
        for (i in 0..255) sum += i * histogram[i]
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var threshold = 127
        for (i in 0..255) {
            wB += histogram[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i * histogram[i]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF).pow(2)
            if (between > best) {
                best = between
                threshold = i
            }
        }
        return threshold
    }
}
