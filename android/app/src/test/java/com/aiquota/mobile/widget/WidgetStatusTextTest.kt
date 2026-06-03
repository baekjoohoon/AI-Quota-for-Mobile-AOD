package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStatusTextTest {
    @Test
    fun providerWidgetStatusLabelsAreKorean() {
        assertEquals("연결됨", providerWidgetStatusLabel("CONNECTED"))
        assertEquals("연결 중", providerWidgetStatusLabel("CONNECTING"))
        assertEquals("연결 안 됨", providerWidgetStatusLabel("DISCONNECTED"))
        assertEquals("데이터 수집 중", providerWidgetStatusLabel("COLLECTING"))
        assertEquals("연결됨", providerWidgetStatusLabel("STALE"))
        assertEquals("연결 확인 필요", providerWidgetStatusLabel("INTERACTIVE_AUTH_REQUIRED"))
        assertEquals("사용할 수 없음", providerWidgetStatusLabel("UNAVAILABLE"))
        assertEquals("오류", providerWidgetStatusLabel("ERROR"))
        assertEquals("주의", providerWidgetStatusLabel("WARNING"))
        assertEquals("정상", providerWidgetStatusLabel("NORMAL"))
        assertEquals("상태 없음", providerWidgetStatusLabel(""))
    }

    @Test
    fun providerWidgetEmptyStateTextIsReadableKorean() {
        val glanceSource = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt").readText()
        val immediateSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetImmediateRenderer.kt").readText()

        assertFalse(glanceSource.contains("?곗씠???놁쓬"))
        assertFalse(immediateSource.contains("?곗씠???놁쓬"))
        assertTrue(glanceSource.contains("데이터 없음"))
        assertTrue(immediateSource.contains("데이터 없음"))
    }
}
