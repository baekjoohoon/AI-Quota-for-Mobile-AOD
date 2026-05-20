package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class ProviderUsageNormalizerTest {
    @Test
    fun claudeUsageApiNormalizesQuotaWindows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CLAUDE,
            """
            {
              "plan": "Pro",
              "usage": {
                "five_hour": {"utilization": 0.25, "resets_at": "2026-05-19T12:00:00Z"},
                "seven_day": {"utilization": 0.4},
                "seven_day_omelette": {"used_percent": 10}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals(ProviderRefreshState.IDLE, snapshot.refreshState)
        assertEquals("Pro", snapshot.plan)
        assertEquals(listOf("Claude Session", "Claude Weekly", "Claude Design"), snapshot.lines.map { it.label })
        assertEquals(0.75f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.60f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.90f, snapshot.lines[2].remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-05-19T12:00:00Z", snapshot.lines[0].resetsAt)
    }

    @Test
    fun claudeUsageApiNormalizesModelAndCoworkBuckets() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CLAUDE,
            """
            {
              "plan": "CLAUDE_MAX",
              "usage": {
                "session": {"used_percent": 20},
                "weekly": {"used_percent": 30},
                "opus": {"used_percent": 40},
                "sonnet": {"used_percent": 50},
                "cowork": {"used_percent": 60},
                "design": {"used_percent": 70}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("CLAUDE_MAX", snapshot.plan)
        assertEquals(
            listOf("Claude Session", "Claude Weekly", "Claude Opus", "Claude Sonnet", "Claude Cowork", "Claude Design"),
            snapshot.lines.map { it.label }
        )
    }

    @Test
    fun claudeRawRemainingAmountWithoutLimitIsRejected() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CLAUDE,
            """
            {
              "usage": {
                "session": {"remaining": 80}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )

        assertNull(snapshot)
    }

    @Test
    fun codexWhamNormalizesPlanClaimsAndWindows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "plan_type": "prolite",
              "rate_limits": {
                "primary_window": {"used_percent": 20, "reset_after_seconds": 18000},
                "secondary_window": {"used_percent": 50},
                "spark": {"used_percent": 3}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Pro 5x", snapshot.plan)
        assertEquals(listOf("Codex Session", "Codex Weekly", "Codex Spark"), snapshot.lines.map { it.label })
        assertEquals(0.80f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals("Resets in 5h", snapshot.lines[0].resetText)
        assertEquals(0.50f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.97f, snapshot.lines[2].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun codexWhamNormalizesSingularAndSiblingLimitShapes() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "plan": "pro",
              "rate_limit": {
                "primary_window": {"usedPercentage": 25, "reset_at": "2026-05-19T12:00:00Z"},
                "secondary_window": {"used_percentage": 75}
              },
              "code_review_rate_limit": {"percent_used": 10},
              "spark_rate_limit": {"used_percent": 15},
              "credits": {"balance": 80, "limit": 100}
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Pro 20x", snapshot.plan)
        assertEquals(listOf("Codex Session", "Codex Weekly", "Codex Code Review", "Codex Spark", "Codex Credits"), snapshot.lines.map { it.label })
        assertEquals(0.75f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-05-19T12:00:00Z", snapshot.lines[0].resetsAt)
    }

    @Test
    fun codexBareCreditsBalanceIsNotTreatedAsPercent() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "plan": "pro",
              "rate_limit": {
                "primary_window": {"used_percent": 25}
              },
              "credits": {"balance": 0}
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(listOf("Codex Session"), snapshot.lines.map { it.label })
    }

    @Test
    fun codexCreditsBalanceWithoutLimitIsRejected() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "credits": {"balance": 0}
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )

        assertNull(snapshot)
    }

    @Test
    fun codexStructuredPagePayloadNormalizesNestedUsageShape() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "account": "user@example.com",
              "usage": {
                "rate_limit": {
                  "primary_window": {"used_percent": 40},
                  "secondary_window": {"used_percent": 20}
                },
                "spark_rate_limits": {"usedPercentage": 5}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals("user@example.com", snapshot.account)
        assertEquals(listOf("Codex Session", "Codex Weekly", "Codex Spark"), snapshot.lines.map { it.label })
        assertEquals(0.60f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun geminiCompactPayloadNormalizesApkShape() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            """
            {
              "account": {"p": "GEMINI_PRO", "e": "user@example.com"},
              "usage": {
                "x": [
                  {"l": "Gemini Pro", "u": 0.02, "r": 1779160000000, "t": "Resets in 2h 42m"},
                  {"l": "Usage", "u": 0.5}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals("Gemini Pro", snapshot.plan)
        assertEquals("user@example.com", snapshot.account)
        assertEquals(listOf("Gemini Pro"), snapshot.lines.map { it.label })
        assertEquals(0.98f, snapshot.lines.single().remainingPercent ?: 0f, 0.001f)
        assertEquals("Resets in 2h 42m", snapshot.lines.single().resetText)
    }

    @Test
    fun geminiPayloadWithoutUsageMetricIsRejected() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            """
            {
              "account": {"p": "GEMINI_PRO", "e": "user@example.com"},
              "usage": {
                "x": [
                  {"l": "Gemini Pro", "t": "Quota is not exposed by the current Gemini web page."}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )

        assertNull(snapshot)
    }

    @Test
    fun geminiUnknownPlanAndUnsupportedUsageLabelsAreRejected() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            """
            {
              "account": {"p": "GEMINI_UNKNOWN", "e": "user@example.com"},
              "usage": {
                "x": [
                  {"l": "Gemini Web Session", "u": 0.1},
                  {"l": "Gemini Ultra", "u": 0.2}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )

        assertNull(snapshot)
    }

    @Test
    fun geminiCodeAssistQuotaBucketsNormalizeByModelFamily() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            """
            {
              "plan": "standard-tier",
              "account": "user@gmail.com",
              "limits": [
                {"modelId": "gemini-2.5-pro", "remainingFraction": 0.98, "resetTime": "2026-05-19T12:00:00Z"},
                {"modelId": "gemini-2.5-pro-preview", "remainingFraction": 0.75, "resetTime": "2026-05-19T11:00:00Z"},
                {"modelId": "gemini-2.5-flash", "remainingFraction": 0.99, "resetTime": "2026-05-19T10:00:00Z"}
              ]
            }
            """.trimIndent(),
            ProviderPayloadSource.NETWORK_RESPONSE
        )!!

        assertEquals("Paid", snapshot.plan)
        assertEquals("user@gmail.com", snapshot.account)
        assertEquals(listOf("Gemini Pro", "Gemini Flash"), snapshot.lines.map { it.label })
        assertEquals(0.75f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-05-19T11:00:00Z", snapshot.lines[0].resetsAt)
        assertEquals(0.99f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun geminiCliOAuthQuotaPayloadNormalizesTargetShape() {
        val payload = GeminiCliOAuthRepository.normalizeQuotaPayload(
            quotaJson = """
            {
              "buckets": [
                {"modelId": "gemini-2.5-flash", "remainingFraction": 0.99, "resetTime": "2026-05-20T14:42:49Z"},
                {"modelId": "gemini-2.5-pro", "remainingFraction": 0.98, "resetTime": "2026-05-20T14:42:49Z"},
                {"modelId": "gemini-2.5-pro-preview", "remainingFraction": 0.75, "resetTime": "2026-05-20T13:42:49Z"}
              ]
            }
            """.trimIndent(),
            account = "user@example.com",
            plan = "GEMINI_PRO"
        )!!
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            payload,
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Gemini Pro", snapshot.plan)
        assertEquals("user@example.com", snapshot.account)
        assertEquals(listOf("Gemini Pro", "Gemini Flash"), snapshot.lines.map { it.label })
        assertEquals(0.75f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(25, snapshot.lines[0].usedPercent)
        assertEquals(0.99f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
        assertEquals(1, snapshot.lines[1].usedPercent)
    }

    @Test
    fun geminiLoadCodeAssistTierControlsPlanLabel() {
        val plan = GeminiCliOAuthRepository.planFromLoadCodeAssist(
            """
            {
              "user": {
                "subscription": {
                  "tier": "free-tier"
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("GEMINI_FREE", plan)
    }

    @Test
    fun geminiCodeAssistRequestsCarryProjectContext() {
        val load = JSONObject(GeminiCliOAuthRepository.loadCodeAssistPayload("project-123"))
        val metadata = load.getJSONObject("metadata")
        val quota = JSONObject(GeminiCliOAuthRepository.retrieveUserQuotaPayload("project-123"))
        val onboard = JSONObject(GeminiCliOAuthRepository.onboardUserPayload("standard-tier", "project-123"))

        assertEquals("project-123", load.getString("cloudaicompanionProject"))
        assertEquals("project-123", metadata.getString("duetProject"))
        assertEquals("project-123", quota.getString("project"))
        assertEquals("standard-tier", onboard.getString("tierId"))
        assertEquals("project-123", onboard.getString("cloudaicompanionProject"))
    }

    @Test
    fun geminiStructuredCodeAssistPayloadUsesPaidTierAndTrustedQuotaBuckets() {
        val payload = GeminiCliOAuthRepository.structuredPayloadFromCodeAssist(
            loadJson = JSONObject(
                """
                {
                  "paidTier": {
                    "name": "gemini-codeassist-google-one-ai-pro"
                  },
                  "cloudaicompanionProject": "project-123"
                }
                """.trimIndent()
            ),
            quotaJson = JSONObject(
                """
                {
                  "buckets": [
                    {"modelId": "gemini-2.5-pro", "remainingFraction": 0.98, "resetTime": "2026-05-20T14:42:49Z"},
                    {"modelId": "gemini-2.5-flash", "remainingFraction": 0.99, "resetTime": "2026-05-20T14:42:49Z"}
                  ]
                }
                """.trimIndent()
            ),
            email = "user@example.com"
        )!!
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            payload,
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Gemini Pro", snapshot.plan)
        assertEquals("user@example.com", snapshot.account)
        assertEquals(listOf("Gemini Pro", "Gemini Flash"), snapshot.lines.map { it.label })
        assertEquals(0.98f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun geminiStructuredCodeAssistPayloadDoesNotInventUnknownPlan() {
        val payload = GeminiCliOAuthRepository.structuredPayloadFromCodeAssist(
            loadJson = JSONObject("{}"),
            quotaJson = JSONObject(
                """
                {
                  "buckets": [
                    {"modelId": "gemini-2.5-pro", "remainingFraction": 0.98}
                  ]
                }
                """.trimIndent()
            ),
            email = "user@example.com"
        )!!

        assertFalse(payload.contains("GEMINI_UNKNOWN"))
        assertFalse(payload.contains("Reset time unknown"))
        assertFalse(payload.contains("\"resetsAt\":0"))

        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            payload,
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertNull(snapshot.plan)
        assertEquals(listOf("Gemini Pro"), snapshot.lines.map { it.label })
        assertEquals(0.98f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertNull(snapshot.lines[0].resetsAt)
    }

    @Test
    fun geminiAuthenticatedPlanPayloadWithoutQuotaMetricIsRejected() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            """
            {
              "account": {"p": "GEMINI_PRO"},
              "usage": {
                "x": [
                  {
                    "l": "Gemini Pro",
                    "t": "Quota is not exposed by the current Gemini web page.",
                    "source": "gemini_web_plan"
                  }
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )

        assertNull(snapshot)
    }

    @Test
    fun geminiAuthenticatedSessionPayloadWithoutQuotaMetricIsRejected() {
        val payload = """
            {
              "account": {"p": "GEMINI_UNKNOWN"},
              "usage": {"x": [
                {
                  "l": "Gemini Web Session",
                  "t": "Quota is not exposed by the current Gemini web page.",
                  "source": "gemini_web_session"
                }
              ]}
            }
        """.trimIndent()

        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            payload,
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )

        assertNull(snapshot)
    }

    @Test
    fun geminiAuthenticatedUltraPayloadWithoutQuotaMetricIsRejected() {
        val payload = """
            {
              "account": {"p": "GEMINI_ULTRA"},
              "usage": {"x": [
                {
                  "l": "Gemini Ultra",
                  "t": "Quota is not exposed by the current Gemini web page.",
                  "source": "gemini_web_session"
                }
              ]}
            }
        """.trimIndent()

        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            payload,
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )

        assertNull(snapshot)
    }

    @Test
    fun copilotEntitlementNormalizesPremiumQuota() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "plan": "Copilot Pro",
              "quotas": {
                "premium_requests": {"remaining": 92, "used_percent": 8, "reset_text": "Resets monthly"}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Copilot Pro", snapshot.plan)
        assertEquals("Premium requests", snapshot.lines.single().label)
        assertEquals(0.92f, snapshot.lines.single().remainingPercent ?: 0f, 0.001f)
        assertEquals("Resets monthly", snapshot.lines.single().resetText)
    }

    @Test
    fun copilotNestedQuotaShapeNormalizesSubscriptionLimits() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "sku": "copilot_pro",
              "quotas": {
                "remaining": {
                  "chat": 80,
                  "completions": 40,
                  "premiumInteractions": 92,
                  "premiumInteractionsPercentage": 92
                },
                "limits": {
                  "chat": 100,
                  "completions": 50,
                  "premiumInteractions": 100
                },
                "resetDate": "2026-06-01T00:00:00Z"
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Copilot Pro", snapshot.plan)
        assertEquals(listOf("Chat", "Inline suggestions", "Premium requests"), snapshot.lines.map { it.label })
        assertEquals(0.92f, snapshot.lines.single { it.label == "Premium requests" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-06-01T00:00:00Z", snapshot.lines.single { it.label == "Premium requests" }.resetsAt)
    }

    @Test
    fun copilotQuotaSnapshotsNormalizePremiumInteractions() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "sku": "copilot_pro",
              "quota_reset_date": "2026-06-01T00:00:00Z",
              "quota_snapshots": {
                "chat": {
                  "entitlement": 100,
                  "remaining": 88,
                  "percent_remaining": 88,
                  "reset_date": "2026-06-15"
                },
                "premium_interactions": {
                  "entitlement": 300,
                  "remaining": 221,
                  "percent_remaining": 73.5,
                  "reset_date": "2026-06-01T00:00:00Z"
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Copilot Pro", snapshot.plan)
        assertEquals(listOf("Chat", "Premium requests"), snapshot.lines.map { it.label })
        assertEquals(0.88f, snapshot.lines.single { it.label == "Chat" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.74f, snapshot.lines.single { it.label == "Premium requests" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-06-01T00:00:00Z", snapshot.lines.single { it.label == "Premium requests" }.resetsAt)
    }

    @Test
    fun copilotQuotaSnapshotsWinOverLegacyZeroPremiumRequests() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "sku": "copilot_pro",
              "quotas": {
                "remaining": {
                  "chat": 88,
                  "premiumInteractions": 0,
                  "premiumInteractionsPercentage": 0
                },
                "limits": {
                  "chat": 100,
                  "premiumInteractions": 100
                },
                "quota_snapshots": {
                  "premium_interactions": {
                    "entitlement": 300,
                    "remaining": 221,
                    "percent_remaining": 73.5,
                    "reset_date": "2026-06-01T00:00:00Z"
                  }
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(listOf("Chat", "Premium requests"), snapshot.lines.map { it.label })
        assertEquals(0.88f, snapshot.lines.single { it.label == "Chat" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.74f, snapshot.lines.single { it.label == "Premium requests" }.remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun copilotBillingPremiumRequestsWinOverLegacyZeroPremiumRequests() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "plan": "copilot_pro",
              "quotas": {
                "remaining": {
                  "chat": 88,
                  "premiumInteractions": 0,
                  "premiumInteractionsPercentage": 0
                },
                "limits": {
                  "chat": 100,
                  "premiumInteractions": 0
                },
                "premium_billing": {
                  "discountQuantity": 27,
                  "userPremiumRequestEntitlement": 300,
                  "filteredUserPremiumRequestEntitlement": 300,
                  "netQuantity": 0,
                  "netBilledAmount": 0
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(listOf("Chat", "Premium requests"), snapshot.lines.map { it.label })
        assertEquals(0.88f, snapshot.lines.single { it.label == "Chat" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.91f, snapshot.lines.single { it.label == "Premium requests" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(27.0, snapshot.lines.single { it.label == "Premium requests" }.usedAmount ?: 0.0, 0.001)
        assertEquals(300.0, snapshot.lines.single { it.label == "Premium requests" }.limitAmount ?: 0.0, 0.001)
    }

    @Test
    fun copilotBillingPremiumRequestsWinOverZeroQuotaSnapshot() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "plan": "copilot_pro",
              "quotas": {
                "quota_snapshots": {
                  "chat": {
                    "percent_remaining": 88,
                    "reset_date": "2026-06-15"
                  },
                  "premium_interactions": {
                    "entitlement": 0,
                    "remaining": 0,
                    "percent_remaining": 0,
                    "reset_date": "2026-06-15"
                  }
                },
                "premium_billing": {
                  "discountQuantity": 27,
                  "userPremiumRequestEntitlement": 300,
                  "filteredUserPremiumRequestEntitlement": 300,
                  "netQuantity": 0,
                  "netBilledAmount": 0
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(listOf("Chat", "Premium requests"), snapshot.lines.map { it.label })
        assertEquals(0.91f, snapshot.lines.single { it.label == "Premium requests" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(27.0, snapshot.lines.single { it.label == "Premium requests" }.usedAmount ?: 0.0, 0.001)
        assertEquals(300.0, snapshot.lines.single { it.label == "Premium requests" }.limitAmount ?: 0.0, 0.001)
    }

    @Test
    fun copilotBillingZeroEntitlementDoesNotCreateFakeZeroPercentGauge() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "plan": "free",
              "quotas": {
                "remaining": {
                  "chat": 88,
                  "premiumInteractions": 0,
                  "premiumInteractionsPercentage": 0
                },
                "limits": {
                  "chat": 100,
                  "premiumInteractions": 0
                },
                "premium_billing": {
                  "discountQuantity": 0,
                  "userPremiumRequestEntitlement": 0,
                  "filteredUserPremiumRequestEntitlement": 0,
                  "netQuantity": 0,
                  "netBilledAmount": 0
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        val premium = snapshot.lines.single { it.label == "Premium requests" }
        assertNull(premium.remainingPercent)
        assertEquals("0 used", premium.remainingText)
        assertEquals(0.0, premium.usedAmount ?: 0.0, 0.001)
        assertEquals(0.0, premium.limitAmount ?: 0.0, 0.001)
    }

    @Test
    fun copilotLimitedUserQuotasNormalizeFreeTierChatAndCompletions() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "copilot_plan": "individual",
              "access_type_sku": "free_limited_copilot",
              "limited_user_quotas": {
                "chat": 410,
                "completions": 3000
              },
              "monthly_quotas": {
                "chat": 500,
                "completions": 4000
              },
              "limited_user_reset_date": "2026-06-15"
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("individual", snapshot.plan)
        assertEquals(listOf("Chat", "Inline suggestions"), snapshot.lines.map { it.label })
        assertEquals(0.82f, snapshot.lines.single { it.label == "Chat" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.75f, snapshot.lines.single { it.label == "Inline suggestions" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-06-15", snapshot.lines.first().resetsAt)
    }

    @Test
    fun copilotMissingCompletionQuotaDoesNotCreateZeroCompletionLine() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "copilot_plan": "individual",
              "limited_user_quotas": {
                "chat": 410
              },
              "monthly_quotas": {
                "chat": 500
              },
              "limited_user_reset_date": "2026-06-15"
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(listOf("Chat"), snapshot.lines.map { it.label })
        assertFalse(snapshot.lines.any { it.label == "Completions" })
    }

    @Test
    fun copilotRemainingCountWithoutPercentOrLimitIsRejected() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "sku": "copilot_pro",
              "quotas": {
                "remaining": {
                  "chat": 80,
                  "premiumInteractions": 12
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )

        assertNull(snapshot)
    }

    @Test
    fun cursorUsageApiNormalizesCurrentPeriodUsage() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "membershipType": "Free",
              "email": "user@example.com",
              "planUsage": {"totalPercentUsed": 4, "resetAt": 1781677951075}
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Free", snapshot.plan)
        assertEquals("user@example.com", snapshot.account)
        assertEquals("Total usage", snapshot.lines.single().label)
        assertEquals(0.96f, snapshot.lines.single().remainingPercent ?: 0f, 0.001f)
        assertEquals("1781677951075", snapshot.lines.single().resetsAt)
    }

    @Test
    fun cursorPercentUsageSurvivesZeroLimitAmounts() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "membershipType": "Pro",
              "planUsage": {
                "totalPercentUsed": 4,
                "used": 0,
                "limit": 0,
                "remaining": 0
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(listOf("Total usage"), snapshot.lines.map { it.label })
        assertEquals(0.96f, snapshot.lines.single().remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun cursorRequestBucketsNormalizeAsRequests() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "membershipType": "Pro",
              "billingCycleEnd": 1781677951075,
              "requestUsage": {
                "gpt-4": {"numRequests": 77, "maxRequestUsage": 500},
                "premium": {"numRequests": 10, "maxRequestUsage": 100}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.NETWORK_RESPONSE
        )!!

        assertEquals("Pro", snapshot.plan)
        assertEquals(listOf("Requests"), snapshot.lines.map { it.label })
        assertEquals(0.855f, snapshot.lines.single().remainingPercent ?: 0f, 0.001f)
        assertEquals(87.0, snapshot.lines.single().usedAmount ?: 0.0, 0.001)
        assertEquals(600.0, snapshot.lines.single().limitAmount ?: 0.0, 0.001)
        assertEquals("requests", snapshot.lines.single().unit)
    }

    @Test
    fun cursorCombinedPlanAndRequestsNormalizeBothRows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "membershipType": "Pro",
              "billingCycleStart": 1772556293029,
              "billingCycleEnd": 1775234693029,
              "planUsage": {"totalPercentUsed": 4},
              "requestUsage": {
                "gpt-4": {"numRequests": 77, "maxRequestUsage": 500}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(listOf("Total usage", "Requests"), snapshot.lines.map { it.label })
        assertEquals(0.96f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.846f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun cursorIndividualUsageZeroSummaryIsRejected() {
        assertNull(
            ProviderUsageNormalizer.normalize(
                ProviderId.CURSOR,
                """
                {
                  "individualUsage": {
                    "plan": {"used": 0, "limit": 0, "remaining": 0}
                  }
                }
                """.trimIndent(),
                ProviderPayloadSource.NETWORK_RESPONSE
            )
        )
    }

    @Test
    fun genericDomTextDoesNotBecomeLiveUsage() {
        assertNull(
            ProviderUsageNormalizer.normalize(
                ProviderId.GEMINI,
                "Usage\nTotal\nSession\nWeekly\nRemaining 100%",
                ProviderPayloadSource.DOM_TEXT
            )
        )
    }

    @Test
    fun snapshotJsonDropsSensitiveFields() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """{"planUsage":{"totalPercentUsed":4},"access_token":"secret","cookie":"raw"}""",
            ProviderPayloadSource.PROVIDER_API
        )!!
        val json = ProviderSnapshotCodec.encode(listOf(snapshot))

        assertFalse(json.contains("access_token"))
        assertFalse(json.contains("cookie"))
        assertFalse(json.contains("secret"))
    }
}
