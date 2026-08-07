# WebView / keep 规则
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}