package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.net.URI
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLoginUrlRewriterTest {
    @Test
    fun addsSelectAccountPromptToClaudeGoogleOAuthStart() {
        val original = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback" +
            "&scope=openid"

        val rewritten = ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, original)

        assertNotNull(rewritten)
        val uri = URI(rewritten!!)
        assertTrue(uri.toString().startsWith("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(uri.rawQuery.contains("prompt=select_account"))
        assertTrue(uri.rawQuery.contains("redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback"))
    }

    @Test
    fun addsSelectAccountPromptToClaudeRegionalGoogleOAuthStart() {
        val original = "https://accounts.google.co.kr/o/oauth2/v2/auth" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback" +
            "&scope=openid"

        val rewritten = ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, original)

        assertNotNull(rewritten)
        assertTrue(URI(rewritten!!).rawQuery.contains("prompt=select_account"))
    }

    @Test
    fun keepsExistingPromptValuesWhenAddingClaudeAccountSelection() {
        val original = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback" +
            "&prompt=consent"

        val rewritten = ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, original)

        assertTrue(URI(rewritten!!).rawQuery.contains("prompt=select_account%20consent"))
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
                "$original&prompt=select_account"
            )
        )
    }

    @Test
    fun doesNotRewriteOrdinaryClaudeOrGooglePages() {
        assertNull(ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertNull(ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, "https://myaccount.google.com/"))
    }
}
