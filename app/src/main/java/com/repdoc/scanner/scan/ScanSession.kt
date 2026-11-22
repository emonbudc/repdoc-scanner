package com.repdoc.scanner.scan

import android.graphics.Bitmap

object ScanSession {
    var pendingBitmap: Bitmap? = null
    var activeDocumentId: String? = null
    var lastSavedDocumentId: String? = null

    fun clearPending() {
        pendingBitmap = null
    }

    fun reset() {
        pendingBitmap = null
        activeDocumentId = null
        lastSavedDocumentId = null
    }
}
