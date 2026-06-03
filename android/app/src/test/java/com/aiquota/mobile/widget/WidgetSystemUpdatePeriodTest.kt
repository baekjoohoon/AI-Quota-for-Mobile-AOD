package com.aiquota.mobile.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSystemUpdatePeriodTest {
    @Test
    fun allHomeScreenWidgetsUseFifteenMinuteSystemUpdatePeriod() {
        val widgetXmlFiles = listOf(
            "src/main/res/xml/ai_quota_widget_circular.xml",
            "src/main/res/xml/ai_quota_widget_large.xml",
            "src/main/res/xml/ai_quota_widget_provider.xml"
        )

        widgetXmlFiles.forEach { path ->
            assertEquals(
                "$path should use 15 minute system widget updates",
                FIFTEEN_MINUTES_MILLIS,
                updatePeriodMillis(path)
            )
        }
    }

    private fun updatePeriodMillis(path: String): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(path))
        return document.documentElement.getAttribute("android:updatePeriodMillis")
    }

    private companion object {
        const val FIFTEEN_MINUTES_MILLIS = "900000"
    }
}
