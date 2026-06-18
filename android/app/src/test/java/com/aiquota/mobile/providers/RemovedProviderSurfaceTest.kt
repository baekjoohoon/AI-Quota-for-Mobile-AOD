package com.aiquota.mobile.providers

import org.junit.Assert.assertFalse
import org.junit.Test

class RemovedProviderSurfaceTest {
    @Test
    fun productCodeDoesNotExposeRemovedProviderSurface() {
        val productSource = java.io.File("src/main").walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .joinToString("\n") { it.readText() }

        val removedTokens = listOf(
            "CODEX_" + "BUSINESS",
            "Codex" + "Business",
            "codex" + "-business",
            "codex" + "_business",
            "Codex " + "Business"
        )

        removedTokens.forEach { token ->
            assertFalse(productSource.contains(token))
        }
    }
}
