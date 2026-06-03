package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPickerOrderTest {
    @Test
    fun manifestDeclaresPreferredWidgetPickerOrder() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val receiverBlocks = Regex("""<receiver[\s\S]*?</receiver>""")
            .findAll(manifest)
            .map { it.value }
            .filter { it.contains("""android:name="android.appwidget.provider"""") }
            .toList()

        val declaredReceiverNames = receiverBlocks.map { block ->
            Regex("""android:name="([^"]+)"""")
                .find(block)
                ?.groupValues
                ?.get(1)
        }

        assertEquals(
            listOf(
                ".widget.AIQuotaUnifiedGlanceWidgetReceiver",
                ".widget.AIQuotaCircularWidgetProvider",
                ".widget.ProviderUsageWidgetProvider"
            ),
            declaredReceiverNames
        )
    }
}
