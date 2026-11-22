package com.repdoc.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import java.io.File
import kotlin.math.abs

object AutoCrop {
    data class Detection(val corners: List<PointF>, val borders: IntArray)

    fun detect(context: Context, bitmap: Bitmap): Detection? {
        if (!NativeFilters.isAutoCropAvailable()) return null
        val file = File(context.cacheDir, "autocrop_${System.currentTimeMillis()}.jpg")
        return try {
            if (!ImageProcessor.saveJpeg(bitmap, file, 96)) return null
            val borders = NativeFilters.detectBorders(file.absolutePath) ?: return null
            val corners = borders.toCorners()
            if (!isValid(bitmap, corners)) return null
            Detection(corners, borders)
        } finally {
            file.delete()
        }
    }

    fun crop(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        if (corners.size != 4) return bitmap
        val borders = corners.toBorders()
        return NativeFilters.dewarp(bitmap, borders)
            ?: ImageProcessor.cropPerspective(bitmap, corners)
    }

    private fun IntArray.toCorners(): List<PointF> {
        if (size < 8) return emptyList()
        return listOf(
            PointF(this[0].toFloat(), this[1].toFloat()),
            PointF(this[2].toFloat(), this[3].toFloat()),
            PointF(this[4].toFloat(), this[5].toFloat()),
            PointF(this[6].toFloat(), this[7].toFloat())
        )
    }

    private fun List<PointF>.toBorders(): IntArray {
        val result = IntArray(8)
        for (index in 0 until 4) {
            result[index * 2] = this[index].x.toInt()
            result[index * 2 + 1] = this[index].y.toInt()
        }
        return result
    }

    private fun isValid(bitmap: Bitmap, corners: List<PointF>): Boolean {
        if (corners.size != 4) return false
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        if (corners.any { it.x < 0f || it.y < 0f || it.x > width || it.y > height }) return false
        var area = 0.0
        for (index in corners.indices) {
            val current = corners[index]
            val next = corners[(index + 1) % corners.size]
            area += current.x.toDouble() * next.y - next.x.toDouble() * current.y
        }
        return abs(area) * 0.5 > width * height * 0.02
    }
}
