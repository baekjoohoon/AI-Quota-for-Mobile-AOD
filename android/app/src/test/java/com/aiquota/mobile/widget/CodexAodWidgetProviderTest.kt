package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexAodWidgetProviderTest {
    @Test
    fun codexAodWidgetIsRegisteredAsACompactHomeAndKeyguardWidget() {
        val providerFile = File("src/main/java/com/aiquota/mobile/widget/CodexAodWidgetProvider.kt")
        val providerInfoFile = File("src/main/res/xml/ai_quota_widget_codex_aod.xml")
        val layoutFile = File("src/main/res/layout/ai_quota_widget_codex_aod.xml")
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(providerFile.exists())
        assertTrue(providerInfoFile.exists())
        assertTrue(layoutFile.exists())

        val providerInfo = providerInfoFile.readText()
        assertTrue(manifest.contains("android:name=\".widget.CodexAodWidgetProvider\""))
        assertTrue(providerInfo.contains("android:targetCellWidth=\"2\""))
        assertTrue(providerInfo.contains("android:targetCellHeight=\"1\""))
        assertTrue(providerInfo.contains("android:widgetCategory=\"home_screen|keyguard\""))
    }

    @Test
    fun codexAodWidgetReadsCachedCodexQuotaWithoutOwningAuthenticationOrCollection() {
        val providerFile = File("src/main/java/com/aiquota/mobile/widget/CodexAodWidgetProvider.kt")
        val payloadFile = File("src/main/java/com/aiquota/mobile/widget/CodexAodWidgetPayload.kt")

        assertTrue(providerFile.exists())
        assertTrue(payloadFile.exists())

        val provider = providerFile.readText()
        val payload = payloadFile.readText()
        assertTrue(provider.contains("WidgetSnapshotCache(context).read()"))
        assertTrue(payload.contains("providerWidgetPayload("))
        assertTrue(payload.contains("ProviderId.CODEX.storageId"))
        assertFalse(provider.contains("WebLoginActivity"))
        assertFalse(provider.contains("ProviderWebCollector"))
        assertFalse(payload.contains("ProviderWebCollector"))
    }

    @Test
    fun codexAodWidgetShowsOnlyFiveHourAndWeeklyRowsWithResetText() {
        val layoutFile = File("src/main/res/layout/ai_quota_widget_codex_aod.xml")
        val payloadFile = File("src/main/java/com/aiquota/mobile/widget/CodexAodWidgetPayload.kt")

        assertTrue(layoutFile.exists())
        assertTrue(payloadFile.exists())

        val layout = layoutFile.readText()
        val payload = payloadFile.readText()
        assertTrue(layout.contains("android:text=\"5H\""))
        assertTrue(layout.contains("android:text=\"W\""))
        assertTrue(layout.contains("@+id/codex_aod_five_hour_reset"))
        assertTrue(layout.contains("@+id/codex_aod_weekly_reset"))
        assertTrue(payload.contains("findFiveHourLine"))
        assertTrue(payload.contains("findWeeklyLine"))
        assertTrue(payload.contains("compactResetText"))
        assertFalse(layout.contains("ProgressBar"))
        assertFalse(layout.contains("ImageView"))
    }
}
