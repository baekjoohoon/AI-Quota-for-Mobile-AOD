package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
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
    fun claudeUsedPercentOneMeansOnePercentNotOneHundredPercent() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CLAUDE,
            """
            {
              "usage": {
                "weekly": {"used_percent": 1},
                "opus": {"usedPercent": 1},
                "sonnet": {"usedPercentage": 1},
                "cowork": {"used_percentage": 1},
                "design": {"percent_used": 1}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(0.99f, snapshot.lines.first { it.key == "claude:weekly" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.first { it.key == "claude:opus" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.first { it.key == "claude:sonnet" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.first { it.key == "claude:cowork" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.first { it.key == "claude:design" }.remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun claudeStructuredWeeklyUtilizationOneMeansOnePercentNotOneHundredPercent() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CLAUDE,
            """
            {
              "usage": {
                "session": {"utilization": 0.25},
                "weekly": {"utilization": 1}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(0.75f, snapshot.lines.first { it.key == "claude:session" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.first { it.key == "claude:weekly" }.remainingPercent ?: 0f, 0.001f)
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

        assertEquals("Max", snapshot.plan)
        assertEquals(
            listOf("Claude Session", "Claude Weekly", "Claude Opus", "Claude Sonnet", "Claude Cowork", "Claude Design"),
            snapshot.lines.map { it.label }
        )
    }

    @Test
    fun claudeUsageApiNormalizesAdditionalAliasAndContainerBuckets() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CLAUDE,
            """
            {
              "usage": {
                "fiveHour": {"remainingPercent": 80},
                "sevenDay": {"remaining_percent": 70},
                "weekly_opus": {"remainingPercentage": 60},
                "weekly_sonnet": {"percent_remaining": 50},
                "cowork_weekly": {"usedPercent": 40},
                "weekly_design": {"used_percent": 30},
                "models": {
                  "claude-haiku-4-5": {
                    "displayName": "Claude Haiku 4.5",
                    "remaining_percent": 90,
                    "resetAt": "2026-06-19T00:00:00Z"
                  }
                },
                "quotaBuckets": [
                  {
                    "key": "long_context",
                    "label": "Long context",
                    "remainingPercent": 25
                  }
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(
            listOf(
                "Claude Session",
                "Claude Weekly",
                "Claude Opus",
                "Claude Sonnet",
                "Claude Cowork",
                "Claude Design",
                "Claude Haiku 4.5",
                "Long context"
            ),
            snapshot.lines.map { it.label }
        )
        assertEquals(0.80f, snapshot.lines.single { it.key == "claude:session" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.60f, snapshot.lines.single { it.key == "claude:opus" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.60f, snapshot.lines.single { it.key == "claude:cowork" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-06-19T00:00:00Z", snapshot.lines.single { it.label == "Claude Haiku 4.5" }.resetsAt)
        assertEquals(0.25f, snapshot.lines.single { it.label == "Long context" }.remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun claudeUsageApiNormalizesCurrentRawExtraUsageAndIgnoresNullBuckets() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CLAUDE,
            """
            {
              "usage": {
                "five_hour": {"utilization": 0.05, "resets_at": "2026-06-16T13:39:59Z"},
                "seven_day": {"utilization": 0.0, "resets_at": "2026-06-22T19:59:59Z"},
                "seven_day_oauth_apps": null,
                "seven_day_opus": null,
                "seven_day_sonnet": null,
                "seven_day_cowork": null,
                "seven_day_omelette": null,
                "tangelo": null,
                "iguana_necktie": null,
                "omelette_promotional": null,
                "cinder_cove": null,
                "extra_usage": {
                  "is_enabled": true,
                  "monthly_limit": 100,
                  "used_credits": 25,
                  "utilization": null,
                  "currency": "USD",
                  "daily": null,
                  "weekly": null
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(
            listOf("Claude Session", "Claude Weekly", "Extra Usage"),
            snapshot.lines.map { it.label }
        )
        assertEquals(0.95f, snapshot.lines.single { it.key == "claude:session" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(1.0f, snapshot.lines.single { it.key == "claude:weekly" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.75f, snapshot.lines.single { it.key == "claude:extra_usage" }.remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun claudePlanFallsBackToSubscriptionMetadata() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CLAUDE,
            """
            {
              "subscription": {"plan_name": "Claude Max 5x"},
              "usage": {
                "weekly": {"used_percent": 30}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Max 5x", snapshot.plan)
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
    fun glmQuotaLimitResponseNormalizesTokenAndMcpLimits() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GLM,
            """
            {
              "code": 200,
              "msg": "success",
              "success": true,
              "data": {
                "limits": [
                  {
                    "type": "TOKENS_LIMIT",
                    "usage": 10000000,
                    "currentValue": 2500000,
                    "percentage": 25,
                    "nextResetTime": 1792537200000
                  },
                  {
                    "type": "TIME_LIMIT",
                    "usage": 500,
                    "currentValue": 125,
                    "percentage": 25
                  }
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(ProviderId.GLM, snapshot.providerId)
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("GLM Coding Plan", snapshot.plan)
        assertEquals(listOf("5-Hour Token Limit", "MCP Monthly Quota"), snapshot.lines.map { it.label })
        assertEquals(0.75f, snapshot.lines.single { it.key == "glm:tokens" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.75f, snapshot.lines.single { it.key == "glm:mcp" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(2_500_000.0, snapshot.lines.single { it.key == "glm:tokens" }.usedAmount ?: 0.0, 0.001)
        assertEquals(10_000_000.0, snapshot.lines.single { it.key == "glm:tokens" }.limitAmount ?: 0.0, 0.001)
        assertEquals("2026-10-20T23:00:00Z", snapshot.lines.single { it.key == "glm:tokens" }.resetsAt)
    }

    @Test
    fun glmQuotaLimitResponseKeepsPercentageOnlyTokenLimits() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GLM,
            """
            {
              "code": 200,
              "msg": "success",
              "success": true,
              "data": {
                "limits": [
                  {
                    "type": "TOKENS_LIMIT",
                    "unit": 3,
                    "number": 5,
                    "percentage": 25,
                    "nextResetTime": 1792537200000
                  },
                  {
                    "type": "TOKENS_LIMIT",
                    "unit": 6,
                    "number": 7,
                    "percentage": 40
                  },
                  {
                    "type": "TIME_LIMIT",
                    "usage": 500,
                    "currentValue": 125,
                    "percentage": 25
                  }
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(
            listOf("5-Hour Token Limit", "Weekly Token Limit", "MCP Monthly Quota"),
            snapshot.lines.map { it.label }
        )
        assertEquals(0.75f, snapshot.lines.single { it.key == "glm:tokens" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.60f, snapshot.lines.single { it.key == "glm:weekly_tokens" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-10-20T23:00:00Z", snapshot.lines.single { it.key == "glm:tokens" }.resetsAt)
        assertNull(snapshot.lines.single { it.key == "glm:tokens" }.usedAmount)
        assertNull(snapshot.lines.single { it.key == "glm:tokens" }.limitAmount)
    }

    @Test
    fun glmWebPayloadSeparatesFiveHourAndWeeklyTokenLimits() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GLM,
            """
            {
              "provider": "glm",
              "source": "visible-dom",
              "plan": "GLM Coding Pro",
              "data": {
                "limits": [
                  {
                    "type": "TOKENS_LIMIT",
                    "unit": 3,
                    "number": 5,
                    "usage": 1000,
                    "currentValue": 250,
                    "percentage": 25
                  },
                  {
                    "type": "TOKENS_LIMIT",
                    "unit": 6,
                    "number": 7,
                    "usage": 1000,
                    "currentValue": 400,
                    "percentage": 40
                  }
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals("GLM Coding Pro", snapshot.plan)
        assertEquals(listOf("5-Hour Token Limit", "Weekly Token Limit"), snapshot.lines.map { it.label })
        assertEquals(0.75f, snapshot.lines.single { it.key == "glm:tokens" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.60f, snapshot.lines.single { it.key == "glm:weekly_tokens" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("visible-dom", snapshot.lines.first().sourceLabel)
    }

    @Test
    fun opencodeVisibleDomPayloadNormalizesGoLimitsAndZenCredits() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.OPENCODE,
            """
            {
              "provider": "opencode",
              "source": "visible-dom",
              "data": {
                "plan": "OpenCode Go",
                "account": "user@example.com",
                "limits": [
                  {
                    "label": "Go 5 hour limit",
                    "remaining_percent": 75,
                    "used": 3,
                    "limit": 12,
                    "unit": "usd",
                    "reset_text": "Resets in 2h"
                  },
                  {
                    "label": "Go weekly limit",
                    "used": 12,
                    "limit": 30,
                    "unit": "usd"
                  },
                  {
                    "label": "Go monthly limit",
                    "remaining": 54,
                    "limit": 60,
                    "unit": "usd"
                  }
                ],
                "credits": {
                  "balance": 4.5
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(ProviderId.OPENCODE, snapshot.providerId)
        assertEquals("OpenCode Go", snapshot.plan)
        assertEquals("user@example.com", snapshot.account)
        assertEquals(
            listOf("Go 5-Hour Limit", "Go Weekly Limit", "Go Monthly Limit", "Zen Credits"),
            snapshot.lines.map { it.label }
        )
        assertEquals(0.75f, snapshot.lines.single { it.key == "opencode:go_5_hour_limit" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.60f, snapshot.lines.single { it.key == "opencode:go_weekly_limit" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.90f, snapshot.lines.single { it.key == "opencode:go_monthly_limit" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("Resets in 2h", snapshot.lines.single { it.key == "opencode:go_5_hour_limit" }.resetText)
        assertEquals("4.5 credits left", snapshot.lines.single { it.key == "opencode:zen_credits" }.remainingText)
        assertNull(snapshot.lines.single { it.key == "opencode:zen_credits" }.remainingPercent)
    }

    @Test
    fun opencodePayloadWithoutTrustedUsageMetricIsRejected() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.OPENCODE,
            """
            {
              "provider": "opencode",
              "source": "visible-dom",
              "data": {
                "plan": "OpenCode Go",
                "limits": [
                  {"label": "Go weekly limit"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
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
    fun codexPlanFallsBackToSubscriptionApiMetadata() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "subscription": {
                "plan_type": "prolite"
              },
              "usage": {
                "rate_limits": {
                  "primary_window": {"remaining_percent": 80}
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Pro 5x", snapshot.plan)
    }

    @Test
    fun codexPlanFallsBackToSubscriptionListMetadata() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "data": [
                {
                  "active_subscription": {
                    "plan": {
                      "id": "prolite",
                      "display_name": "ChatGPT Pro Lite"
                    }
                  }
                }
              ],
              "usage": {
                "rate_limits": {
                  "primary_window": {"remaining_percent": 80}
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals("Pro 5x", snapshot.plan)
    }

    @Test
    fun codexVisibleDomPayloadNormalizesSeparateSparkWindowsAndCredits() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "source": "visible-dom",
              "usage": {
                "rate_limits": {
                  "primary_window": {
                    "label": "Codex Session",
                    "remaining_percent": 99,
                    "used_percent": 1,
                    "reset_text": "오전 5:24 초기화"
                  },
                  "secondary_window": {
                    "label": "Codex Weekly",
                    "remaining_percent": 100,
                    "used_percent": 0
                  },
                  "spark_primary_window": {
                    "label": "GPT-5.3-Codex-Spark 5h",
                    "remaining_percent": 100,
                    "used_percent": 0
                  },
                  "spark_secondary_window": {
                    "label": "GPT-5.3-Codex-Spark Weekly",
                    "remaining_percent": 100,
                    "used_percent": 0
                  }
                },
                "credits": {
                  "remaining": 0
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(
            listOf("Codex Session", "Codex Weekly", "GPT-5.3-Codex-Spark 5h", "GPT-5.3-Codex-Spark Weekly", "Codex Credits"),
            snapshot.lines.map { it.label }
        )
        assertEquals(0.99f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(0, snapshot.lines[1].usedPercent)
        assertEquals("visible-dom", snapshot.lines[0].sourceLabel)
        assertEquals("0 credits left", snapshot.lines[4].remainingText)
        assertEquals(0.0, snapshot.lines[4].remainingAmount ?: -1.0, 0.001)
        assertNull(snapshot.lines[4].remainingPercent)
    }

    @Test
    fun codexVisibleDomPayloadKeepsOnePercentRemainingAsOnePercent() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "source": "visible-dom",
              "usage": {
                "rate_limits": {
                  "secondary_window": {
                    "label": "Codex Weekly",
                    "remaining_percent": 1,
                    "used_percent": 99,
                    "reset_text": "2026. 6. 11. 오전 9:59 초기화"
                  },
                  "spark_secondary_window": {
                    "label": "GPT-5.3-Codex-Spark Weekly",
                    "remaining_percent": 100,
                    "used_percent": 0
                  }
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        val weekly = snapshot.lines.single { it.key == "codex:secondary_window" }
        val sparkWeekly = snapshot.lines.single { it.key == "codex:spark_secondary_window" }
        assertEquals(0.01f, weekly.remainingPercent ?: 0f, 0.001f)
        assertEquals(99, weekly.usedPercent)
        assertEquals(1.0f, sparkWeekly.remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun codexVisibleDomSparkWindowsInheritMatchingResetTextWhenMissing() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "source": "visible-dom",
              "usage": {
                "rate_limits": {
                  "primary_window": {
                    "label": "Codex Session",
                    "remaining_percent": 91,
                    "reset_text": "오전 1:24 초기화"
                  },
                  "secondary_window": {
                    "label": "Codex Weekly",
                    "remaining_percent": 97,
                    "reset_text": "오후 3:23 초기화"
                  },
                  "spark_primary_window": {
                    "label": "GPT-5.3-Codex-Spark 5h",
                    "remaining_percent": 100
                  },
                  "spark_secondary_window": {
                    "label": "GPT-5.3-Codex-Spark Weekly",
                    "remaining_percent": 100
                  }
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals("오전 1:24 초기화", snapshot.lines.single { it.key == "codex:spark_primary_window" }.resetText)
        assertEquals("오후 3:23 초기화", snapshot.lines.single { it.key == "codex:spark_secondary_window" }.resetText)
    }

    @Test
    fun codexVisibleDomPayloadDoesNotBorrowWeeklyResetForIdleSessionWindows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "source": "visible-dom",
              "usage": {
                "rate_limits": {
                  "primary_window": {
                    "label": "Codex Session",
                    "remaining_percent": 100
                  },
                  "secondary_window": {
                    "label": "Codex Weekly",
                    "remaining_percent": 90,
                    "reset_text": "Resets in 6d 2h"
                  },
                  "spark_primary_window": {
                    "label": "GPT-5.3-Codex-Spark 5h",
                    "remaining_percent": 100
                  },
                  "spark_secondary_window": {
                    "label": "GPT-5.3-Codex-Spark Weekly",
                    "remaining_percent": 100,
                    "reset_text": "Resets in 6d 2h"
                  }
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertNull(snapshot.lines.single { it.key == "codex:primary_window" }.resetText)
        assertEquals("Resets in 6d 2h", snapshot.lines.single { it.key == "codex:secondary_window" }.resetText)
        assertNull(snapshot.lines.single { it.key == "codex:spark_primary_window" }.resetText)
        assertEquals("Resets in 6d 2h", snapshot.lines.single { it.key == "codex:spark_secondary_window" }.resetText)
    }

    @Test
    fun codexWhamPrefersRemainingPercentWhenUsedPercentConflictsAfterReset() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "plan_type": "prolite",
              "rate_limits": {
                "secondary_window": {
                  "used_percent": 100,
                  "remaining_percent": 99,
                  "resets_at": "2026-06-07T00:00:00Z"
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        val weekly = snapshot.lines.single { it.key == "codex:secondary_window" }
        assertEquals(0.99f, weekly.remainingPercent ?: 0f, 0.001f)
        assertEquals(1, weekly.usedPercent)
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
    fun codexCompactScriptPayloadNormalizesStoreApkRows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "account": "<email>",
              "plan": "prolite",
              "usage": {
                "x": [
                  {"l": "Codex Session", "u": 0.12, "r": 1779160000000, "t": "Resets in 4h"},
                  {"l": "Codex Weekly", "u": 0.51, "t": "Resets in 5d"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals("Pro 5x", snapshot.plan)
        assertEquals(listOf("Codex Session", "Codex Weekly"), snapshot.lines.map { it.label })
        assertEquals(0.88f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals("1779160000000", snapshot.lines[0].resetsAt)
    }

    @Test
    fun codexCompactRowsWithoutProviderPrefixStillNormalizeSessionAndWeekly() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "plan": "prolite",
              "usage": {
                "x": [
                  {"l": "5시간 세션", "u": 0.08, "t": "4시간 12분 후 초기화"},
                  {"l": "주간 세션", "u": 0.72, "t": "3일 1시간 후 초기화"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(listOf("Codex Session", "Codex Weekly"), snapshot.lines.map { it.label })
        assertEquals(0.92f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.28f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun codexCompactRowsPreferRemainingPercentOverAmbiguousUsedField() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "plan": "prolite",
              "usage": {
                "x": [
                  {"l": "Codex Session", "u": 1, "remaining_percent": 100, "t": "4시간 후 초기화"},
                  {"l": "Codex Weekly", "u": 1, "remainingFraction": 1.0, "t": "3일 후 초기화"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(listOf("Codex Session", "Codex Weekly"), snapshot.lines.map { it.label })
        assertEquals(1.0f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(1.0f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun codexCompactRowsCanReportZeroRemaining() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "plan": "prolite",
              "usage": {
                "x": [
                  {"l": "Codex Session", "u": 1, "remaining_percent": 0, "t": "Resets in 5h"},
                  {"l": "Codex Weekly", "u": 1, "remainingFraction": 0, "t": "Resets in 6d"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(listOf("Codex Session", "Codex Weekly"), snapshot.lines.map { it.label })
        assertEquals(0.0f, snapshot.lines[0].remainingPercent ?: -1f, 0.001f)
        assertEquals(0.0f, snapshot.lines[1].remainingPercent ?: -1f, 0.001f)
        assertEquals("Resets in 5h", snapshot.lines[0].resetText)
    }

    @Test
    fun codexAuthoritativeRateLimitCanReportZeroRemaining() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CODEX,
            """
            {
              "plan_type": "prolite",
              "rate_limits": {
                "primary_window": {"used_percent": 100, "reset_after_seconds": 18000}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(listOf("Codex Session"), snapshot.lines.map { it.label })
        assertEquals(0.0f, snapshot.lines.single().remainingPercent ?: -1f, 0.001f)
    }

    @Test
    fun geminiCompactPayloadWithoutModelIdsIsRejected() {
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
        )

        assertNull(snapshot)
    }

    @Test
    fun geminiCompactPayloadDoesNotCreateCollapsedLimitRows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            """
            {
              "account": {"p": "GEMINI_PRO", "e": "user@example.com"},
              "usage": {
                "x": [
                  {"l": "Gemini Pro", "u": 0.02, "t": "오후 4:58에 초기화"},
                  {"l": "Gemini Weekly", "u": 0.12, "t": "6월 2일 오전 3:26에 초기화"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )

        assertNull(snapshot)
    }

    @Test
    fun geminiUsagePageLimitPayloadNormalizesDomRows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            """
            {
              "account": {"p": "GEMINI_PRO", "e": "user@example.com"},
              "usage": {
                "x": [
                  {"l": "5-hour limit", "u": 0.04, "t": "오후 5:56에 초기화"},
                  {"l": "Weekly limit", "u": 0, "t": "6월 4일 오후 12:56에 초기화"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )

        assertNotNull(snapshot)
        snapshot!!
        assertEquals("Pro", snapshot.plan)
        assertEquals("user@example.com", snapshot.account)
        assertEquals(listOf("5-hour limit", "Weekly limit"), snapshot.lines.map { it.label })
        assertEquals(listOf("gemini:5_hour_limit", "gemini:weekly_limit"), snapshot.lines.map { it.key })
        assertEquals(0.96f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(1.0f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
        assertEquals("오후 5:56에 초기화", snapshot.lines[0].resetText)
        assertEquals("6월 4일 오후 12:56에 초기화", snapshot.lines[1].resetText)
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
    fun geminiCodeAssistQuotaBucketsNormalizeByModelId() {
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
        assertEquals(listOf("2.5 pro", "2.5 pro-pre", "2.5 flash"), snapshot.lines.map { it.label })
        assertEquals(0.98f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-05-19T12:00:00Z", snapshot.lines[0].resetsAt)
        assertEquals(0.75f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines[2].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun geminiCodeAssistRemainingPercentOneMeansOnePercentNotOneHundredPercent() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            """
            {
              "plan": "standard-tier",
              "limits": [
                {"modelId": "gemini-2.5-pro", "remainingPercent": 1},
                {"modelId": "gemini-2.5-flash", "remainingPercentage": 1}
              ]
            }
            """.trimIndent(),
            ProviderPayloadSource.NETWORK_RESPONSE
        )!!

        assertEquals(0.01f, snapshot.lines.single { it.label == "2.5 pro" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.01f, snapshot.lines.single { it.label == "2.5 flash" }.remainingPercent ?: 0f, 0.001f)
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

        assertEquals("Pro", snapshot.plan)
        assertEquals("user@example.com", snapshot.account)
        assertEquals(listOf("2.5 pro", "2.5 pro-pre", "2.5 flash"), snapshot.lines.map { it.label })
        assertEquals(0.98f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(2, snapshot.lines[0].usedPercent)
        assertEquals(0.75f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
        assertEquals(25, snapshot.lines[1].usedPercent)
        assertEquals(0.99f, snapshot.lines[2].remainingPercent ?: 0f, 0.001f)
        assertEquals(1, snapshot.lines[2].usedPercent)
    }

    @Test
    fun geminiCliOAuthQuotaPayloadHidesUnavailableFreePlanProBuckets() {
        val payload = GeminiCliOAuthRepository.normalizeQuotaPayload(
            quotaJson = """
            {
              "buckets": [
                {"modelId": "gemini-2.5-flash", "remainingFraction": 1, "resetTime": "2026-06-04T09:22:06Z"},
                {"modelId": "gemini-2.5-flash-lite", "remainingFraction": 1, "resetTime": "2026-06-04T09:22:06Z"},
                {"modelId": "gemini-2.5-pro", "remainingFraction": 0, "resetTime": "1970-01-01T00:00:00Z"},
                {"modelId": "gemini-3-flash-preview", "remainingFraction": 1, "resetTime": "2026-06-04T09:22:06Z"},
                {"modelId": "gemini-3-pro-preview", "remainingFraction": 0, "resetTime": "1970-01-01T00:00:00Z"},
                {"modelId": "gemini-3.1-flash-lite", "remainingFraction": 1, "resetTime": "2026-06-04T09:22:06Z"},
                {"modelId": "gemini-3.1-flash-lite-preview", "remainingFraction": 1, "resetTime": "2026-06-04T09:22:06Z"},
                {"modelId": "gemini-3.1-pro-preview", "remainingFraction": 0, "resetTime": "1970-01-01T00:00:00Z"}
              ]
            }
            """.trimIndent(),
            account = "user@example.com",
            plan = "GEMINI_FREE"
        )!!
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.GEMINI,
            payload,
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(
            listOf(
                "3.1 flash-lite-pre",
                "3.1 flash-lite",
                "3 flash-pre",
                "2.5 flash-lite",
                "2.5 flash"
            ),
            snapshot.lines.map { it.label }
        )
        assertEquals(listOf(0, 0, 0, 0, 0), snapshot.lines.map { it.usedPercent })
    }

    @Test
    fun geminiCliOAuthQuotaPayloadKeepsExhaustedPaidPlanProBuckets() {
        val payload = GeminiCliOAuthRepository.normalizeQuotaPayload(
            quotaJson = """
            {
              "buckets": [
                {"modelId": "gemini-2.5-pro", "remainingFraction": 0, "resetTime": "2026-06-04T09:22:06Z"},
                {"modelId": "gemini-2.5-flash", "remainingFraction": 1, "resetTime": "2026-06-04T09:22:06Z"}
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

        assertEquals("Pro", snapshot.plan)
        assertEquals(listOf("2.5 pro", "2.5 flash"), snapshot.lines.map { it.label })
        assertEquals(listOf(100, 0), snapshot.lines.map { it.usedPercent })
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

        assertEquals("Pro", snapshot.plan)
        assertEquals("user@example.com", snapshot.account)
        assertEquals(listOf("2.5 pro", "2.5 flash"), snapshot.lines.map { it.label })
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
        assertEquals(listOf("2.5 pro"), snapshot.lines.map { it.label })
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
    fun copilotFeaturesPageUsageUsesGlobalResetForChatAndCompletions() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "plan": "free",
              "collectorMode": "webview-features-page",
              "quotas": {
                "quota_reset_date": "2026-06-15",
                "chat": {"used_percent": 12},
                "completions": {"used_percent": 0}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals("free", snapshot.plan)
        assertEquals(listOf("Chat", "Inline suggestions"), snapshot.lines.map { it.label })
        assertEquals(0.88f, snapshot.lines.single { it.label == "Chat" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(1.0f, snapshot.lines.single { it.label == "Inline suggestions" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-06-15", snapshot.lines.single { it.label == "Chat" }.resetsAt)
        assertEquals("2026-06-15", snapshot.lines.single { it.label == "Inline suggestions" }.resetsAt)
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
    fun cursorPercentFieldsTreatOneAsOnePercentNotOneHundredPercent() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "membershipType": "Pro",
              "planUsage": {
                "totalPercentUsed": 1,
                "autoPercentUsed": 1,
                "apiPercentUsed": 1,
                "breakdown": {
                  "onDemand": 1
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(0.99f, snapshot.lines.single { it.label == "Total usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.single { it.label == "Auto usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.single { it.label == "API usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.single { it.label == "On-demand usage" }.remainingPercent ?: 0f, 0.001f)
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
    fun cursorPlanUsagePercentBreakdownNormalizesAutoAndApiRows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "membershipType": "Free",
              "billingCycleEnd": 1781677951075,
              "planUsage": {
                "totalSpend": 8,
                "bonusSpend": 8,
                "totalPercentUsed": 4,
                "autoPercentUsed": 8,
                "apiPercentUsed": 0
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(listOf("Total usage", "Auto usage", "API usage"), snapshot.lines.map { it.label })
        assertEquals(0.96f, snapshot.lines.single { it.label == "Total usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.92f, snapshot.lines.single { it.label == "Auto usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(1.0f, snapshot.lines.single { it.label == "API usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals("1781677951075", snapshot.lines.single { it.label == "Auto usage" }.resetsAt)
    }

    @Test
    fun cursorPlanUsageStructuredBreakdownNormalizesAutoApiAndOnDemandRows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "membershipType": "Pro",
              "billingCycleEnd": 1781677951075,
              "planUsage": {
                "totalPercentUsed": 20,
                "breakdown": {
                  "auto": {"used": 12, "limit": 100},
                  "api": {"remaining": 90, "limit": 100},
                  "onDemand": {"remaining": 15, "limit": 20}
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(listOf("Total usage", "Auto usage", "API usage", "On-demand usage"), snapshot.lines.map { it.label })
        assertEquals(0.88f, snapshot.lines.single { it.label == "Auto usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.90f, snapshot.lines.single { it.label == "API usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.75f, snapshot.lines.single { it.label == "On-demand usage" }.remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun cursorIndividualUsageBreakdownNormalizesPlanBreakdownAndOnDemandRows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "membershipType": "Pro",
              "billingCycleEnd": 1781677951075,
              "individualUsage": {
                "plan": {
                  "limit": 100,
                  "remaining": 80,
                  "breakdown": [
                    {"label": "Auto", "used": 9, "limit": 100},
                    {"label": "API", "remaining": 95, "limit": 100}
                  ]
                },
                "onDemand": {"limit": 20, "remaining": 19}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.NETWORK_RESPONSE
        )!!

        assertEquals(listOf("Total usage", "Auto usage", "API usage", "On-demand usage"), snapshot.lines.map { it.label })
        assertEquals(0.80f, snapshot.lines.single { it.label == "Total usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.91f, snapshot.lines.single { it.label == "Auto usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.95f, snapshot.lines.single { it.label == "API usage" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.95f, snapshot.lines.single { it.label == "On-demand usage" }.remainingPercent ?: 0f, 0.001f)
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
    fun antigravityCompactPayloadNormalizesStoreApkRows() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.ANTIGRAVITY,
            """
            {
              "account": "<email>",
              "plan": "ANTIGRAVITY_PLUS",
              "usage": {
                "x": [
                  {"l": "Antigravity Session", "u": 0.20, "t": "Resets in 5h"},
                  {"l": "Antigravity Weekly", "remaining_percent": 88, "resetAt": "2026-06-01T00:00:00Z"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals("Plus", snapshot.plan)
        assertEquals("<email>", snapshot.account)
        assertEquals(listOf("Antigravity Session", "Antigravity Weekly"), snapshot.lines.map { it.label })
        assertEquals(0.80f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.88f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-06-01T00:00:00Z", snapshot.lines[1].resetsAt)
    }

    @Test
    fun antigravityPercentFieldsTreatOneAsOnePercentNotOneHundredPercent() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.ANTIGRAVITY,
            """
            {
              "usage": {
                "x": [
                  {"l": "Antigravity Session", "usedPercent": 1},
                  {"l": "Antigravity Weekly", "used_percent": 1},
                  {"l": "Antigravity Remaining", "remainingPercentage": 1},
                  {"l": "Antigravity Percent Used", "percentUsed": 1}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(0.99f, snapshot.lines.single { it.key == "antigravity:session" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.single { it.key == "antigravity:weekly" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.01f, snapshot.lines.single { it.key == "antigravity:remaining" }.remainingPercent ?: 0f, 0.001f)
        assertEquals(0.99f, snapshot.lines.single { it.key == "antigravity:percent_used" }.remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun antigravityCollectedPayloadDerivesPlanAndUsageLabels() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.ANTIGRAVITY,
            """
            {
              "subscription": {
                "displayName": "Google AI Pro"
              },
              "quota": {
                "windows": [
                  {
                    "displayName": "Agent mode",
                    "used": 12,
                    "limit": 50,
                    "resetTime": "2026-06-01T00:00:00Z",
                    "unit": "requests"
                  },
                  {
                    "metric": {
                      "displayName": "Autocomplete"
                    },
                    "remaining": 81,
                    "limit": 100,
                    "resetText": "Resets in 5h",
                    "unit": "requests"
                  }
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals("AI Pro", snapshot.plan)
        assertEquals(listOf("Agent mode", "Autocomplete"), snapshot.lines.map { it.label })
        assertEquals(0.76f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.81f, snapshot.lines[1].remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-06-01T00:00:00Z", snapshot.lines[0].resetsAt)
        assertEquals("requests", snapshot.lines[0].unit)
    }

    @Test
    fun antigravityAvailableModelsAreSortedByProductPriority() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.ANTIGRAVITY,
            """
            {
              "models": {
                "claude-opus-4-6-thinking": {
                  "displayName": "Claude Opus 4.6 (Thinking)",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                },
                "gemini-3-flash": {
                  "displayName": "Gemini 3 Flash",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                },
                "gemini-3-5-flash-high": {
                  "displayName": "Gemini 3.5 Flash (High)",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                },
                "gemini-3-1-flash-lite": {
                  "displayName": "Gemini 3.1 Flash Lite",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                },
                "gemini-3-1-pro-low": {
                  "displayName": "Gemini 3.1 Pro (Low)",
                  "quotaInfo": {"remainingFraction": 0.8, "resetTime": "2026-06-03T12:00:00Z"}
                },
                "gpt-oss-120b-medium": {
                  "displayName": "GPT-OSS 120B (Medium)",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                },
                "gemini-3-1-flash-image": {
                  "displayName": "Gemini 3.1 Flash Image",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                },
                "gemini-3-1-pro-high": {
                  "displayName": "Gemini 3.1 Pro (High)",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                },
                "claude-sonnet-4-6-thinking": {
                  "displayName": "Claude Sonnet 4.6 (Thinking)",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                },
                "gemini-3-5-flash-medium": {
                  "displayName": "Gemini 3.5 Flash (Medium)",
                  "quotaInfo": {"remainingFraction": 1.0, "resetTime": "2026-06-01T05:00:00Z"}
                }
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.PROVIDER_API
        )!!

        assertEquals(
            listOf(
                "Gemini 3.5 Flash (High)",
                "Gemini 3.5 Flash (Medium)",
                "Gemini 3.1 Pro (High)",
                "Gemini 3.1 Pro (Low)",
                "Gemini 3 Flash",
                "Gemini 3.1 Flash Image",
                "Gemini 3.1 Flash Lite",
                "Claude Sonnet 4.6 (Thinking)",
                "Claude Opus 4.6 (Thinking)",
                "GPT-OSS 120B (Medium)"
            ),
            snapshot.lines.map { it.label }
        )
    }

    @Test
    fun antigravityPayloadWithoutUsageMetricIsRejected() {
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.ANTIGRAVITY,
            """
            {
              "plan": "ANTIGRAVITY_PRO",
              "usage": {
                "x": [
                  {"l": "Antigravity Session", "t": "Usage unavailable"}
                ]
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )

        assertNull(snapshot)
    }

    @Test
    fun remainingAliasesAcceptZeroAndFullBoundariesAcrossProviders() {
        val cursor = ProviderUsageNormalizer.normalize(
            ProviderId.CURSOR,
            """
            {
              "provider": "cursor",
              "planUsage": {"remaining_fraction": 1},
              "limits": [
                {"label": "On-demand", "remainingFraction": 0}
              ]
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!
        val copilot = ProviderUsageNormalizer.normalize(
            ProviderId.COPILOT,
            """
            {
              "provider": "copilot",
              "quota_snapshots": {
                "chat": {"remainingFraction": 1},
                "completions": {"remaining_fraction": 0}
              }
            }
            """.trimIndent(),
            ProviderPayloadSource.STRUCTURED_SCRIPT
        )!!

        assertEquals(1.0f, cursor.lines.single { it.label == "Total usage" }.remainingPercent ?: -1f, 0.001f)
        assertEquals(0.0f, cursor.lines.single { it.label == "On-demand" }.remainingPercent ?: -1f, 0.001f)
        assertEquals(1.0f, copilot.lines.single { it.label == "Chat" }.remainingPercent ?: -1f, 0.001f)
        assertEquals(0.0f, copilot.lines.single { it.label == "Inline suggestions" }.remainingPercent ?: -1f, 0.001f)
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
