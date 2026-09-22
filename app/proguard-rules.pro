# Exam Browser Optimized Proguard Rules
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# WebView Javascript Interface Keep Rules
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve View Binding reflection
-keepclassmembers class * extends androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(...);
}

# Optimize R8 / Proguard
-repackageclasses ''
-allowaccessmodification
-dontusemixedcaseclassnames
-dontpreverify
-verbose
