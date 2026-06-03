package com.aiquota.mobile.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleOAuthLoopbackNetworkSecurityTest {
    @Test
    fun manifestPermitsCleartextOnlyForOAuthLoopbackRedirects() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val networkConfig = java.io.File("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(networkConfig.contains("<domain includeSubdomains=\"false\">127.0.0.1</domain>"))
        assertTrue(networkConfig.contains("<domain includeSubdomains=\"false\">localhost</domain>"))
        assertTrue(networkConfig.contains("<domain-config cleartextTrafficPermitted=\"true\">"))
        assertFalse(manifest.contains("android:usesCleartextTraffic=\"true\""))
        assertFalse(networkConfig.contains("<base-config cleartextTrafficPermitted=\"true\""))
    }
}
