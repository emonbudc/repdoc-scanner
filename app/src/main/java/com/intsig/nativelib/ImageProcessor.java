package com.intsig.nativelib;

import android.graphics.Bitmap;

public final class ImageProcessor {
    private ImageProcessor() {
    }

    public static native int ReflectBinaryColorShiftBitmap(Bitmap bitmap, int mode);

    public static native int deblurBitmap(Bitmap bitmap, int mode);

    public static native String GetVersion();
}
