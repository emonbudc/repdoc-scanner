package com.repdoc.scanner.util

import androidx.annotation.DrawableRes
import com.repdoc.scanner.R

enum class DocumentFilter(
    val displayName: String,
    val enhanceMode: Int,
    @DrawableRes val iconRes: Int
) {
    ORIGINAL("Original", -1, R.drawable.ic_cs_filter_original),
    LIGHTEN("Lighten", 15, R.drawable.ic_cs_filter_lighten),
    MAGIC_COLOR("Magic Color", 17, R.drawable.ic_cs_filter_magic_color),
    GRAYSCALE("Grayscale", 10, R.drawable.ic_cs_filter_gray_scale),
    BLACK_AND_WHITE("B&W", 19, R.drawable.ic_cs_filter_b_and_w),
    ECO("Eco", 16, R.drawable.ic_cs_filter_eco),
    NO_SHADOW("No Shadow", -10, R.drawable.ic_cs_filter_no_shadow),
    OMNI_FIX("Omni Fix", -12, R.drawable.ic_cs_filter_omni_fix),
    NO_WATERMARK("No Watermark", 8, R.drawable.ic_cs_filter_no_watermark),
    NO_HANDWRITING("No Handwriting", 9, R.drawable.ic_cs_filter_no_handwriting),
    INVERT("Invert", 110, R.drawable.ic_cs_filter_invert)
}
