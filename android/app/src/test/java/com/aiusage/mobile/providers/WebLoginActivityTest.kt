package com.aiusage.mobile.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLoginActivityTest {
    @Test
    fun detectsClaudeAuthenticatedOrgCookie() {
        assertTrue(
            claudeCookieIndicatesAuthenticatedSession(
                "cf_clearance=keep; lastActiveOrg=6d7e7f53-6216-45b3-93bb-764f73f98c92; other=value"
            )
        )
    }

    @Test
    fun rejectsClaudeCookieWithoutActiveOrganization() {
        assertFalse(
            claudeCookieIndicatesAuthenticatedSession(
                "cf_clearance=keep; sessionKey=abc"
            )
        )
    }

    @Test
    fun acceptsClaudeAuthenticatedAppNavigation() {
        assertTrue(
            isClaudeAuthenticatedAppNavigation("https://claude.ai/new")
        )
    }

    @Test
    fun rejectsClaudeLoginNavigationForAuthenticatedShortcut() {
        assertFalse(
            isClaudeAuthenticatedAppNavigation("https://claude.ai/login")
        )
    }
}
