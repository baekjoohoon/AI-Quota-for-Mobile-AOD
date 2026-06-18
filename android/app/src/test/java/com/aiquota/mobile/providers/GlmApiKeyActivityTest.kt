package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlmApiKeyActivityTest {
    @Test
    fun glmConnectionScreenUsesAppThemeInsteadOfPlainPlatformButtons() {
        val source = File("src/main/java/com/aiquota/mobile/providers/GlmApiKeyActivity.kt").readText()

        assertTrue(
            "GLM connection screen should use the active AI Quota theme.",
            source.contains("widgetConfigureStyle") &&
                source.contains("ThemePreferencesRepository") &&
                source.contains("applyWidgetConfigureWindow")
        )
        assertTrue(
            "GLM connection screen should render a themed provider card with the official provider icon.",
            source.contains("providerIconRes(ProviderId.GLM)") &&
                source.contains("widgetConfigureRoundedBackground") &&
                source.contains("ScrollView")
        )
        assertTrue(
            "Primary and secondary actions should be themed TextView controls, not default platform buttons.",
            source.contains("fun themedAction") &&
                source.contains("style.accentColor") &&
                source.contains("style.buttonCornerRadiusDp")
        )
        assertFalse(
            "Default Android Button widgets make the screen visually inconsistent with the app.",
            source.contains("import android.widget.Button") ||
                source.contains("Button(this).apply")
        )
    }

    @Test
    fun glmConnectionScreenHasLocalizedCopy() {
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        listOf(
            "glm_connection_status_choose",
            "glm_connection_status_api_key_prompt",
            "glm_connection_web_oauth",
            "glm_connection_api_key_hint",
            "glm_connection_api_key_action",
            "glm_connection_cancel"
        ).forEach { key ->
            assertTrue("English strings should contain $key.", english.contains("name=\"$key\""))
            assertTrue("Korean strings should contain $key.", korean.contains("name=\"$key\""))
        }
        assertTrue(korean.contains(">웹 로그인으로 연결<"))
        assertTrue(english.contains(">Connect with web login<"))
    }

    @Test
    fun glmConnectionScreenAsksForApiKeyOnlyAfterApiKeyAction() {
        val source = File("src/main/java/com/aiquota/mobile/providers/GlmApiKeyActivity.kt").readText()

        assertTrue(
            "API key field should not be visible on the initial choice screen.",
            source.contains("apiKeyInput.visibility = View.GONE")
        )
        assertTrue(
            "API key action should first switch the screen into API key entry mode.",
            source.contains("private fun showApiKeyEntry") &&
                source.contains("onClick = ::showApiKeyEntry")
        )
        assertTrue(
            "The API key entry mode should reveal the input and then collect using the entered key.",
            source.contains("apiKeyInput.visibility = View.VISIBLE") &&
                source.contains("saveButton.setOnClickListener { saveAndCollect() }")
        )
    }
}
