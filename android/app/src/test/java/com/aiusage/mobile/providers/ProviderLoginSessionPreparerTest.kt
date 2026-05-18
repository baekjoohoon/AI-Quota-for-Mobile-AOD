package com.aiusage.mobile.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLoginSessionPreparerTest {
    @Test
    fun claudeSessionCleanupExpiresCurrentClaudeCookiesButKeepsChallengeCookies() {
        val requests = ProviderLoginSessionPreparer.claudeCookieDeletionRequestsForTest(
            mapOf("https://claude.ai/" to "sessionKey=abc; lastActiveOrg=org_123; cf_clearance=ok; __cf_bm=ok")
        )
        val headers = requests.map { it.header }

        assertTrue(headers.any { it.startsWith("sessionKey=;") && "Domain=.claude.ai" in it })
        assertTrue(headers.any { it.startsWith("lastActiveOrg=;") && "Domain=claude.ai" in it })
        assertFalse(headers.any { it.startsWith("cf_clearance=;") })
        assertFalse(headers.any { it.startsWith("__cf_bm=;") })
        assertTrue(requests.any { it.url == "https://claude.ai/" })
        assertTrue(requests.any { it.url == "https://www.claude.ai/" })
    }
}
