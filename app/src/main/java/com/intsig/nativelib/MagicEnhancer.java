package com.intsig.nativelib;

import android.graphics.Bitmap;

public final class MagicEnhancer {
    public static final int SHADOW_TYPE_DEFAULT = 1;
    public static final int SHADOW_TYPE_COLOR = 2;
    public static final int SHADOW_TYPE_MAGIC = 3;
    public static final int SHADOW_TYPE_COMPLEX_BACKGROUND_ADJUST = 4;
    public static final int SHADOW_TYPE_STRONG_REMOVE_SHADOW = 5;

    private MagicEnhancer() {
    }

    public static native int deshadowBitmap(Bitmap bitmap, int type);

    public static native int deshadowBitmap2(Bitmap bitmap, int type, int sessionId);

    public static native int deshadowBitmapNew(Bitmap bitmap, int type, int sessionId);

    public static native int classifyShadowTypeBitmap(Bitmap bitmap);

    public static native String GetVersion();
}
