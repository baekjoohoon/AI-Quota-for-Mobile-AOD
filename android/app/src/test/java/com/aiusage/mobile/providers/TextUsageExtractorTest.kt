package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.UsageSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUsageExtractorTest {
    @Test
    fun extractsPercentLeftAsConnectedIdleUsageLine() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CLAUDE,
            visibleText = "Plan\n80% left\nResets in 3 hours"
        )

        assertEquals(ProviderId.CLAUDE, snapshot.providerId)
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals(ProviderRefreshState.IDLE, snapshot.refreshState)
        assertEquals(1, snapshot.lines.size)
        assertEquals("Usage", snapshot.lines.single().label)
        assertEquals(0.8f, snapshot.lines.single().remainingPercent)
        assertEquals("80% left", snapshot.lines.single().remainingText)
        assertEquals("Resets in 3 hours", snapshot.lines.single().resetText)
        assertEquals(UsageSeverity.NORMAL, snapshot.lines.single().severity)
    }

    @Test
    fun extractsPercentRemainingAndWarningSeverity() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.GEMINI,
            visibleText = "You have 25% remaining until reset tomorrow"
        )

        val line = snapshot.lines.single()
        assertEquals(0.25f, line.remainingPercent)
        assertEquals("25% remaining", line.remainingText)
        assertEquals(UsageSeverity.WARNING, line.severity)
        assertEquals("Reset tomorrow", line.resetText)
    }

    @Test
    fun marksLowRemainingAsDangerSeverity() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CODEX,
            visibleText = "Only 14% left"
        )

        assertEquals(UsageSeverity.DANGER, snapshot.lines.single().severity)
    }

    @Test
    fun returnsUnavailableSnapshotWhenNoUsageTextIsVisible() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CURSOR,
            visibleText = "Dashboard settings and billing"
        )

        assertEquals(ProviderId.CURSOR, snapshot.providerId)
        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertEquals(ProviderRefreshState.IDLE, snapshot.refreshState)
        assertTrue(snapshot.lines.isEmpty())
        assertNotNull(snapshot.message)
        assertTrue(snapshot.message!!.contains("No visible usage limit"))
    }

    @Test
    fun ignoresBlankResetTextWhenUsageIsFound() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = "35% left"
        )

        assertEquals(UsageSeverity.NORMAL, snapshot.lines.single().severity)
        assertNull(snapshot.lines.single().resetText)
    }

    @Test
    fun extractsUsedOfLimitQuotaText() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = "Premium requests\n15 of 50 premium requests used\nResets next month"
        )

        val line = snapshot.lines.single()
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Premium requests", line.label)
        assertEquals(0.7f, line.remainingPercent)
        assertEquals("35 of 50 left", line.remainingText)
        assertEquals("15 used of 50", line.detailText)
        assertEquals(UsageSeverity.NORMAL, line.severity)
    }

    @Test
    fun extractsTextOnlyRemainingCount() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CODEX,
            visibleText = "Model limit\n3 messages remaining\nReset tomorrow"
        )

        val line = snapshot.lines.single()
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Messages", line.label)
        assertNull(line.remainingPercent)
        assertEquals("3 messages remaining", line.remainingText)
        assertEquals("Reset tomorrow", line.resetText)
    }

    @Test
    fun preservesSubscriptionPlanWithoutInventingUsageLine() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CODEX,
            visibleText = "localStorage:billing={\"subscription\":{\"plan\":\"Plus\"}}"
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Plus", snapshot.planLabel)
        assertTrue(snapshot.lines.isEmpty())
        assertNotNull(snapshot.message)
    }

    @Test
    fun ignoresFreePlanFallbackWhenCountersAreNotExposed() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CODEX,
            visibleText = """
                {
                  "s": "s",
                  "provider": "codex",
                  "d": {
                    "p": "Free",
                    "x": []
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Free", snapshot.planLabel)
        assertTrue(snapshot.lines.isEmpty())
    }

    @Test
    fun doesNotInventLimitRowsWhenAuthenticatedProviderSessionHasNoCounters() {
        val claude = TextUsageExtractor.extract(
            providerId = ProviderId.CLAUDE,
            visibleText = authenticatedEmptyPayload("claude", "Free")
        )
        val codex = TextUsageExtractor.extract(
            providerId = ProviderId.CODEX,
            visibleText = authenticatedEmptyPayload("codex", "Free")
        )
        val gemini = TextUsageExtractor.extract(
            providerId = ProviderId.GEMINI,
            visibleText = authenticatedEmptyPayload("gemini", "Basic")
        )
        val copilot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = authenticatedEmptyPayload("copilot", "Free")
        )

        listOf(claude, codex, gemini, copilot).forEach { snapshot ->
            assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
            assertTrue(snapshot.lines.none { it.sourceLabel?.contains("subscription default") == true })
            assertTrue(snapshot.lines.isEmpty())
            assertNotNull(snapshot.message)
        }
    }

    @Test
    fun keepsOnlyPlanLabelWhenPaidProviderSessionHasNoCounters() {
        val claude = TextUsageExtractor.extract(
            providerId = ProviderId.CLAUDE,
            visibleText = authenticatedEmptyPayload("claude", "Claude Max")
        )
        val codex = TextUsageExtractor.extract(
            providerId = ProviderId.CODEX,
            visibleText = authenticatedEmptyPayload("codex", "Pro")
        )
        val gemini = TextUsageExtractor.extract(
            providerId = ProviderId.GEMINI,
            visibleText = authenticatedEmptyPayload("gemini", "Google AI Pro")
        )

        assertEquals("Claude Max", claude.planLabel)
        assertEquals("Pro", codex.planLabel)
        assertEquals("Google AI Pro", gemini.planLabel)
        assertTrue(claude.lines.isEmpty())
        assertTrue(codex.lines.isEmpty())
        assertTrue(gemini.lines.isEmpty())
        assertNotNull(claude.message)
        assertNotNull(codex.message)
        assertNotNull(gemini.message)
    }

    @Test
    fun doesNotDeriveGeminiLimitRowsWhenAuthenticatedSessionExposesNoCounters() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.GEMINI,
            visibleText = """
                {
                  "s": "s",
                  "provider": "gemini",
                  "c": {
                    "authenticatedApp": true
                  },
                  "d": {
                    "x": []
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
    }

    @Test
    fun doesNotDeriveCopilotLimitRowsWhenAuthenticatedSessionExposesNoCounters() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = """
                {
                  "s": "s",
                  "provider": "copilot",
                  "c": {
                    "authenticatedApp": true
                  },
                  "d": {
                    "x": []
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
    }

    @Test
    fun extractsStructuredRemainingOnlyLimit() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CODEX,
            visibleText = """
                {
                  "s": "s",
                  "provider": "codex",
                  "d": {
                    "x": [
                      {
                        "l": "GPT-5 messages",
                        "remaining": 12,
                        "unit": "messages",
                        "window": "daily",
                        "source": "backend-api/accounts/check"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("GPT-5 messages", line.label)
        assertNull(line.remainingPercent)
        assertEquals("12 messages left", line.remainingText)
        assertEquals(12.0, line.remainingAmount ?: -1.0, 0.0001)
        assertEquals("messages", line.unit)
        assertEquals("daily", line.windowText)
    }

    @Test
    fun extractsStructuredBridgeUsageResponseFromProviderScript() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CLAUDE,
            visibleText = """
                {
                  "s": "s",
                  "d": {
                    "p": "Pro",
                    "l": 1778836860000,
                    "x": [
                      {
                        "l": "Weekly limits",
                        "u": 65.0,
                        "r": 1778880000000,
                        "t": "resets tomorrow"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Pro", snapshot.planLabel)
        assertEquals("Weekly limits", line.label)
        assertEquals(0.35f, line.remainingPercent)
        assertEquals("35% left", line.remainingText)
        assertEquals("65% used", line.detailText)
        assertEquals("resets tomorrow", line.resetText)
        assertEquals(UsageSeverity.WARNING, line.severity)
    }

    @Test
    fun extractsRemainingFirstLimitDetailsFromStructuredPayload() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = """
                {
                  "s": "s",
                  "provider": "copilot",
                  "d": {
                    "p": "Pro",
                    "x": [
                      {
                        "l": "Premium requests",
                        "used": 120,
                        "limit": 300,
                        "remaining": 180,
                        "unit": "premium requests",
                        "category": "premium_requests",
                        "window": "monthly",
                        "startsAt": "2026-05-01T00:00:00Z",
                        "resetsAt": "2026-06-01T00:00:00Z",
                        "t": "Resets Jun 1",
                        "source": "settings/copilot",
                        "confidence": 0.92
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Premium requests", line.label)
        assertEquals(0.6f, line.remainingPercent)
        assertEquals("180 of 300 premium requests left", line.remainingText)
        assertEquals("120 used of 300", line.detailText)
        assertEquals("premium_requests", line.category)
        assertEquals("premium requests", line.unit)
        assertEquals(120.0, line.usedAmount ?: -1.0, 0.0001)
        assertEquals(300.0, line.limitAmount ?: -1.0, 0.0001)
        assertEquals(180.0, line.remainingAmount ?: -1.0, 0.0001)
        assertEquals("monthly", line.windowText)
        assertEquals("2026-05-01T00:00:00Z", line.startsAt)
        assertEquals("2026-06-01T00:00:00Z", line.resetsAt)
        assertEquals("settings/copilot", line.sourceLabel)
        assertEquals(0.92f, line.confidence ?: -1f, 0.0001f)
    }

    @Test
    fun extractsSageApkStyleStructuredLimitTimestamps() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CLAUDE,
            visibleText = """
                {
                  "s": "s",
                  "provider": "claude",
                  "d": {
                    "x": [
                      {
                        "l": "Weekly messages",
                        "u": 40,
                        "s": 1777593600000,
                        "r": 1780272000000,
                        "t": "Resets Jun 1"
                      }
                    ],
                    "l": 1780000000000
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals("Weekly messages", line.label)
        assertEquals(0.6f, line.remainingPercent)
        assertEquals("Resets Jun 1", line.resetText)
        assertEquals("2026-05-01T00:00:00Z", line.startsAt)
        assertEquals("2026-06-01T00:00:00Z", line.resetsAt)
    }

    @Test
    fun extractsClaudeOrganizationUsageApiResponse() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CLAUDE,
            visibleText = """
                {
                  "five_hour": {
                    "utilization": 0.0,
                    "resets_at": null
                  },
                  "seven_day": {
                    "utilization": 4.0,
                    "resets_at": "2026-05-18T20:00:00.159005+00:00"
                  },
                  "seven_day_omelette": {
                    "utilization": 0.0,
                    "resets_at": null
                  },
                  "extra_usage": {
                    "is_enabled": false,
                    "monthly_limit": null
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals(3, snapshot.lines.size)
        assertEquals("Five_hour", snapshot.lines[0].label)
        assertEquals(1f, snapshot.lines[0].remainingPercent)
        assertEquals("Seven_day", snapshot.lines[1].label)
        assertEquals(0.96f, snapshot.lines[1].remainingPercent ?: -1f, 0.0001f)
        assertEquals("2026-05-18T20:00:00.159005+00:00", snapshot.lines[1].resetsAt)
        assertEquals("Seven_day_omelette", snapshot.lines[2].label)
    }

    @Test
    fun prefersClaudeOrganizationUsageApiLinesOverGenericRateLimitFragments() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CLAUDE,
            visibleText = """
                {
                  "s": "s",
                  "provider": "claude",
                  "d": {
                    "p": "Pro",
                    "x": [
                      {
                        "l": "rate_limit",
                        "remaining": 1,
                        "r": "2026-05-19T00:34:45.935812Z",
                        "source": "/new"
                      },
                      {
                        "l": "Five_hour",
                        "u": 0,
                        "r": "2026-05-18T12:00:00Z",
                        "source": "/api/organizations/:id/usage"
                      },
                      {
                        "l": "Seven_day",
                        "u": 4,
                        "r": "2026-05-18T20:00:00Z",
                        "source": "/api/organizations/:id/usage"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        assertEquals(listOf("Five_hour", "Seven_day"), snapshot.lines.map { it.label })
        assertEquals(listOf("100% left", "96% left"), snapshot.lines.map { it.remainingText })
        assertEquals("5 hours", snapshot.lines[0].windowText)
        assertEquals("7 days", snapshot.lines[1].windowText)
    }

    @Test
    fun extractsCopilotEntitlementUsageApiResponse() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = """
                {
                  "licenseType": "licensed_limited",
                  "plan": "free",
                  "quotas": {
                    "limits": {
                      "premiumInteractions": 0,
                      "completions": 4000
                    },
                    "remaining": {
                      "chat": 500,
                      "completions": 4000,
                      "premiumInteractions": 0,
                      "chatPercentage": 100.0,
                      "premiumInteractionsPercentage": 0.0
                    },
                    "resetDate": "2026-06-15",
                    "overagesEnabled": false
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Free", snapshot.planLabel)
        assertEquals(2, snapshot.lines.size)
        assertEquals("Chat", snapshot.lines[0].label)
        assertEquals(1f, snapshot.lines[0].remainingPercent)
        assertEquals("500 messages left", snapshot.lines[0].remainingText)
        assertEquals("2026-06-15T00:00:00Z", snapshot.lines[0].resetsAt)
        assertEquals("Completions", snapshot.lines[1].label)
        assertEquals(1f, snapshot.lines[1].remainingPercent)
        assertEquals("4,000 of 4,000 completions left", snapshot.lines[1].remainingText)
    }

    @Test
    fun derivesCopilotFreeCompletionLimitWhenEntitlementOmitsLimit() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = """
                {
                  "licenseType": "licensed_limited",
                  "plan": "free",
                  "quotas": {
                    "limits": {
                      "premiumInteractions": 0
                    },
                    "remaining": {
                      "chat": 500,
                      "completions": 3200,
                      "chatPercentage": 100.0
                    },
                    "resetDate": "2026-06-15"
                  }
                }
            """.trimIndent()
        )

        val completions = snapshot.lines.first { it.label == "Completions" }
        assertEquals(0.8f, completions.remainingPercent ?: -1f, 0.0001f)
        assertEquals("3,200 of 4,000 completions left", completions.remainingText)
        assertEquals("800 used of 4,000", completions.detailText)
    }

    @Test
    fun derivesCopilotCompletionLimitWhenEntitlementOmitsPlanAndLimit() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = """
                {
                  "quotas": {
                    "limits": {
                      "premiumInteractions": 0
                    },
                    "remaining": {
                      "chat": 500,
                      "completions": 4000,
                      "chatPercentage": 100.0
                    },
                    "resetDate": "2026-06-15"
                  }
                }
            """.trimIndent()
        )

        val completions = snapshot.lines.first { it.label == "Completions" }
        assertEquals(1f, completions.remainingPercent ?: -1f, 0.0001f)
        assertEquals("4,000 of 4,000 completions left", completions.remainingText)
        assertEquals("0 used of 4,000", completions.detailText)
    }

    @Test
    fun extractsGeminiQuotaRowsFromStructuredCollectorPayload() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.GEMINI,
            visibleText = """
                {
                  "s": "s",
                  "provider": "gemini",
                  "d": {
                    "x": [
                      {
                        "l": "Flash",
                        "used": 0,
                        "limit": 25,
                        "remaining": 25,
                        "unit": "requests",
                        "category": "usage_window",
                        "window": "daily",
                        "r": "2026-05-18T10:56:26.773Z",
                        "source": "/_/BardChatUi/data/batchexecute CheckGeminiQuota action 4",
                        "confidence": 0.97
                      },
                      {
                        "l": "Pro",
                        "used": 0,
                        "limit": 5,
                        "remaining": 5,
                        "unit": "requests",
                        "category": "usage_window",
                        "window": "daily",
                        "r": "2026-05-18T10:56:26.773Z",
                        "source": "/_/BardChatUi/data/batchexecute CheckGeminiQuota action 3",
                        "confidence": 0.97
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Free", snapshot.planLabel)
        assertEquals(listOf("Pro", "Flash"), snapshot.lines.take(2).map { it.label })
        assertEquals(1f, snapshot.lines[0].remainingPercent)
        assertEquals("5 of 5 requests left", snapshot.lines[0].remainingText)
        assertEquals("2026-05-18T10:56:26.773Z", snapshot.lines[0].resetsAt)
    }

    @Test
    fun derivesRemainingLimitDetailsWhenOnlyUsedAndLimitArePresent() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CURSOR,
            visibleText = """
                {
                  "s": "s",
                  "provider": "cursor",
                  "d": {
                    "x": [
                      {
                        "l": "Included usage",
                        "used": 17.5,
                        "limit": 20,
                        "unit": "USD",
                        "category": "included_usage"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals(0.125f, line.remainingPercent)
        assertEquals("2.5 of 20 USD left", line.remainingText)
        assertEquals("17.5 used of 20", line.detailText)
        assertEquals(UsageSeverity.DANGER, line.severity)
    }

    @Test
    fun extractsCursorRequestUsageDerivedFromApiUsagePayload() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CURSOR,
            visibleText = """
                {
                  "s": "s",
                  "provider": "cursor",
                  "d": {
                    "x": [
                      {
                        "l": "gpt-4",
                        "used": 18,
                        "limit": 20,
                        "unit": "requests",
                        "category": "fast_requests",
                        "source": "/api/usage"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Total usage", line.label)
        assertEquals(0.1f, line.remainingPercent)
        assertEquals("2 of 20 requests left", line.remainingText)
        assertEquals("18 used of 20", line.detailText)
        assertEquals("fast_requests", line.category)
        assertEquals("/api/usage", line.sourceLabel)
    }

    @Test
    fun extractsCursorIncludedUsageWhenPlanLimitIsZero() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CURSOR,
            visibleText = """
                {
                  "s": "s",
                  "provider": "cursor",
                  "d": {
                    "p": "Free",
                    "x": [
                      {
                        "l": "Included usage",
                        "limit": 0,
                        "remaining": 0,
                        "unit": "USD",
                        "category": "included_usage",
                        "window": "monthly",
                        "source": "/api/usage-summary",
                        "confidence": 0.9
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Free", snapshot.planLabel)
        assertEquals("Total usage", line.label)
        assertEquals(1f, line.remainingPercent)
        assertEquals("100% left", line.remainingText)
        assertEquals("usage_window", line.category)
    }

    @Test
    fun extractsCursorPlanUsageAsTotalUsage() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CURSOR,
            visibleText = """
                {
                  "s": "s",
                  "provider": "cursor",
                  "d": {
                    "p": "Pro",
                    "x": [
                      {
                        "l": "Total usage",
                        "used": 12,
                        "limit": 24,
                        "remaining": 12,
                        "unit": "USD",
                        "category": "included_usage",
                        "window": "monthly",
                        "resetsAt": "2026-06-17T06:32:31Z",
                        "source": "/api/usage-summary"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals("Pro", snapshot.planLabel)
        assertEquals("Total usage", line.label)
        assertEquals(0.5f, line.remainingPercent)
        assertEquals("12 of 24 USD left", line.remainingText)
        assertEquals("12 used of 24", line.detailText)
        assertEquals("included_usage", line.category)
        assertEquals("2026-06-17T06:32:31Z", line.resetsAt)
    }

    @Test
    fun prefersCursorTotalUsageGaugeOverRemainingOnlyFallback() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CURSOR,
            visibleText = """
                {
                  "s": "s",
                  "provider": "cursor",
                  "d": {
                    "p": "Free",
                    "x": [
                      {
                        "l": "Total usage",
                        "remaining": 10,
                        "unit": "USD",
                        "category": "included_usage",
                        "window": "monthly",
                        "source": "/dashboard"
                      },
                      {
                        "l": "Total usage",
                        "u": 0,
                        "window": "monthly",
                        "startsAt": "2026-05-17T06:32:31Z",
                        "resetsAt": "2026-06-17T06:32:31Z",
                        "source": "/api/usage-summary"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val line = snapshot.lines.single()
        assertEquals("Total usage", line.label)
        assertEquals(1f, line.remainingPercent)
        assertEquals("100% left", line.remainingText)
        assertEquals("2026-06-17T06:32:31Z", line.resetsAt)
    }

    @Test
    fun ignoresGithubSitemapNumbersFromStructuredPayload() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = """
                {
                  "s": "s",
                  "provider": "copilot",
                  "d": {
                    "x": [
                      {
                        "l": "Sitemap",
                        "used": 2,
                        "limit": 2024,
                        "remaining": 2022,
                        "unit": "Sitemap",
                        "source": "/settings/copilot/usage"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
    }

    @Test
    fun ignoresCopilotMarketingPlanPageNumbersFromStructuredPayload() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = """
                {
                  "s": "s",
                  "provider": "copilot",
                  "d": {
                    "p": "Pro",
                    "x": [
                      {
                        "l": "Blogs.microsoft.com",
                        "used": 7,
                        "limit": 7,
                        "remaining": 0,
                        "unit": "count",
                        "source": "/features/copilot/plans"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
    }

    @Test
    fun ignoresCursorCompletedCountersFromStructuredPayload() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CURSOR,
            visibleText = """
                {
                  "s": "s",
                  "provider": "cursor",
                  "d": {
                    "p": "Free",
                    "x": [
                      {
                        "l": "Completed",
                        "used": 0,
                        "limit": 4,
                        "remaining": 4,
                        "unit": "Completed",
                        "source": "/dashboard"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
    }

    private fun authenticatedEmptyPayload(providerId: String, plan: String): String {
        return """
            {
              "s": "s",
              "provider": "$providerId",
              "c": {
                "authenticatedApp": true
              },
              "d": {
                "p": "$plan",
                "x": []
              }
            }
        """.trimIndent()
    }
}
