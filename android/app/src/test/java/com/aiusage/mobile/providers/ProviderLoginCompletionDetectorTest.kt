package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLoginCompletionDetectorTest {
    @Test
    fun detectsClaudeAppAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CLAUDE,
                url = "https://claude.ai/",
                visibleText = "Claude\nNew chat\nMessage Claude\nProjects"
            )
        )
    }

    @Test
    fun detectsClaudeKoreanAppShellAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CLAUDE,
                url = "https://claude.ai/new",
                visibleText = "Claude\n\u0043laude\uc640 \ud568\uaed8 \ucee4\ud53c \ud55c\uc794\uc5b4\ub5a0\uc138\uc694?\n\uc0c8 \ucc44\ud305\n\uac80\uc0c9\n\ud504\ub85c\uc81d\ud2b8\n\ucf54\ub4dc"
            )
        )
    }

    @Test
    fun rejectsClaudeLoggedOutLandingOrLoginPage() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CLAUDE,
                url = "https://claude.ai/",
                visibleText = "Claude\n로그인 또는 회원가입\nGoogle 계정으로 계속하기\nApple 계정으로 계속하기"
            )
        )
    }

    @Test
    fun detectsCodexChatGptAppAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://chatgpt.com/",
                visibleText = "ChatGPT\n새 채팅\n라이브러리\n프로젝트\nMessage ChatGPT"
            )
        )
    }

    @Test
    fun detectsCodexKoreanChatGptComposerAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://chatgpt.com/",
                visibleText = "ChatGPT\n\uc0c8 \ucc44\ud305\n\ub77c\uc774\ube0c\ub7ec\ub9ac\n\ud504\ub85c\uc81d\ud2b8\n\ubb34\uc5c7\uc744 \ub3c4\uc640\ub4dc\ub9b4\uae4c\uc694?"
            )
        )
    }

    @Test
    fun rejectsCodexPublicChatGptHomeWithoutAuthenticatedNavigation() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://chatgpt.com/",
                visibleText = "ChatGPT\n준비되면 얘기해 주세요.\n무엇이든 물어보세요"
            )
        )
    }

    @Test
    fun rejectsCodexPlatformUsagePage() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://platform.openai.com/usage",
                visibleText = "OpenAI Platform\nUsage\nLimits\nSettings\nOrganization"
            )
        )
    }

    @Test
    fun rejectsCodexLoginPage() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://auth.openai.com/authorize",
                visibleText = "Log in\nEmail address\nContinue with Google"
            )
        )
    }

    @Test
    fun rejectsCodexKoreanLoginPageOnChatGpt() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://chatgpt.com/",
                visibleText = "ChatGPT\n로그인 또는 회원가입\nGoogle 계정으로 계속하기\nApple 계정으로 계속하기\n이메일 주소"
            )
        )
    }

    @Test
    fun detectsGeminiAppAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.GEMINI,
                url = "https://gemini.google.com/app",
                visibleText = "Gemini\nGemini에게 물어보기\n새 채팅"
            )
        )
    }

    @Test
    fun detectsGeminiKoreanAppShellAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.GEMINI,
                url = "https://gemini.google.com/app",
                visibleText = "\u0047oogle \u0047emini\n\u0047emini\uc640\uc758 \ub300\ud654\n\uc0ac\uc6a9\uc790\ub2d8, \uc548\ub155\ud558\uc138\uc694\n\u0047emini\uc5d0\uac8c \ubb3c\uc5b4\ubcf4\uae30\n\uc0c8 \ucc44\ud305"
            )
        )
    }

    @Test
    fun rejectsGoogleAccountLoginPage() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.GEMINI,
                url = "https://accounts.google.com/",
                visibleText = "로그인\n이메일 또는 휴대전화\n비밀번호"
            )
        )
    }

    @Test
    fun detectsCopilotSettingsAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.COPILOT,
                url = "https://github.com/settings/copilot",
                visibleText = "GitHub Copilot\nCopilot settings\nBilling"
            )
        )
    }

    @Test
    fun detectsCopilotSettingsAfterLoginWhenGenericSignInTextIsPresent() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.COPILOT,
                url = "https://github.com/settings/billing",
                visibleText = """
                    GitHub Copilot
                    Billing and licensing
                    Premium request analytics
                    Copilot Premium Request
                    Sign in with a different account
                """.trimIndent()
            )
        )
    }

    @Test
    fun rejectsGithubSignInPage() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.COPILOT,
                url = "https://github.com/login",
                visibleText = "Sign in to GitHub\nUsername or email address\nPassword"
            )
        )
    }

    @Test
    fun rejectsOtherProviderHost() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CURSOR,
                url = "https://chatgpt.com/",
                visibleText = "Cursor dashboard usage settings"
            )
        )
    }

    @Test
    fun rejectsCursorSignInLandingPage() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CURSOR,
                url = "https://cursor.com/dashboard",
                visibleText = """
                    Welcome to Cursor
                    Sign in
                    Email
                    Continue with Google
                    Continue with GitHub
                    Continue with Apple
                    Don't have an account?
                    Sign up
                """.trimIndent()
            )
        )
    }

    @Test
    fun detectsCursorDashboardAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CURSOR,
                url = "https://cursor.com/dashboard",
                visibleText = "Cursor dashboard usage settings account workspace billing"
            )
        )
    }

    @Test
    fun detectsStructuredProviderExtractorPayloadAfterLogin() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://chatgpt.com/",
                visibleText = """
                    {
                      "s": "s",
                      "provider": "codex",
                      "c": {
                        "login": false,
                        "providerPage": true,
                        "authenticatedApp": true,
                        "textLength": 140
                      },
                      "d": {
                        "x": []
                      }
                    }
                """.trimIndent()
            )
        )
    }

    @Test
    fun rejectsStructuredCodexPayloadWithoutAuthenticatedAppMarkerOrUsageData() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://chatgpt.com/",
                visibleText = """
                    {
                      "s": "s",
                      "provider": "codex",
                      "c": {
                        "login": false,
                        "providerPage": true,
                        "authenticatedApp": false,
                        "textLength": 140
                      },
                      "d": {
                        "x": []
                      }
                    }
                """.trimIndent()
            )
        )
    }

    @Test
    fun detectsStructuredCodexPayloadWhenUsageDataExists() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://chatgpt.com/",
                visibleText = """
                    {
                      "s": "s",
                      "provider": "codex",
                      "c": {
                        "login": false,
                        "providerPage": true,
                        "authenticatedApp": false,
                        "textLength": 140
                      },
                      "d": {
                        "x": [
                          {"l":"Messages","remaining":12,"unit":"messages"}
                        ]
                      }
                    }
                """.trimIndent()
            )
        )
    }

    @Test
    fun detectsStructuredCursorPayloadWithHiddenDomTextAndAuthenticatedEndpoint() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CURSOR,
                url = "https://cursor.com/dashboard",
                visibleText = """
                    {
                      "s": "s",
                      "provider": "cursor",
                      "c": {
                        "login": false,
                        "providerPage": true,
                        "authenticatedApp": true,
                        "textLength": 0
                      },
                      "d": {
                        "x": []
                      }
                    }
                """.trimIndent()
            )
        )
    }

    @Test
    fun detectsStructuredCursorPayloadWithUsageDataAndHiddenDomText() {
        assertTrue(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CURSOR,
                url = "https://cursor.com/dashboard",
                visibleText = """
                    {
                      "s": "s",
                      "provider": "cursor",
                      "c": {
                        "login": false,
                        "providerPage": true,
                        "authenticatedApp": false,
                        "textLength": 0
                      },
                      "d": {
                        "x": [
                          {"l":"gpt-4","used":18,"limit":20,"unit":"requests"}
                        ]
                      }
                    }
                """.trimIndent()
            )
        )
    }

    @Test
    fun rejectsStructuredProviderExtractorPayloadOnLoginPage() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CODEX,
                url = "https://auth.openai.com/authorize",
                visibleText = """
                    {
                      "s": "s",
                      "provider": "codex",
                      "c": {
                        "login": true,
                        "providerPage": false,
                        "textLength": 120
                      },
                      "d": {
                        "x": []
                      }
                    }
                """.trimIndent()
            )
        )
    }

    @Test
    fun rejectsStructuredClaudePayloadWithoutAuthenticatedAppMarker() {
        assertFalse(
            ProviderLoginCompletionDetector.isLoginComplete(
                providerId = ProviderId.CLAUDE,
                url = "https://claude.ai/",
                visibleText = """
                    {
                      "s": "s",
                      "provider": "claude",
                      "c": {
                        "login": false,
                        "providerPage": true,
                        "textLength": 240
                      },
                      "d": {
                        "x": []
                      }
                    }
                """.trimIndent()
            )
        )
    }
}
