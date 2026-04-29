-keep class com.app.positivelygeared.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
