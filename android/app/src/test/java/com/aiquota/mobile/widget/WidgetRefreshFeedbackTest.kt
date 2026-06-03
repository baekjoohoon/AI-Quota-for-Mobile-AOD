package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshFeedbackTest {
    @Test
    fun automaticRefreshDoesNotSpinWidgetsWithoutManualFeedback() {
        assertFalse(
            WidgetRefreshFeedback.isRefreshInProgress(
                widgetRefreshActive = false
            )
        )
    }

    @Test
    fun refreshFeedbackIsActiveForThePressedWidgetBeforeProviderStartsCollecting() {
        assertTrue(
            WidgetRefreshFeedback.isRefreshInProgress(
                widgetRefreshActive = true
            )
        )
        assertFalse(
            WidgetRefreshFeedback.isRefreshInProgress(
                widgetRefreshActive = false
            )
        )
    }

    @Test
    fun refreshFeedbackApiCannotUseProviderCollectionStateForSpinner() {
        val source = File("src/main/java/com/aiquota/mobile/widget/WidgetRefreshFeedback.kt").readText()

        assertFalse(source.contains("providers: List<ProviderWidgetPayload>"))
        assertFalse(source.contains("providers.any"))
        assertTrue(source.contains("fun isRefreshInProgress("))
        assertTrue(source.contains("widgetRefreshActive: Boolean"))
    }

    @Test
    fun refreshFeedbackPersistenceDoesNotUseSynchronousCommits() {
        val source = File("src/main/java/com/aiquota/mobile/widget/WidgetRefreshFeedback.kt").readText()

        assertTrue(source.contains(".apply()"))
        assertFalse(source.contains(".commit()"))
    }

}
