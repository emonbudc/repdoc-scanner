package com.intsig.nativelib;

import android.graphics.Bitmap;

public final class PageScanner {
    private PageScanner() {
    }

    public static native int CropDewarpImagePos(Bitmap bitmap, int[] borders, int rotation, boolean flag);

    public static native int CropDewarpImagePos2(Bitmap bitmap, int[] borders, int rotation, boolean flag);

    public static native int DewarpImagePos(Bitmap bitmap, int[] borders, boolean flag);

    public static native int detectBorderMode(int[] borders, int[] output, int width, int height, int mode);

    public static native String GetVersion();
}
