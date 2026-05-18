package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

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
    fun keepsExistingPromptValuesWhenAddingClaudeAccountSelection() {
        val original = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback" +
            "&prompt=consent"

        val rewritten = ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, original)

        assertTrue(URI(rewritten!!).rawQuery.contains("prompt=select_account%20consent"))
    }

    @Test
    fun removesClaudeGoogleAccountPinningParameters() {
        val original = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback" +
            "&login_hint=old%40example.com" +
            "&authuser=0" +
            "&hd=example.com" +
            "&prompt=select_account"

        val rewritten = ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, original)

        assertNotNull(rewritten)
        val rawQuery = URI(rewritten!!).rawQuery
        assertTrue(rawQuery.contains("prompt=select_account"))
        assertFalse(rawQuery.contains("login_hint="))
        assertFalse(rawQuery.contains("authuser="))
        assertFalse(rawQuery.contains("hd="))
        assertTrue(rawQuery.contains("redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback"))
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

    @Test
    fun doesNotRewriteGoogleAuthIntermediatePages() {
        val checkCookie = "https://accounts.google.com/CheckCookie" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback"
        val consent = "https://accounts.google.com/signin/oauth/consent" +
            "?client_id=anthropic" +
            "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fauth%2Fcallback"

        assertNull(ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, checkCookie))
        assertNull(ProviderLoginUrlRewriter.rewriteMainFrameUrl(ProviderId.CLAUDE, consent))
    }
}
