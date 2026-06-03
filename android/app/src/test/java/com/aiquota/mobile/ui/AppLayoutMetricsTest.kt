package com.aiquota.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLayoutMetricsTest {
    @Test
    fun tabletMetricsUseMoreVerticalBreathingRoomThanPhone() {
        val phone = appLayoutMetrics(screenWidthDp = 411, screenHeightDp = 923)
        val tablet = appLayoutMetrics(screenWidthDp = 1024, screenHeightDp = 640)

        assertTrue(tablet.contentVerticalPaddingDp > phone.contentVerticalPaddingDp)
        assertTrue(tablet.navVerticalPaddingDp > phone.navVerticalPaddingDp)
        assertTrue(tablet.navBarMinHeightDp > phone.navBarMinHeightDp)
        assertEquals(13, phone.topBarTopExtraPaddingDp)
        assertEquals(8, phone.mainContentTopLiftDp)
        assertEquals(4, phone.topBarSettingsYOffsetDp)
        assertTrue(phone.navBottomExtraPaddingDp >= 30)
        assertTrue(tablet.navBottomExtraPaddingDp >= 30)
    }

    @Test
    fun navigationChipWidthScalesWithAvailableWidth() {
        val narrowPhone = appLayoutMetrics(screenWidthDp = 320, screenHeightDp = 640)
        val compactPhone = appLayoutMetrics(screenWidthDp = 360, screenHeightDp = 800)
        val phone = appLayoutMetrics(screenWidthDp = 411, screenHeightDp = 923)
        val tablet = appLayoutMetrics(screenWidthDp = 1024, screenHeightDp = 640)

        val narrowNavWidth = narrowPhone.navHorizontalPaddingDp * 2 +
            narrowPhone.navChipWidthDp * 6 +
            narrowPhone.navGapDp * 5
        val compactNavWidth = compactPhone.navHorizontalPaddingDp * 2 +
            compactPhone.navChipWidthDp * 6 +
            compactPhone.navGapDp * 5
        val phoneNavWidth = phone.navHorizontalPaddingDp * 2 +
            phone.navChipWidthDp * 6 +
            phone.navGapDp * 5

        assertTrue(narrowNavWidth <= 320)
        assertTrue(compactNavWidth <= 360)
        assertTrue(phoneNavWidth <= 411)
        assertTrue(phone.navChipWidthDp in 48..64)
        assertTrue(tablet.navChipWidthDp in 72..96)
        assertTrue(tablet.navChipWidthDp < phone.navChipWidthDp * 2)
        assertTrue(tablet.navBarMaxWidthDp < 1024)
        assertTrue(phone.navChipHeightDp >= 70)
        assertTrue(phone.navChipVerticalPaddingDp <= 4)
        assertTrue(tablet.navChipHeightDp >= 64)
    }

    @Test
    fun dashboardCardsFitConfiguredVisibleCountBeforeNextCardStarts() {
        val phone = appLayoutMetrics(screenWidthDp = 411, screenHeightDp = 923)
        val tablet = appLayoutMetrics(screenWidthDp = 1024, screenHeightDp = 640)

        assertEquals(3, phone.dashboardVisibleProviderCount)
        assertEquals(1, phone.dashboardGridColumnCount)
        assertEquals(4, tablet.dashboardVisibleProviderCount)
        assertEquals(2, tablet.dashboardGridColumnCount)

        val phoneCardHeight = dashboardProviderCardHeightDp(viewportHeightDp = 720, layoutMetrics = phone)
        val phoneNextStart = phone.contentVerticalPaddingDp +
            phone.dashboardTitleHeightDp +
            phone.sectionSpacingDp * (phone.dashboardVisibleProviderCount + 1) +
            phoneCardHeight * phone.dashboardVisibleProviderCount
        assertTrue(phoneNextStart >= 720)

        val tabletCardHeight = dashboardProviderCardHeightDp(viewportHeightDp = 580, layoutMetrics = tablet)
        val tabletVisibleRows = tablet.dashboardVisibleProviderCount / tablet.dashboardGridColumnCount
        val tabletNextStart = tablet.contentVerticalPaddingDp +
            tablet.dashboardTitleHeightDp +
            tablet.sectionSpacingDp * (tabletVisibleRows + 1) +
            tabletCardHeight * tabletVisibleRows
        assertTrue(tabletNextStart >= 580)
    }

    @Test
    fun largerScreensScaleSharedLayoutLengths() {
        val compact = appLayoutMetrics(screenWidthDp = 411, screenHeightDp = 800)
        val expanded = appLayoutMetrics(screenWidthDp = 900, screenHeightDp = 1200)

        assertTrue(expanded.contentHorizontalPaddingDp > compact.contentHorizontalPaddingDp)
        assertTrue(expanded.contentVerticalPaddingDp > compact.contentVerticalPaddingDp)
        assertTrue(expanded.cardPaddingDp > compact.cardPaddingDp)
        assertTrue(expanded.cardSpacingDp > compact.cardSpacingDp)
        assertTrue(expanded.navChipVerticalPaddingDp > compact.navChipVerticalPaddingDp)
    }

    @Test
    fun dashboardProviderCardHeightKeepsThreeProvidersVisibleOnCompactDashboard() {
        val metrics = appLayoutMetrics(screenWidthDp = 393, screenHeightDp = 852)

        val cardHeight = dashboardProviderCardHeightDp(
            viewportHeightDp = 590,
            layoutMetrics = metrics
        )

        assertTrue(
            "Dashboard card height must keep three provider cards visible on compact phones.",
            cardHeight <= 187
        )
    }
}
