package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ProviderLoginUrlRewriterTest {
    @Test
    fun rewritesClaudeGoogleOAuthStartToAccountChooser() {
        val original = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback" +
            "&scope=openid"

        val rewritten = ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, original)

        assertNotNull(rewritten)
        val uri = URI(rewritten!!)
        assertEquals("https", uri.scheme)
        assertEquals("accounts.google.com", uri.host)
        assertEquals("/AccountChooser", uri.path)
        assertTrue(uri.rawQuery.contains("hl=ko"))

        val continueUrl = queryValue(uri, "continue")
        assertTrue(continueUrl.startsWith("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(continueUrl.contains("prompt=select_account"))
        assertTrue(continueUrl.contains("redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback"))
    }

    @Test
    fun keepsExistingPromptValuesWhenAddingClaudeAccountSelection() {
        val original = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback" +
            "&prompt=consent"

        val rewritten = ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, original)
        val continueUrl = queryValue(URI(rewritten!!), "continue")

        assertTrue(continueUrl.contains("prompt=select_account%20consent"))
    }

    @Test
    fun doesNotRewriteNonClaudeOrAlreadySelectableUrls() {
        val original = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback"

        assertNull(ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.GEMINI, original))
        assertNull(ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CURSOR, original))
        assertNull(
            ProviderLoginUrlRewriter.rewriteMainFrameUrl(
                ProviderId.CLAUDE,
                "https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fclaude.ai"
            )
        )
        assertNull(
            ProviderLoginUrlRewriter.rewriteMainFrameUrl(
                ProviderId.CLAUDE,
                "$original&prompt=select_account"
            )
        )
    }

    @Test
    fun doesNotRewriteOrdinaryClaudeOrGooglePages() {
        assertNull(ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertNull(ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, "https://myaccount.google.com/"))
    }

    private fun queryValue(uri: URI, key: String): String {
        return uri.rawQuery.split("&")
            .first { it.substringBefore("=") == key }
            .substringAfter("=")
            .let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
    }
}
