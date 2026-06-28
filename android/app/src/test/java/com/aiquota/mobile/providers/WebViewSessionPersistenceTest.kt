package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewSessionPersistenceTest {
    @Test
    fun sessionDestructiveWebViewAndCookieCallsAreConfinedToExplicitDisconnectCleaner() {
        val source = mainSourceText()
        val forbiddenCalls = listOf(
            "removeAllCookies",
            "removeSessionCookies",
            "WebStorage.getInstance().deleteAllData",
            "webStorage.deleteAllData",
            "clearCache(true)",
            "clearHistory()",
            "clearFormData()"
        )

        forbiddenCalls.forEach { forbidden ->
            assertFalse("$forbidden must not be used for provider session cleanup", source.contains(forbidden))
        }

        val cleaner = File("src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt").readText()
        assertTrue(cleaner.contains("clearProviderWebSessionCookies(cookieManager, providerId)"))
        assertTrue(cleaner.contains("clearProviderWebStorageOrigins(webStorage, providerId)"))
        assertTrue(cleaner.contains("webStorage.deleteOrigin(origin)"))
    }

    @Test
    fun loginSessionReachedWithoutUsageStillFlushesCookiesBeforeLeavingWebView() {
        val source = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val method = source.substringAfter("private fun finishConnectedWithoutUsage")
            .substringBefore("private fun failKeepingPrevious")

        assertTrue(method.contains("CookieManager.getInstance().flush()"))
    }

    @Test
    fun codexFreshLoginClearsCookiesAndWebStorageBeforeLoadingStartUrl() {
        val source = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val onCreate = source.substringAfter("override fun onCreate(savedInstanceState: Bundle?)")
            .substringBefore("override fun onDestroy()")

        assertTrue(onCreate.contains("ProviderWebSessionClearPolicy.shouldClearBeforeLogin(providerId, previousConnectionState)"))
        assertTrue(onCreate.contains("clearProviderWebSession(cookieManager, providerId)"))
        assertTrue(onCreate.indexOf("clearProviderWebSession(cookieManager, providerId)") < onCreate.indexOf("webView.loadUrl(requestedStartUrl)"))
        assertTrue(source.contains("WebStorage.getInstance()"))
        assertTrue(source.contains("ProviderWebSessionCleaner.clearProviderWebSession(cookieManager, WebStorage.getInstance(), providerId)"))
    }

    private fun mainSourceText(excludeFileNames: Set<String> = emptySet()): String {
        return File("src/main")
            .walkTopDown()
            .filter { it.isFile && it.name !in excludeFileNames && (it.extension == "kt" || it.name == "AndroidManifest.xml") }
            .joinToString("\n") { it.readText() }
    }
}
