package com.aiquota.mobile.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenCodeUsagePageRoutesTest {
    @Test
    fun workspaceShellRedirectsToGoUsagePage() {
        assertEquals(
            "https://opencode.ai/workspace/wrk_123/go",
            OpenCodeUsagePageRoutes.goUsageUrlFrom("https://opencode.ai/workspace/wrk_123")
        )
        assertEquals(
            "https://opencode.ai/workspace/wrk_123/go",
            OpenCodeUsagePageRoutes.goUsageUrlFrom("https://opencode.ai/workspace/wrk_123/usage")
        )
        assertEquals(
            "https://www.opencode.ai/workspace/wrk_123/go",
            OpenCodeUsagePageRoutes.goUsageUrlFrom("https://www.opencode.ai/workspace/wrk_123/settings")
        )
    }

    @Test
    fun goUsagePageDoesNotRedirectAgain() {
        assertNull(OpenCodeUsagePageRoutes.goUsageUrlFrom("https://opencode.ai/workspace/wrk_123/go"))
        assertNull(OpenCodeUsagePageRoutes.goUsageUrlFrom("https://opencode.ai/auth"))
        assertNull(OpenCodeUsagePageRoutes.goUsageUrlFrom("https://opencode.ai/docs/go/"))
        assertNull(OpenCodeUsagePageRoutes.goUsageUrlFrom("https://example.com/workspace/wrk_123"))
    }
}
