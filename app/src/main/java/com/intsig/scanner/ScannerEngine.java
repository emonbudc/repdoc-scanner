package com.intsig.scanner;

import android.content.Context;
import android.graphics.Bitmap;

public final class ScannerEngine {
    private ScannerEngine() {
    }

    public static native int initEngine(Context context, String key);

    public static synchronized native int initThreadContext();

    public static native int destroyThreadContext(int threadId);

    public static native int enhanceBitmap(int threadId, Bitmap bitmap, int mode);

    public static native int detectBorder(int threadId, String imagePath, int[] borders, int mode);

    public static native int trimBitmap(int threadId, Bitmap bitmap, int[] borders, Bitmap output, int mode, int flag);

    public static native int setLogLevel(int level);

    public static native String GetVersion();

    public static boolean enhanceImage(int threadId, Bitmap bitmap, int mode) {
        return enhanceBitmap(threadId, bitmap, mode) >= 0;
    }

    public static int[] detectBoundLinesFromImageFile3(int threadId, String imagePath) {
        int[] borders = new int[8];
        int result = detectBorder(threadId, imagePath, borders, 10);
        return result < 0 ? null : borders;
    }
}
