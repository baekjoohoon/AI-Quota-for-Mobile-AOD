# AI Quota release obfuscation rules.
#
# First security step: shorten internal implementation names while preserving
# runtime entry points that Android, WorkManager, and WebView JavaScript bridges
# resolve by name.

-dontshrink
-dontoptimize

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class com.aiquota.mobile.AIQuotaApplication
-keep class com.aiquota.mobile.** extends android.app.Activity
-keep class com.aiquota.mobile.** extends android.app.Service
-keep class com.aiquota.mobile.** extends android.content.BroadcastReceiver
-keep class com.aiquota.mobile.** extends android.appwidget.AppWidgetProvider
-keep class com.aiquota.mobile.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver

-keep class com.aiquota.mobile.sync.ForegroundRefreshHealthWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
