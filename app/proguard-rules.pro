# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools directory.

# Keep JavaScript interface classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the app's bridge classes
-keep class com.seanime.app.AppUpdater$Bridge { *; }
-keep class com.seanime.app.MainActivity$OrientationBridge { *; }
