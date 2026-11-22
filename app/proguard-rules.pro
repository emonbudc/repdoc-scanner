-keep class com.intsig.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn com.intsig.**
-dontwarn com.google.mlkit.**
