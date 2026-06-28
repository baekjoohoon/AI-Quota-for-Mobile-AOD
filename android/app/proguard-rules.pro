# AI Quota release obfuscation rules.
#
# First security step: shorten internal implementation names while preserving
# runtime entry points that Android, WorkManager, and WebView JavaScript bridges
# resolve by name.

-dontshrink
-dontoptimize

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class com.aiquota.mobile.AIQuotaApplication { public <init>(); }

-keep class com.aiquota.mobile.MainActivity { public <init>(); }
-keep class com.aiquota.mobile.providers.WebLoginActivity { public <init>(); }
-keep class com.aiquota.mobile.providers.GeminiCliLoopbackOAuthActivity { public <init>(); }
-keep class com.aiquota.mobile.providers.AntigravityLoopbackOAuthActivity { public <init>(); }
-keep class com.aiquota.mobile.providers.GoogleAuthorizationActivity { public <init>(); }
-keep class net.openid.appauth.RedirectUriReceiverActivity { public <init>(); }
-keep class com.aiquota.mobile.widget.ProviderWidgetConfigureActivity { public <init>(); }
-keep class com.aiquota.mobile.widget.DashboardWidgetConfigureActivity { public <init>(); }

-keep class com.aiquota.mobile.providers.ProviderUsageCollectionService { public <init>(); }
-keep class com.aiquota.mobile.providers.ProviderBackgroundRefreshService { public <init>(); }

-keep class com.aiquota.mobile.widget.WidgetManualRefreshReceiver { public <init>(); }
-keep class com.aiquota.mobile.providers.ProviderRefreshReceiver { public <init>(); }
-keep class com.aiquota.mobile.widget.AIQuotaUnifiedGlanceWidgetReceiver { public <init>(); }
-keep class com.aiquota.mobile.widget.AIQuotaCircularWidgetProvider { public <init>(); }
-keep class com.aiquota.mobile.widget.ProviderUsageWidgetProvider { public <init>(); }
-keep class com.aiquota.mobile.debug.LiveRefreshIssueDebugReceiver { public <init>(); }

-keep class com.aiquota.mobile.sync.ForegroundRefreshHealthWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
