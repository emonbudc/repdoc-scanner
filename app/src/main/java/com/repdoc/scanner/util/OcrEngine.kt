package com.repdoc.scanner.util

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class OcrLanguage(
    val code: String,
    val label: String,
    val shortLabel: String
) {
    BANGLA_ENGLISH("ben+eng", "Bangla + English", "BN+EN"),
    BANGLA("ben", "Bangla", "BN"),
    ENGLISH("eng", "English", "EN")
}

object OcrEngine {

    private val lock = Any()
    private var tess: TessBaseAPI? = null
    private var tessLanguage: String? = null

    suspend fun recognize(
        context: Context,
        bitmap: Bitmap,
        language: OcrLanguage = OcrLanguage.BANGLA_ENGLISH
    ): String = withContext(Dispatchers.IO) {
        val tesseractText = runCatching {
            recognizeTesseract(context, bitmap, language.code)
        }.getOrDefault("")
        if (tesseractText.isNotBlank()) return@withContext tesseractText
        runCatching { recognizeMlKit(bitmap) }.getOrDefault("")
    }

    private fun recognizeTesseract(context: Context, bitmap: Bitmap, language: String): String {
        synchronized(lock) {
            val engine = tess ?: TessBaseAPI().also { tess = it }
            if (tessLanguage != language) {
                val dataPath = ensureTrainedData(context).absolutePath
                if (!engine.init(dataPath, language)) {
                    engine.recycle()
                    tess = null
                    tessLanguage = null
                    return ""
                }
                tessLanguage = language
            }
            engine.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            engine.setImage(bitmap)
            return engine.utF8Text.orEmpty()
        }
    }

    private fun ensureTrainedData(context: Context): File {
        val root = File(context.filesDir, "tesseract")
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        listOf("ben.traineddata", "eng.traineddata").forEach { name ->
            val target = File(tessdata, name)
            if (!target.isFile || target.length() == 0L) {
                context.assets.open("tessdata/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return root
    }

    private suspend fun recognizeMlKit(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            recognizer.close()
        }
    }
}
