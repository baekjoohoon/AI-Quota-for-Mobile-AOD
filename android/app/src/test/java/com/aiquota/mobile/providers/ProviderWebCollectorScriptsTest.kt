package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ProviderWebCollectorScriptsTest {
    @Test
    fun collectorRunsOnlyAfterProviderShellsAreReached() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CLAUDE, "https://claude.ai/new", mapOf("lastActiveOrg" to "org_123"), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CODEX, "https://chatgpt.com/", emptyMap(), "ChatGPT"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://gemini.google.com/app", emptyMap(), "Gemini"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://gemini.google.com/usage", emptyMap(), "Gemini usage"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "about:blank", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "about:blank", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "https://github.com/settings/copilot", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "https://github.com/settings/billing/premium_requests_usage", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.ANTIGRAVITY, "about:blank", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CURSOR, "https://cursor.com/dashboard", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GLM, "https://z.ai/manage-apikey/coding-plan/personal/my-plan", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GLM, "https://chat.z.ai/", emptyMap(), "Coding Plan Usage"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.OPENCODE, "https://opencode.ai/auth", emptyMap(), "OpenCode Go usage limits"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.OPENCODE, "https://opencode.ai/zen/go/usage", emptyMap(), "Weekly limit"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.OPENCODE, "https://opencode.ai/workspace/wrk_123/go", emptyMap(), "롤링 사용량"))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CLAUDE, "https://claude.ai/login", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CLAUDE, "https://claude.ai/login", mapOf("lastActiveOrg" to "org_123"), "Claude"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CODEX, "https://chatgpt.com/auth/login", emptyMap(), "ChatGPT"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CODEX, "https://chatgpt.com/", emptyMap(), "로그인 또는 회원가입\nGoogle 계정으로 계속하기"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CODEX, "https://admin.openai.com/analytics/codex", emptyMap(), "Codex token usage"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://accounts.google.com/signin", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "https://github.com/login", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "https://github.com/", mapOf("logged_in" to "yes"), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.ANTIGRAVITY, "https://accounts.google.com/signin", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.ANTIGRAVITY, "https://antigravity.google/docs/plans", emptyMap(), "Antigravity"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CURSOR, "https://api.workos.com/sso/authorize", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GLM, "https://z.ai/login", emptyMap(), "Login"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.OPENCODE, "https://opencode.ai/docs/go/", emptyMap(), "OpenCode Go"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.OPENCODE, "https://opencode.ai/auth", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.OPENCODE, "https://opencode.ai/auth", emptyMap(), "Sign in to OpenCode"))
    }

    @Test
    fun hiddenRefreshDetectsProviderLoginPages() {
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CODEX, "https://chatgpt.com/auth/login"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CODEX, "https://chatgpt.com/", "로그인 또는 회원가입"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CODEX, "https://auth.openai.com/authorize"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.COPILOT, "https://github.com/login"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.COPILOT, "https://github.com/sessions/two-factor"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.ANTIGRAVITY, "https://accounts.google.com/signin"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CURSOR, "https://api.workos.com/sso/authorize"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CURSOR, "https://api.workos.com/sso/authorize", "Sign in to Cursor"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CURSOR, "https://github.com/login"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GLM, "https://z.ai/login"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.OPENCODE, "https://opencode.ai/auth", "Sign in to OpenCode"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.OPENCODE, "https://auth.opencode.ai/authorize"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.OPENCODE, "https://auth.opencode.ai/authorize", "Sign in to OpenCode"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GEMINI, "https://gemini.google.com/app", "Gemini\n3.5 Flash\n로그인\nGemini와의 대화\n개인 AI 어시스턴트인 Gemini를 만나 보세요"))
        assertTrue(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GEMINI, "https://gemini.google.com/usage", "Gemini\n로그인\nGemini와의 대화\n개인 AI 어시스턴트인 Gemini를 만나 보세요"))

        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CLAUDE, "https://claude.ai/new"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CODEX, "https://chatgpt.com/"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.COPILOT, "https://github.com/settings/copilot"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.ANTIGRAVITY, "https://antigravity.google/docs/plans"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.CURSOR, "https://cursor.com/dashboard"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GLM, "https://z.ai/manage-apikey/coding-plan/personal/my-plan"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.OPENCODE, "https://opencode.ai/auth", "OpenCode Go usage limits"))
        assertFalse(ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GEMINI, "https://gemini.google.com/usage", "Current usage\nUsage limit\n5-hour limit"))
    }

    @Test
    fun serviceCollectorCanReinjectWebDomProvidersAfterPageSettles() {
        assertTrue(ProviderWebCollectorScripts.shouldAllowCollectorReinjection(ProviderId.CLAUDE))
        assertTrue(ProviderWebCollectorScripts.shouldAllowCollectorReinjection(ProviderId.CODEX))
        assertTrue(ProviderWebCollectorScripts.shouldAllowCollectorReinjection(ProviderId.OPENCODE))
        assertTrue(ProviderWebCollectorScripts.shouldAllowCollectorReinjection(ProviderId.COPILOT))
        assertTrue(ProviderWebCollectorScripts.shouldAllowCollectorReinjection(ProviderId.CURSOR))
        assertTrue(ProviderWebCollectorScripts.shouldAllowCollectorReinjection(ProviderId.GLM))
        assertTrue(ProviderWebCollectorScripts.shouldAllowCollectorReinjection(ProviderId.GEMINI))
        assertFalse(ProviderWebCollectorScripts.shouldAllowCollectorReinjection(ProviderId.ANTIGRAVITY))
    }

    @Test
    fun collectorScriptsExposeOnlyProviderSafeCookiesToPageJavascript() {
        val cookies = mapOf(
            "lastActiveOrg" to "org_123",
            "aiquota_sensitive_cookie_name" to "cookie_secret_xyz"
        )

        val glm = ProviderWebCollectorScripts.build(ProviderId.GLM, cookies, "")
        assertFalse(glm.contains("cookie_secret_xyz"))
        assertFalse(glm.contains("aiquota_sensitive_cookie_name"))
        assertFalse(glm.contains("\"lastActiveOrg\":\"org_123\""))

        val claude = ProviderWebCollectorScripts.build(ProviderId.CLAUDE, cookies, "")
        assertTrue(claude.contains("\"lastActiveOrg\":\"org_123\""))
        assertFalse(claude.contains("cookie_secret_xyz"))
        assertFalse(claude.contains("aiquota_sensitive_cookie_name"))
    }

    @Test
    fun glmCollectorConvertsPageStateLimitsToTrustedPayload() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected GLM runtime checks", node != null)

        val glm = ProviderWebCollectorScripts.build(ProviderId.GLM, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-glm-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = {
              hostname: "z.ai",
              pathname: "/manage-apikey/coding-plan/personal/my-plan",
              href: "https://z.ai/manage-apikey/coding-plan/personal/my-plan"
            };
            const quota = {
              productName: "GLM Coding Pro",
              data: {
                limits: [
                  { type: "TOKENS_LIMIT", unit: 3, number: 5, usage: 1000, currentValue: 250, percentage: 25, nextResetTime: 1792537200000 },
                  { type: "TOKENS_LIMIT", unit: 6, number: 7, usage: 1000, currentValue: 400, percentage: 40 },
                  { type: "TIME_LIMIT", unit: 5, number: 1, usage: 500, currentValue: 125, percentage: 25 }
                ]
              }
            };
            global.document = {
              title: "My Coding Plan",
              body: { innerText: "GLM Coding Plan Usage" },
              documentElement: { innerText: "GLM Coding Plan Usage" },
              scripts: [{ textContent: JSON.stringify(quota) }],
              querySelectorAll: () => []
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $glm
            (async function() {
              for (let i = 0; i < 8 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              const limits = posted[0] && posted[0].data && posted[0].data.limits;
              if (posted[0].provider !== "glm" || !Array.isArray(limits) || limits.length !== 3) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              if (limits[0].type !== "TOKENS_LIMIT" || limits[1].number !== 7 || limits[2].type !== "TIME_LIMIT") {
                console.error(JSON.stringify(limits));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("GLM collector did not post trusted quota payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun glmCollectorReportsNoSubscriptionWhenPlanPageSaysNoSubscription() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected GLM runtime checks", node != null)

        val glm = ProviderWebCollectorScripts.build(ProviderId.GLM, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-glm-no-subscription-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = "GLM Coding Plan\nYou don't have any subscription";
            global.window = global;
            global.location = {
              hostname: "z.ai",
              pathname: "/manage-apikey/coding-plan/personal/my-plan",
              href: "https://z.ai/manage-apikey/coding-plan/personal/my-plan"
            };
            global.document = {
              title: "My Coding Plan",
              body: { innerText: pageText },
              documentElement: { innerText: pageText },
              scripts: [],
              querySelectorAll: () => []
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $glm
            (async function() {
              for (let i = 0; i < 8 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length !== 0 || errors.length === 0 || errors[0].errorKind !== "glm_no_subscription") {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              if (errors[0].message !== "You don't have any subscription") {
                console.error(JSON.stringify(errors[0]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("GLM no-subscription DOM was not reported as a planless state:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun opencodeCollectorConvertsVisibleDomUsageToTrustedPayload() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected OpenCode runtime checks", node != null)

        val opencode = ProviderWebCollectorScripts.build(ProviderId.OPENCODE, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-opencode-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = [
              "OpenCode Go",
              "Usage limits",
              "5 hour limit",
              "${'$'}3 of ${'$'}12 used",
              "75% remaining",
              "Resets in 2h",
              "Weekly limit",
              "${'$'}12 of ${'$'}30 used",
              "60% remaining",
              "Monthly limit",
              "${'$'}6 of ${'$'}60 used",
              "90% remaining",
              "Zen balance",
              "${'$'}4.50 credits"
            ].join("\n");
            global.window = global;
            global.location = {
              hostname: "opencode.ai",
              pathname: "/auth",
              href: "https://opencode.ai/auth"
            };
            global.document = {
              title: "OpenCode",
              body: { innerText: pageText },
              documentElement: { innerText: pageText },
              scripts: [],
              querySelectorAll: () => []
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $opencode
            (async function() {
              for (let i = 0; i < 10 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              const payload = posted[0];
              const limits = payload && payload.data && payload.data.limits;
              const credits = payload && payload.data && payload.data.credits;
              if (payload.provider !== "opencode" || payload.source !== "visible-dom") {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              if (!Array.isArray(limits) || limits.length !== 3) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              if (limits[0].label !== "Go 5 hour limit" || limits[0].remaining_percent !== 75) {
                console.error(JSON.stringify(limits[0]));
                process.exit(1);
              }
              if (limits[1].label !== "Go weekly limit" || limits[1].remaining_percent !== 60) {
                console.error(JSON.stringify(limits[1]));
                process.exit(1);
              }
              if (limits[2].label !== "Go monthly limit" || limits[2].remaining_percent !== 90) {
                console.error(JSON.stringify(limits[2]));
                process.exit(1);
              }
              if (!credits || credits.balance !== 4.5) {
                console.error(JSON.stringify(credits));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("OpenCode visible usage DOM was not converted to usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun opencodeCollectorConvertsKoreanGoUsagePageToTrustedPayload() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected OpenCode runtime checks", node != null)

        val opencode = ProviderWebCollectorScripts.build(ProviderId.OPENCODE, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-opencode-ko-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = [
              "현재 OpenCode Go를 구독 중입니다.",
              "롤링 사용량",
              "0%",
              "초기화까지 남은 시간: 5 시간 0 분",
              "주간 사용량",
              "0%",
              "초기화까지 남은 시간: 3 일 18 시간",
              "월간 사용량",
              "0%",
              "초기화까지 남은 시간: 29 일 23 시간"
            ].join("\n");
            global.window = global;
            global.location = {
              hostname: "opencode.ai",
              pathname: "/workspace/wrk_123/go",
              href: "https://opencode.ai/workspace/wrk_123/go"
            };
            global.document = {
              title: "OpenCode",
              body: { innerText: pageText },
              documentElement: { innerText: pageText },
              scripts: [],
              querySelectorAll: () => []
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $opencode
            (async function() {
              for (let i = 0; i < 10 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              const payload = posted[0];
              const limits = payload && payload.data && payload.data.limits;
              if (payload.provider !== "opencode" || payload.source !== "visible-dom") {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              if (!Array.isArray(limits) || limits.length !== 3) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              if (limits[0].label !== "Go 5 hour limit" || limits[0].used_percent !== 0 || limits[0].reset_text !== "Resets in 5h 0m") {
                console.error(JSON.stringify(limits[0]));
                process.exit(1);
              }
              if (limits[1].label !== "Go weekly limit" || limits[1].used_percent !== 0 || limits[1].reset_text !== "Resets in 3d 18h") {
                console.error(JSON.stringify(limits[1]));
                process.exit(1);
              }
              if (limits[2].label !== "Go monthly limit" || limits[2].used_percent !== 0 || limits[2].reset_text !== "Resets in 29d 23h") {
                console.error(JSON.stringify(limits[2]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("OpenCode Korean Go DOM was not converted to usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun geminiCollectorRunsOnUsagePageOrAuthenticatedAppShell() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://gemini.google.com/app", emptyMap(), "Gemini"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://gemini.google.com/usage", emptyMap(), "Gemini usage"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.GEMINI, "https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.GEMINI, "https://gemini.google.com/app"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.GEMINI, "https://gemini.google.com/usage"))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://accounts.google.com/signin", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.GEMINI, "https://accounts.google.com/signin"))
        assertTrue(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.GEMINI, "https://gemini.google.com/app"))
    }

    @Test
    fun opencodeCollectorRunsOnConsoleUsageResourcesOnly() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.OPENCODE, "https://opencode.ai/zen/go/usage"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.OPENCODE, "https://opencode.ai/workspace/wrk_123/go"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.OPENCODE, "https://opencode.ai/billing/credits"))
        assertTrue(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.OPENCODE, "https://opencode.ai/workspace/wrk_123/go"))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.OPENCODE, "https://opencode.ai/docs/go/"))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.OPENCODE, "https://opencode.ai/auth"))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.OPENCODE, "https://opencode.ai/docs/go/"))
    }

    @Test
    fun geminiCollectorStopsWithNoTrustedPayloadInsteadOfWaitingForOuterTimeout() {
        val script = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), "")

        assertTrue(script.contains("collectorStartTtlMs = 30000"))
        assertTrue(script.contains("state.href === href"))
        assertTrue(script.contains("collectAttempts < 6"))
        assertTrue(script.contains("gemini_no_trusted_payload"))
        assertTrue(script.contains("function clickGeminiSignIn()"))
        assertTrue(script.contains("c.awaitInteractiveLoginUsage && clickGeminiSignIn()"))
        assertTrue(script.contains("function(provider, force)"))
        assertTrue(script.contains("!force && state.provider === provider"))
        assertTrue(script.contains("__AIQuotaStartProviderCollector(\"gemini\", window.__AIQuotaCollector && window.__AIQuotaCollector.awaitInteractiveLoginUsage)"))
        assertTrue(script.indexOf("setTimeout(collectGeminiUsage, 5000)") < script.indexOf("gemini_no_trusted_payload"))
    }

    @Test
    fun geminiUsagePageDetectionFallsBackToInjectedPageUrl() {
        val script = ProviderWebCollectorScripts.build(
            ProviderId.GEMINI,
            emptyMap(),
            "",
            pageUrl = "https://gemini.google.com/usage"
        )

        assertTrue(script.contains("pageUrl: \"https://gemini.google.com/usage\""))
        assertTrue(script.contains("isGeminiUsagePageUrl(c.pageUrl)"))
    }

    @Test
    fun geminiCollectorReportsLoginRequiredOnMarketingShell() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Gemini runtime checks", node != null)

        val asset = java.io.File("src/main/assets/gemini_collector.js").readText()
        val gemini = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), asset)
        val path = Files.createTempFile("ai-quota-gemini-login-required-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = {
              hostname: "gemini.google.com",
              pathname: "/app",
              href: "https://gemini.google.com/app"
            };
            const pageText = "Gemini\n3.5 Flash\n로그인\nGemini와의 대화\n개인 AI 어시스턴트인 Gemini를 만나 보세요";
            global.document = {
              title: "Google Gemini",
              body: { innerText: pageText },
              documentElement: { innerText: pageText },
              scripts: [],
              querySelectorAll: () => []
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $gemini
            (async function() {
              for (let i = 0; i < 4 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length !== 0 || errors.length === 0 || errors[0].errorKind !== "gemini_login_required") {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Gemini marketing shell was not reported as login-required:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun geminiInteractiveCollectorClicksMarketingShellSignIn() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Gemini runtime checks", node != null)

        val gemini = ProviderWebCollectorScripts.build(
            providerId = ProviderId.GEMINI,
            cookies = emptyMap(),
            geminiCollectorAsset = "",
            awaitInteractiveLoginUsage = true
        )
        val path = Files.createTempFile("ai-quota-gemini-login-click-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            let clicked = false;
            global.window = global;
            global.location = {
              hostname: "gemini.google.com",
              pathname: "/app",
              href: "https://gemini.google.com/app"
            };
            const pageText = "Gemini\n3.5 Flash\n로그인\nGemini와의 대화\n개인 AI 어시스턴트인 Gemini를 만나 보세요";
            const loginElement = {
              innerText: "로그인",
              textContent: "로그인",
              href: "https://accounts.google.com/ServiceLogin?continue=https://gemini.google.com/usage",
              getAttribute: () => "",
              click: () => { clicked = true; }
            };
            global.document = {
              title: "Google Gemini",
              body: { innerText: pageText },
              documentElement: { innerText: pageText },
              scripts: [],
              querySelectorAll: () => [loginElement]
            };
            global.localStorage = { length: 0, key: () => null, getItem: () => "" };
            global.sessionStorage = { length: 0, key: () => null, getItem: () => "" };
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $gemini
            (async function() {
              for (let i = 0; i < 4 && !clicked && location.href.indexOf("accounts.google.com/ServiceLogin") < 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if ((!clicked && location.href.indexOf("accounts.google.com/ServiceLogin") < 0) || posted.length !== 0 || errors.length !== 0) {
                console.error(JSON.stringify({ clicked, href: location.href, posted, errors }));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Gemini marketing shell sign-in was not clicked:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun googleCollectorsUseObservedWebViewDataWithoutSyntheticCodeAssistGate() {
        val gemini = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), "")
        val antigravity = ProviderWebCollectorScripts.build(ProviderId.ANTIGRAVITY, emptyMap(), "")
        val geminiCollect = gemini.substringAfter("function collectGeminiUsage() {")
            .substringBefore("setTimeout(collectGeminiUsage, 1800)")
        val antigravityCollect = antigravity.substringAfter("function collectAntigravityUsage")
            .substringBefore("installAntigravityNetworkHook")

        assertTrue(gemini.contains("function postGeminiObservedPayload()"))
        assertTrue(geminiCollect.contains("if (postGeminiObservedPayload()) return;"))
        assertFalse(geminiCollect.contains("postGeminiWebFetchPayload"))
        assertFalse(geminiCollect.contains("postGeminiBridgePayload"))

        assertTrue(antigravity.contains("function postAntigravityObservedPayload()"))
        assertTrue(antigravityCollect.contains("if (postAntigravityObservedPayload()) return;"))
        assertFalse(antigravityCollect.contains("postAntigravityWebFetchPayload"))
        assertFalse(antigravityCollect.contains("postAntigravityBridgePayload"))
    }

    @Test
    fun googleCollectorsDoNotDependOnOauthClientRefreshFallback() {
        val gemini = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), "")
        val antigravity = ProviderWebCollectorScripts.build(ProviderId.ANTIGRAVITY, emptyMap(), "")

        assertFalse(gemini.contains("bridgeRequiresGoogleRelogin"))
        assertFalse(gemini.contains("OAuth login must be refreshed"))
        assertFalse(antigravity.contains("bridgeRequiresGoogleRelogin"))
        assertFalse(antigravity.contains("OAuth login must be refreshed"))
    }

    @Test
    fun geminiUsagePageTextIsConvertedToTrustedPayload() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Gemini runtime checks", node != null)

        val asset = java.io.File("src/main/assets/gemini_collector.js").readText()
        val gemini = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), asset)
        val path = Files.createTempFile("ai-quota-gemini-usage-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = [
              "Gemini",
              "사용량 한도",
              "PRO",
              "",
              "요금제의 한도에 따라 Gemini를 사용할 수 있는 시간이 결정됩니다.",
              "",
              "방금 업데이트됨",
              "",
              "현재 사용량",
              "",
              "4% 사용됨",
              "",
              "오전 12:26에 초기화",
              "",
              "주간 한도",
              "",
              "6월 2일 오후 12:26에 초기화",
              "",
              "0% 사용됨",
              "",
              "AI Pro보다 20배 더 많은 사용량 제공"
            ].join("\n");
            global.window = global;
            global.location = {
              hostname: "gemini.google.com",
              pathname: "/usage",
              href: "https://gemini.google.com/usage"
            };
            global.document = {
              title: "사용",
              body: { innerText: pageText },
              documentElement: { innerText: pageText },
              scripts: [{ textContent: "Google AI Plus legacy hidden state" }],
              querySelectorAll: () => []
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({
              stale: JSON.stringify({
                usage: {
                  x: [
                    { l: "5-hour limit", u: 0, t: "오후 5:09에 초기화" },
                    { l: "Weekly limit", u: 0, t: "6월 4일 오후 12:09에 초기화" }
                  ]
                }
              })
            });
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $gemini
            (async function() {
              for (let i = 0; i < 8 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const payload = posted[0];
              const rows = payload && payload.usage && payload.usage.x;
              if (!payload.account || payload.account.p !== "GEMINI_PRO") {
                console.error(JSON.stringify(payload.account));
                process.exit(1);
              }
              if (payload.provider !== "gemini" || !Array.isArray(rows) || rows.length !== 2) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              if (rows[0].l !== "5-hour limit" || rows[0].u !== 0.04 || rows[0].t !== "오전 12:26에 초기화") {
                console.error(JSON.stringify(rows[0]));
                process.exit(1);
              }
              if (rows[1].l !== "Weekly limit" || rows[1].u !== 0 || rows[1].t !== "6월 2일 오후 12:26에 초기화") {
                console.error(JSON.stringify(rows[1]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Gemini usage page text was not converted to usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun geminiCollectorTreatsUsedPercentOneAsOnePercent() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Gemini runtime checks", node != null)

        val asset = java.io.File("src/main/assets/gemini_collector.js").readText()
        val gemini = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), asset)
        val path = Files.createTempFile("ai-quota-gemini-used-percent-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = {
              hostname: "gemini.google.com",
              pathname: "/usage",
              href: "https://gemini.google.com/usage"
            };
            global.document = {
              title: "Gemini",
              body: { innerText: "Gemini" },
              documentElement: { innerText: "Gemini" },
              scripts: [],
              querySelectorAll: () => []
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({
              quota: JSON.stringify({ modelId: "gemini-2.5-pro", usedPercent: 1, resetTime: "2026-06-10T00:00:00Z" })
            });
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function(url) {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $gemini
            (async function() {
              for (let i = 0; i < 8 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              const rows = posted[0] && posted[0].usage && posted[0].usage.x;
              if (!Array.isArray(rows) || rows.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              if (rows[0].u !== 0.01) {
                console.error(JSON.stringify(rows[0]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Gemini collector treated usedPercent=1 as 100% used:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun geminiUsagePageDoesNotBorrowWeeklyResetForIdleFiveHourLimit() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Gemini runtime checks", node != null)

        val asset = java.io.File("src/main/assets/gemini_collector.js").readText()
        val gemini = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), asset)
        val path = Files.createTempFile("ai-quota-gemini-idle-reset-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = {
              hostname: "gemini.google.com",
              pathname: "/usage",
              href: "https://gemini.google.com/usage"
            };
            global.document = {
              title: "Gemini",
              body: { innerText: "Gemini" },
              documentElement: { innerText: "Gemini" },
              scripts: [],
              querySelectorAll: () => []
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function(url) {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $gemini
            const payload = window.SAGE_USAGE_EXTRACTOR.buildGeminiUsagePayload({
              usagePage: true,
              pageText: [
                "Gemini",
                "사용량 한도",
                "PRO",
                "현재 사용량",
                "0% 사용됨",
                "주간 한도",
                "6월 4일 오후 12:36에 초기화",
                "0% 사용됨"
              ].join("\n"),
              combinedText: "Gemini Google AI Pro hidden state",
              limits: []
            });
            const rows = payload && payload.usage && payload.usage.x;
            if (!payload.account || payload.account.p !== "GEMINI_PRO") {
              console.error(JSON.stringify(payload.account));
              process.exit(1);
            }
            if (!Array.isArray(rows) || rows.length !== 2) {
              console.error(JSON.stringify(payload));
              process.exit(1);
            }
            if (rows[0].l !== "5-hour limit" || rows[0].u !== 0 || Object.prototype.hasOwnProperty.call(rows[0], "t")) {
              console.error(JSON.stringify(rows[0]));
              process.exit(1);
            }
            if (rows[1].l !== "Weekly limit" || rows[1].u !== 0 || rows[1].t !== "6월 4일 오후 12:36에 초기화") {
              console.error(JSON.stringify(rows[1]));
              process.exit(1);
            }
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Gemini usage page should not borrow weekly reset for idle 5-hour limit:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun geminiUsagePageCanUseObservedRowsBeforeAllVisibleUsageTextLoads() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Gemini runtime checks", node != null)

        val asset = java.io.File("src/main/assets/gemini_collector.js").readText()
        val path = Files.createTempFile("ai-quota-gemini-visible-gate", ".js")
        val runtime = """
            global.window = global;
            global.PROVIDER_ID = "gemini";
            global.safeText = function(value) { return value === null || value === undefined ? "" : String(value); };
            global.parseNumber = function(value) { var n = Number(value); return Number.isFinite(n) ? n : null; };
            global.isNumber = function(value) { return typeof value === "number" && Number.isFinite(value); };
            $asset
            const payload = window.SAGE_USAGE_EXTRACTOR.buildGeminiUsagePayload({
              usagePage: true,
              pageText: "Gemini\n사용량 한도\nPRO\n현재 사용량\n4% 사용됨\n오전 12:26에 초기화",
              combinedText: "Google AI Plus hidden state",
              limits: [
                { l: "5-hour limit", u: 0, t: "오후 5:09에 초기화" },
                { l: "Weekly limit", u: 0, t: "6월 4일 오후 12:09에 초기화" }
              ]
            });
            const rows = payload && payload.usage && payload.usage.x;
            if (!payload || payload.account.p !== "GEMINI_PRO" || !Array.isArray(rows) || rows.length !== 2) {
              console.error(JSON.stringify(payload));
              process.exit(1);
            }
            if (rows[0].l !== "5-hour limit" || rows[0].u !== 0.04 || rows[0].t !== "오전 12:26에 초기화") {
              console.error(JSON.stringify(rows[0]));
              process.exit(1);
            }
            if (rows[1].l !== "Weekly limit" || rows[1].u !== 0 || rows[1].t !== "6월 4일 오후 12:09에 초기화") {
              console.error(JSON.stringify(rows[1]));
              process.exit(1);
            }
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Gemini usage page did not use observed rows before all visible usage text loaded:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun geminiUsagePageDefaultsToFreeWithoutPaidPlanBadge() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Gemini runtime checks", node != null)

        val asset = java.io.File("src/main/assets/gemini_collector.js").readText()
        val path = Files.createTempFile("ai-quota-gemini-plan-gate", ".js")
        val runtime = """
            global.window = global;
            global.PROVIDER_ID = "gemini";
            global.safeText = function(value) { return value === null || value === undefined ? "" : String(value); };
            global.parseNumber = function(value) { var n = Number(value); return Number.isFinite(n) ? n : null; };
            global.isNumber = function(value) { return typeof value === "number" && Number.isFinite(value); };
            $asset
            const payload = window.SAGE_USAGE_EXTRACTOR.buildGeminiUsagePayload({
              usagePage: true,
              pageText: [
                "사용량 한도",
                "방금 업데이트됨",
                "현재 사용량",
                "0% 사용됨",
                "오후 5:36에 초기화",
                "주간 한도",
                "6월 4일 오후 12:36에 초기화",
                "0% 사용됨"
              ].join("\n"),
              combinedText: "Google AI Plus hidden state",
              limits: []
            });
            const rows = payload && payload.usage && payload.usage.x;
            if (!payload || payload.account.p !== "GEMINI_FREE" || !Array.isArray(rows) || rows.length !== 2) {
              console.error(JSON.stringify(payload));
              process.exit(1);
            }
            if (rows[0].l !== "5-hour limit" || rows[0].u !== 0 || rows[0].t !== "오후 5:36에 초기화") {
              console.error(JSON.stringify(rows[0]));
              process.exit(1);
            }
            if (rows[1].l !== "Weekly limit" || rows[1].u !== 0 || rows[1].t !== "6월 4일 오후 12:36에 초기화") {
              console.error(JSON.stringify(rows[1]));
              process.exit(1);
            }
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Gemini usage page did not default missing paid plan badge to Free:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun claudeCollectorCanStartFromAuthenticatedSpaApiResources() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://claude.ai/api/account_profile"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://claude.ai/api/organizations/org_123/subscription_details?cached=true"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://claude.ai/api/bootstrap/org_123/current_user_access"))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/sms"))
    }

    @Test
    fun codexCollectorCanStartFromAuthenticatedChatGptResources() {
        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/codex/api/codex/usage"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/api/codex/usage"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/wham/usage"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/me"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/subscriptions?account_id=301d47ae-f627-4ddc-b2c2-330419bdc6ba"))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.CODEX, "https://chatgpt.com/auth/login", "https://chatgpt.com/backend-api/me"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.CODEX, "https://chatgpt.com/", "https://chatgpt.com/backend-api/me"))
    }

    @Test
    fun cursorCollectorCanStartFromDashboardUsageResources() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CURSOR, "https://cursor.com/api/usage?user=user_123"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CURSOR, "https://cursor.com/api/usage-summary"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.CURSOR, "https://cursor.com/dashboard", "https://cursor.com/api/usage?user=user_123"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.CURSOR, "https://cursor.com/login", "https://cursor.com/api/usage?user=user_123"))
    }

    @Test
    fun copilotCollectorCanStartFromBillingResources() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/github-copilot/chat/entitlement"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/github-copilot/chat/token"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/settings/billing/premium_requests_usage"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/settings/billing/copilot_usage_card?customer_id=abc123&period=3&query="))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/copilot_internal/user"))
        assertTrue(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.COPILOT,
                "about:blank",
                "https://github.com/github-copilot/chat/entitlement"
            )
        )
        assertTrue(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.COPILOT,
                "https://github.com/settings/billing/premium_requests_usage",
                "https://github.com/settings/billing/copilot_usage_card?customer_id=abc123&period=3&query="
            )
        )
        assertFalse(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.COPILOT,
                "https://github.com/login",
                "https://github.com/settings/billing/premium_requests_usage"
            )
        )
    }

    @Test
    fun claudeResourceTriggerDoesNotStartCollectorWhileMainPageIsLogin() {
        assertFalse(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.CLAUDE,
                "https://claude.ai/login",
                "https://claude.ai/api/bootstrap/org_123/current_user_access"
            )
        )
        assertTrue(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.CLAUDE,
                "https://claude.ai/new",
                "https://claude.ai/api/bootstrap/org_123/current_user_access"
            )
        )
    }

    @Test
    fun claudeCollectorPayloadIsIgnoredOnLoginOrGooglePages() {
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/sms"))
        assertTrue(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CLAUDE, "https://claude.ai/new"))
    }

    @Test
    fun collectorBridgePayloadsAndErrorsAreAcceptedOnlyFromTrustedProviderPages() {
        assertTrue(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CODEX, "https://chatgpt.com/", """{"provider":"codex"}"""))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CODEX, "https://chatgpt.com/", """{"provider":"claude"}"""))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CODEX, "https://example.com/", """{"provider":"codex"}"""))

        assertTrue(ProviderWebCollectorScripts.shouldAcceptCollectorError(ProviderId.CLAUDE, "https://claude.ai/new"))
        assertTrue(ProviderWebCollectorScripts.shouldAcceptCollectorError(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorError(ProviderId.CLAUDE, "https://example.com/login"))
    }

    @Test
    fun claudeCollectorExtractsResetUsageFromWebViewState() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Claude runtime checks", node != null)

        val claude = ProviderWebCollectorScripts.build(ProviderId.CLAUDE, mapOf("lastActiveOrg" to "org_test"), "")
        val path = Files.createTempFile("ai-quota-claude-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = [
              "Claude Pro",
              "Claude: Session Reset",
              "80% left",
              "Claude session limit resets in 2h 10m",
              "Claude: Weekly Reset",
              "84% left",
              "Claude weekly limit resets in 4d 12h",
              "Claude: Design Reset",
              "100% left",
              "Claude weekly Design limit resets in 4d 12h"
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/new" };
            global.document = {
              title: "Claude",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function(url) {
              if (String(url).includes("/api/organizations/") && String(url).includes("/usage")) {
                return { ok: false, status: 404, text: async () => "{}", json: async () => ({}) };
              }
              if (String(url).includes("/subscription_details")) {
                return { ok: true, status: 200, text: async () => JSON.stringify({ plan_name: "Claude Pro" }), json: async () => ({ plan_name: "Claude Pro" }) };
              }
              return { ok: true, status: 200, text: async () => JSON.stringify([{ id: "org_test", plan: "Claude Pro" }]), json: async () => ([{ id: "org_test", plan: "Claude Pro" }]) };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $claude
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const payload = posted[0];
              const usage = payload && payload.usage;
              if (!usage || !usage.session || !usage.weekly || !usage.design) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              if (usage.session.remaining_percent !== 80 || usage.weekly.remaining_percent !== 84 || usage.design.remaining_percent !== 100) {
                console.error(JSON.stringify(usage));
                process.exit(1);
              }
              if (usage.session.resetText !== "resets in 2h 10m" || usage.weekly.resetText !== "resets in 4d 12h") {
                console.error(JSON.stringify(usage));
                process.exit(1);
              }
              if (payload.plan !== "Pro") {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Claude WebView reset text was not converted to usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun claudeCollectorPostsAdditionalUsageContainersFromUsageApi() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Claude runtime checks", node != null)

        val claude = ProviderWebCollectorScripts.build(ProviderId.CLAUDE, mapOf("lastActiveOrg" to "org_test"), "")
        val path = Files.createTempFile("ai-quota-claude-extra-buckets-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = { pathname: "/new" };
            global.document = {
              title: "Claude",
              documentElement: { innerText: "Claude" },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function(url) {
              const value = String(url);
              if (value.includes("/api/organizations/") && value.includes("/usage")) {
                return {
                  ok: true,
                  status: 200,
                  text: async () => JSON.stringify({
                    models: {
                      "claude-haiku-4-5": { displayName: "Claude Haiku 4.5", remaining_percent: 90 }
                    },
                    quotaBuckets: [
                      { key: "long_context", label: "Long context", remainingPercent: 25 }
                    ]
                  }),
                  json: async () => ({
                    models: {
                      "claude-haiku-4-5": { displayName: "Claude Haiku 4.5", remaining_percent: 90 }
                    },
                    quotaBuckets: [
                      { key: "long_context", label: "Long context", remainingPercent: 25 }
                    ]
                  })
                };
              }
              if (value.includes("/subscription_details")) {
                return { ok: true, status: 200, text: async () => "{}", json: async () => ({}) };
              }
              return { ok: true, status: 200, text: async () => JSON.stringify([{ id: "org_test" }]), json: async () => ([{ id: "org_test" }]) };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $claude
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const usage = posted[0] && posted[0].usage;
              if (!usage || !usage.models || !Array.isArray(usage.quotaBuckets)) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Claude collector did not post model/container usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun claudeCollectorMergesUsageApiBucketsWithVisibleSessionUsage() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Claude runtime checks", node != null)

        val claude = ProviderWebCollectorScripts.build(ProviderId.CLAUDE, mapOf("lastActiveOrg" to "197643947"), "")
        val path = Files.createTempFile("ai-quota-claude-merged-usage-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const orgUuid = "00000000-0000-4000-8000-000000000001";
            const pageText = [
              "Claude Pro",
              "Claude: Session Reset",
              "95% left",
              "Claude session limit resets in 4h 10m",
              "Claude: Weekly Reset",
              "100% left",
              "Claude weekly limit resets in 6d 5h"
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/new" };
            global.document = {
              title: "Claude",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function(url) {
              const value = String(url);
              if (value.includes("/api/organizations/197643947/usage")) {
                return {
                  ok: false,
                  status: 400,
                  text: async () => JSON.stringify({ error: { message: "numeric id is not accepted for usage" } }),
                  json: async () => ({ error: { message: "numeric id is not accepted for usage" } })
                };
              }
              if (value.includes("/api/organizations/" + orgUuid + "/usage")) {
                return {
                  ok: true,
                  status: 200,
                  text: async () => JSON.stringify({
                    models: {
                      "claude-haiku-4-5": { displayName: "Claude Haiku 4.5", remaining_percent: 90 }
                    },
                    quotaBuckets: [
                      { key: "long_context", label: "Long context", remainingPercent: 25 }
                    ]
                  }),
                  json: async () => ({
                    models: {
                      "claude-haiku-4-5": { displayName: "Claude Haiku 4.5", remaining_percent: 90 }
                    },
                    quotaBuckets: [
                      { key: "long_context", label: "Long context", remainingPercent: 25 }
                    ]
                  })
                };
              }
              if (value.includes("/subscription_details")) {
                return { ok: true, status: 200, text: async () => "{}", json: async () => ({}) };
              }
              return {
                ok: true,
                status: 200,
                text: async () => JSON.stringify([{ id: 197643947, uuid: orgUuid }]),
                json: async () => ([{ id: 197643947, uuid: orgUuid }])
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $claude
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const usage = posted[0] && posted[0].usage;
              if (!usage || !usage.session || !usage.weekly || !usage.models || !Array.isArray(usage.quotaBuckets)) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
              if (usage.session.remaining_percent !== 95 || usage.weekly.remaining_percent !== 100) {
                console.error(JSON.stringify(usage));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Claude collector dropped usage API buckets after visible usage was found:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun claudeCollectorDoesNotBorrowWeeklyResetForIdleSession() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Claude runtime checks", node != null)

        val claude = ProviderWebCollectorScripts.build(ProviderId.CLAUDE, mapOf("lastActiveOrg" to "org_test"), "")
        val path = Files.createTempFile("ai-quota-claude-idle-session-reset-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = [
              "Claude Pro",
              "Claude: Session Reset",
              "80% left",
              "Claude: Weekly Reset",
              "84% left",
              "Claude weekly limit resets in 4d 12h"
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/new" };
            global.document = {
              title: "Claude",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function(url) {
              if (String(url).includes("/api/organizations/") && String(url).includes("/usage")) {
                return { ok: false, status: 404, text: async () => "{}", json: async () => ({}) };
              }
              if (String(url).includes("/subscription_details")) {
                return { ok: true, status: 200, text: async () => JSON.stringify({ plan_name: "Claude Pro" }), json: async () => ({ plan_name: "Claude Pro" }) };
              }
              return { ok: true, status: 200, text: async () => JSON.stringify([{ id: "org_test", plan: "Claude Pro" }]), json: async () => ([{ id: "org_test", plan: "Claude Pro" }]) };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $claude
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const usage = posted[0] && posted[0].usage;
              if (!usage || !usage.session || !usage.weekly) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
              if (usage.session.remaining_percent !== 80 || Object.prototype.hasOwnProperty.call(usage.session, "resetText")) {
                console.error(JSON.stringify(usage.session));
                process.exit(1);
              }
              if (usage.weekly.remaining_percent !== 84 || usage.weekly.resetText !== "resets in 4d 12h") {
                console.error(JSON.stringify(usage.weekly));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Claude WebView state should not borrow weekly reset for idle session:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun claudeCollectorExtractsPlanFromVisibleWebPlanText() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Claude runtime checks", node != null)

        val claude = ProviderWebCollectorScripts.build(ProviderId.CLAUDE, mapOf("lastActiveOrg" to "org_test"), "")
        val path = Files.createTempFile("ai-quota-claude-visible-plan-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = [
              "Claude Settings",
              "Current plan",
              "Max 5x",
              "Claude: Session Reset",
              "80% left",
              "Claude session limit resets in 2h 10m"
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/settings/account" };
            global.document = {
              title: "Claude",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function(url) {
              if (String(url).includes("/api/organizations/") && String(url).includes("/usage")) {
                return { ok: false, status: 404, text: async () => "{}", json: async () => ({}) };
              }
              if (String(url).includes("/subscription_details")) {
                return { ok: false, status: 404, text: async () => "{}", json: async () => ({}) };
              }
              return { ok: true, status: 200, text: async () => JSON.stringify([{ id: "org_test" }]), json: async () => ([{ id: "org_test" }]) };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $claude
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              if (posted[0].plan !== "Max 5x") {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Claude WebView plan text was not included in usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun copilotCollectorExtractsUsageFromGitHubWebViewState() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Copilot runtime checks", node != null)

        val copilot = ProviderWebCollectorScripts.build(ProviderId.COPILOT, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-copilot-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = { pathname: "/settings/copilot/features", href: "https://github.com/settings/copilot/features" };
            global.document = {
              title: "GitHub Copilot",
              documentElement: { innerText: "GitHub Copilot" },
              scripts: [],
              querySelector: () => null,
              createElement: () => ({ innerHTML: "", value: "" })
            };
            global.__NEXT_DATA__ = {
              props: {
                pageProps: {
                  copilot: {
                    planName: "Copilot Pro",
                    resetDate: "2026-06-15",
                    usage: [
                      { title: "Chat messages", usedPercent: 1 },
                      { title: "Code completion", remainingPercent: 100 }
                    ]
                  }
                }
              }
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value)),
              fetchCopilotJson: () => JSON.stringify({ ok: false, status: 404, json: {} }),
              fetchCopilotJsonWithAuthorization: () => JSON.stringify({ ok: false, status: 404, json: {} })
            };
            global.fetch = async function(url) {
              return {
                ok: false,
                status: 404,
                text: async () => "{}"
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $copilot
            (async function() {
              for (let i = 0; i < 14 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const payload = posted[0];
              if (payload.plan !== "Copilot Pro") {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              const quotas = payload.quotas || {};
              if (!quotas.chat || !quotas.completions || quotas.chat.used_percent !== 1 || quotas.completions.used_percent !== 0) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              if (quotas.quota_reset_date !== "2026-06-15") {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Copilot GitHub WebView state was not converted to usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun cursorCollectorExtractsUsageFromWebViewState() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Cursor runtime checks", node != null)

        val cursor = ProviderWebCollectorScripts.build(ProviderId.CURSOR, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-cursor-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = { pathname: "/dashboard", href: "https://cursor.com/dashboard" };
            global.document = {
              title: "Cursor",
              documentElement: { innerText: "Cursor dashboard" },
              scripts: [],
              querySelector: () => null,
              getElementById: () => null
            };
            global.__NEXT_DATA__ = {
              props: {
                pageProps: {
                  dashboard: {
                    membershipType: "Pro",
                    billingCycleEnd: 1781677951075,
                    planUsage: {
                      totalPercentUsed: 6,
                      autoPercentUsed: 3,
                      apiPercentUsed: 1,
                      resetAt: 1781677951075,
                      breakdown: {
                        onDemand: { remaining: 19, limit: 20 }
                      }
                    },
                    requestUsage: {
                      premium: { numRequests: 12, maxRequestUsage: 100 }
                    }
                  }
                }
              }
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value)),
              fetchCursorJson: () => JSON.stringify({ ok: false, status: 404, json: {} })
            };
            global.fetch = async function() {
              return {
                ok: false,
                status: 404,
                clone() { return { text: async () => "{}" }; },
                text: async () => "{}"
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $cursor
            (async function() {
              for (let i = 0; i < 14 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const payload = posted[0];
              if (payload.membershipType !== "Pro") {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              if (!payload.planUsage || payload.planUsage.totalPercentUsed !== 6) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              if (payload.planUsage.autoPercentUsed !== 3 || payload.planUsage.apiPercentUsed !== 1) {
                console.error(JSON.stringify(payload.planUsage));
                process.exit(1);
              }
              if (!payload.planUsage.breakdown || !payload.planUsage.breakdown.onDemand) {
                console.error(JSON.stringify(payload.planUsage));
                process.exit(1);
              }
              const requestBuckets = payload.requestUsage ? Object.values(payload.requestUsage) : [];
              if (!requestBuckets.some((bucket) => bucket && bucket.numRequests === 12 && bucket.maxRequestUsage === 100)) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Cursor WebView state was not converted to usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun antigravityCollectorExtractsUsageFromWebViewState() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Antigravity runtime checks", node != null)

        val antigravity = ProviderWebCollectorScripts.build(ProviderId.ANTIGRAVITY, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-antigravity-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = { pathname: "/docs/plans", href: "https://antigravity.google/docs/plans" };
            global.document = {
              title: "Antigravity",
              documentElement: { innerText: "Antigravity Plus\nAntigravity Session\n80% remaining\nResets in 5h" },
              scripts: [],
              querySelector: () => null
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({
              usage: JSON.stringify({
                plan: "ANTIGRAVITY_PLUS",
                usage: {
                  x: [
                    { l: "Antigravity Session", u: 20, t: "Resets in 5h" },
                    { l: "Antigravity Weekly", remaining_percent: 88, resetAt: "2026-06-01T00:00:00Z" }
                  ]
                }
              })
            });
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: false,
                status: 404,
                clone() { return { text: async () => "{}" }; },
                text: async () => "{}"
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $antigravity
            (async function() {
              for (let i = 0; i < 8 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const payload = posted[0];
              const rows = payload && payload.usage && payload.usage.x;
              if (payload.plan !== "ANTIGRAVITY_PLUS" || !Array.isArray(rows) || rows.length !== 2) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Antigravity WebView state was not converted to usage payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun latePayloadFromPreviousProviderIsIgnoredByCurrentRefreshJob() {
        val claudePayload = """{"provider":"claude","lines":[{"label":"Claude Session","remainingPercent":0.6}]}"""
        val codexPayload = """{"provider":"codex","lines":[{"label":"Codex Session","remainingPercent":0.5}]}"""

        assertFalse(
            ProviderWebCollectorScripts.shouldAcceptCollectorPayload(
                ProviderId.CODEX,
                "https://chatgpt.com/",
                claudePayload
            )
        )
        assertTrue(
            ProviderWebCollectorScripts.shouldAcceptCollectorPayload(
                ProviderId.CODEX,
                "https://chatgpt.com/",
                codexPayload
            )
        )
    }

    @Test
    fun collectorScriptsUseSameSessionProviderApis() {
        val claude = ProviderWebCollectorScripts.build(ProviderId.CLAUDE, mapOf("lastActiveOrg" to "org_123"), "")
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val gemini = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), "window.SAGE_USAGE_EXTRACTOR={buildGeminiUsagePayload:function(){return null;}};")
        val copilot = ProviderWebCollectorScripts.build(ProviderId.COPILOT, emptyMap(), "")
        val cursor = ProviderWebCollectorScripts.build(ProviderId.CURSOR, emptyMap(), "")
        val antigravity = ProviderWebCollectorScripts.build(ProviderId.ANTIGRAVITY, emptyMap(), "")

        assertTrue(claude.contains("/api/organizations/"))
        assertTrue(claude.contains("subscription_details"))
        assertTrue(claude.contains("pickClaudePlan"))
        assertTrue(claude.contains("probeClaudeSession"))
        assertTrue(claude.contains("__AIQuotaClaudeNetworkRows"))
        assertTrue(claude.contains("installClaudeNetworkHook"))
        assertTrue(claude.contains("scanClaudePageState"))
        assertTrue(claude.contains("scanClaudeUsageText"))
        assertTrue(claude.contains("Claude: Session Reset"))
        assertTrue(claude.contains("Claude weekly Design limit resets"))
        assertTrue(claude.contains("usageOk"))
        assertFalse(claude.contains("claude_usage_unavailable"))
        assertTrue(codex.contains("__AIQuotaCodexNetworkRows"))
        assertTrue(codex.contains("installCodexNetworkHook"))
        assertTrue(codex.contains("pushCodexNetworkRow"))
        assertTrue(codex.contains("/api/auth/session"))
        assertTrue(codex.contains("observedAccountId"))
        assertTrue(codex.contains("c.observedAccountId"))
        assertTrue(codex.contains("/backend-api/me"))
        assertTrue(codex.contains("/backend-api/accounts/check"))
        assertTrue(codex.contains("pickCodexAccountId"))
        assertTrue(codex.contains("extractCodexUsageFromRows"))
        assertTrue(codex.contains("scanCodexUsageCandidate"))
        assertTrue(codex.contains("scanCodexPageState"))
        assertTrue(codex.contains("sessionOk"))
        assertTrue(codex.contains("codex_usage_unavailable"))
        assertFalse(codex.contains("hasCodexUsagePayload(result.usage)"))
        assertFalse(codex.contains("/backend-api/codex/api/codex/usage"))
        assertFalse(codex.contains("/backend-api/api/codex/usage"))
        assertFalse(codex.contains("/backend-api/wham/usage"))
        assertTrue(codex.contains("https://chatgpt.com/codex/cloud/settings/analytics#usage"))
        assertTrue(gemini.contains("buildGeminiUsagePayload"))
        assertFalse(gemini.contains("fetchGeminiWebSessionUsagePayload"))
        assertFalse(gemini.contains("postGeminiBridgePayload"))
        assertFalse(gemini.contains("cloudcode-pa.googleapis.com"))
        assertTrue(gemini.contains("__AIQuotaGeminiNetworkRows"))
        assertTrue(gemini.contains("XMLHttpRequest"))
        assertTrue(gemini.contains("remainingFraction"))
        assertTrue(gemini.contains("modelId"))
        assertTrue(gemini.contains("extractJsonCandidates"))
        assertTrue(gemini.contains("c.pageText"))
        assertTrue(gemini.contains("setTimeout(collectGeminiUsage, 5000)"))
        assertTrue(gemini.contains("remainingFractionValue <= 1 ? remainingFractionValue : remainingFractionValue / 100"))
        assertFalse(gemini.contains("100 - remaining"))
        assertFalse(gemini.contains("Gemini Web Session"))
        assertFalse(gemini.contains("Quota is not exposed by the current Gemini web page."))
        assertFalse(gemini.contains("gemini_usage_unavailable"))
        assertTrue(gemini.contains("건너뛰기"))
        assertTrue(copilot.contains("github-copilot/chat/entitlement"))
        assertTrue(copilot.contains("github-copilot/chat/token"))
        assertTrue(copilot.contains("featuresPageUsageFromText"))
        assertTrue(copilot.contains("collectFeaturesUsageWithMetadata"))
        assertTrue(copilot.contains("featuresPlanFromText"))
        assertTrue(copilot.contains("무료"))
        assertFalse(copilot.contains("臾대즺"))
        assertTrue(copilot.contains("__AIQuotaCopilotNetworkRows"))
        assertTrue(copilot.contains("installCopilotNetworkHook"))
        assertTrue(copilot.contains("scanCopilotPageState"))
        assertTrue(copilot.contains("scanCopilotUsageCandidate"))
        assertTrue(copilot.contains("scanCopilotUsageText"))
        assertTrue(copilot.contains("remainingPercent"))
        assertTrue(copilot.contains("quota_reset_date"))
        assertTrue(copilot.contains("settings/copilot/features"))
        assertTrue(copilot.contains("fetchCopilotJsonWithAuthorization"))
        assertTrue(copilot.contains("GitHub-Bearer"))
        assertTrue(copilot.contains("githubApiAuthorizationHeaderFromToken"))
        assertTrue(copilot.contains("https://api.github.com/copilot_internal/user"))
        assertTrue(copilot.contains("https://github.com/copilot_internal/user"))
        assertTrue(copilot.contains("AIQuotaCopilot internal status="))
        assertTrue(copilot.contains("AIQuotaCopilot internal_session status="))
        assertTrue(copilot.contains("AIQuotaCopilot settings status="))
        assertTrue(copilot.contains("limitedIdx="))
        assertTrue(copilot.contains("GitHub-Verified-Fetch"))
        assertTrue(copilot.contains("fetch-nonce"))
        assertTrue(copilot.contains("https://github.com/settings/copilot"))
        assertTrue(copilot.contains("/settings/billing/copilot_usage_card"))
        assertTrue(copilot.contains("limited_user_quotas"))
        assertTrue(copilot.contains("monthly_quotas"))
        assertTrue(copilot.contains("script[data-target='react-app.embeddedData']"))
        assertTrue(copilot.contains("nativeJson(\"https://github.com/settings/billing/premium_requests_usage\")"))
        assertTrue(copilot.contains("premium_billing"))
        assertTrue(copilot.contains("amountLimit !== null && amountLimit > 0"))
        assertFalse(copilot.contains("line.remaining = remaining"))
        assertTrue(cursor.contains("/api/auth/stripe"))
        assertTrue(cursor.contains("/api/usage"))
        assertTrue(cursor.contains("/api/auth/usage"))
        assertTrue(cursor.contains("__AIQuotaCursorNetworkRows"))
        assertTrue(cursor.contains("pushCursorNetworkRow"))
        assertTrue(cursor.contains("scanCursorPageState"))
        assertTrue(cursor.contains("window.__NEXT_DATA__"))
        assertTrue(cursor.contains("/api/usage?user="))
        assertTrue(cursor.contains("hasTrustedCursorPayload"))
        assertTrue(cursor.contains("hasTrustedRequestUsage"))
        assertTrue(cursor.contains("fetchCursorJson"))
        assertTrue(cursor.contains("api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage"))
        assertTrue(cursor.contains("api2.cursor.sh/aiserver.v1.DashboardService/GetPlanInfo"))
        assertTrue(cursor.contains("api2.cursor.sh/aiserver.v1.DashboardService/GetCreditGrantsBalance"))
        assertTrue(cursor.contains("/api/dashboard/get-credit-grants-balance"))
        assertTrue(cursor.contains("requestUsage"))
        assertFalse(cursor.contains("cursor_usage_unavailable"))
        assertTrue(antigravity.contains("scanAntigravityPageState"))
        assertFalse(antigravity.contains("fetchAntigravityWebSessionUsagePayload"))
        assertFalse(antigravity.contains("postAntigravityBridgePayload"))
        assertFalse(antigravity.contains("cloudcode-pa.googleapis.com"))
        assertTrue(antigravity.contains("__AIQuotaAntigravityNetworkRows"))
        assertTrue(antigravity.contains("this.__aiQuotaAntigravityUrl = url"))
        assertTrue(antigravity.contains("pushAntigravityNetworkRow(this.__aiQuotaAntigravityUrl || \"\", this.responseText || \"\")"))
        assertTrue(antigravity.contains("hasTrustedAntigravityPayload"))
        assertTrue(antigravity.contains("normalizePlan"))
        assertFalse(antigravity.contains("ANTIGRAVITY_PLUS"))
        assertFalse(antigravity.contains("antigravity_usage_unavailable"))
        listOf(claude, codex, gemini, copilot, antigravity, cursor).forEach { script ->
            assertTrue(script.contains("__AIQuotaStartProviderCollector"))
            assertTrue(script.contains("__AIQuotaProviderCollectorState"))
            assertTrue(script.contains("collectorStartTtlMs"))
            assertFalse(script.contains("return true;\n                };"))
            assertFalse(script.contains("__AIQuotaCollectorRunning"))
            assertFalse(script.contains("__AIQuotaProviderCollectorRunning_"))
            assertTrue(script.contains("credentials: \"include\""))
            assertTrue(script.contains("AIQuotaCollectorBridge.postUsagePayload"))
        }
    }

    @Test
    fun codexCollectorUsesChatGptPageStateInsteadOfDirectUsageApi() {
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")

        assertTrue(codex.contains("buildCodexDiagnostics"))
        assertTrue(codex.contains("codexUsageStatus"))
        assertTrue(codex.contains("codexUsageStatuses"))
        assertTrue(codex.contains("looksLikeChatGptLogin"))
        assertTrue(codex.contains("codex_auth_required"))
        assertTrue(codex.contains("tokenPresent"))
        assertTrue(codex.contains("accountIdPresent"))
        assertTrue(codex.contains("rowsUsagePresent"))
        assertTrue(codex.contains("safeCodexAuthHints"))
        assertTrue(codex.contains("sessionAuthHint"))
        assertTrue(codex.contains("storageAuthHint"))
        assertTrue(codex.contains("whamSkippedNoToken"))
        assertTrue(codex.contains("sessionStatus"))
        assertTrue(codex.contains("meStatus"))
        assertTrue(codex.contains("accountCheckStatus"))
        assertTrue(codex.contains("__AIQuotaCodexNetworkRows"))
        assertTrue(codex.contains("installCodexNetworkHook"))
        assertTrue(codex.contains("scanCodexPageState"))
        assertTrue(codex.contains("scanCodexUsageText"))
        assertTrue(codex.contains("remainingPercentFromText"))
        assertTrue(codex.contains("Codex \" + KO_CODEX_WEEKLY + \" \" + KO_CODEX_SESSION"))
        assertTrue(codex.contains("looksLikeCodexAccountId"))
        assertTrue(codex.contains("[0-9a-fA-F]{8}"))
        assertFalse(codex.contains("/backend-api/codex/api/codex/usage"))
        assertFalse(codex.contains("/backend-api/api/codex/usage"))
        assertFalse(codex.contains("/backend-api/wham/usage"))
        assertFalse(codex.contains("ChatGPT-Account-Id"))
        assertFalse(codex.contains("id_token;"))
    }

    @Test
    fun codexCollectorDoesNotAttachInternalUsedPercentComparison() {
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")

        assertFalse(codex.contains("internal_used_percent"))
        assertFalse(codex.contains("attachCodexInternalUsageComparison"))
    }

    @Test
    fun codexSessionProbeFetchesAreTimeoutBounded() {
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")

        assertTrue(codex.contains("AbortController"))
        assertTrue(codex.contains("timeoutMs"))
        assertTrue(codex.contains("Promise.race"))
        assertTrue(codex.contains("Promise.all"))
        assertTrue(codex.contains("clearTimeout"))
    }

    @Test
    fun codexPageStateScanIsBudgetBounded() {
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")

        assertTrue(codex.contains("codexScanBudget"))
        assertTrue(codex.contains("keys.length && keyIndex < 80"))
        assertTrue(codex.contains("index < value.length && index < 80"))
    }

    @Test
    fun codexCollectorDoesNotBlockUsageNavigationOnSubscriptionPlanFetch() {
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val probe = codex.substringAfter("async function probeCodexSession()")
            .substringBefore("function looksLikeChatGptApp()")

        assertTrue(probe.indexOf("result.usage = scanCodexPageState(result.accountId)") < probe.indexOf("fetchCodexSubscriptionPlan(result.accountId)"))
        assertTrue(probe.contains("if (!result.plan)"))
        assertTrue(codex.contains("\" subscription=\" + result.subscriptionStatus"))
        assertTrue(codex.contains("\" plan=\" + !!result.plan"))
    }

    @Test
    fun codexCollectorUsesAccountMetadataPlanWhenSubscriptionEndpointRejects() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Codex runtime checks", node != null)

        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-codex-account-plan-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = [
              "Codex",
              "Codex 5 hour usage limit",
              "97% left",
              "Resets in 1h 24m",
              "Codex Weekly usage limit",
              "70% left",
              "Resets in 2d 21h"
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/codex/cloud/settings/analytics", href: "https://chatgpt.com/codex/cloud/settings/analytics#usage" };
            global.document = {
              title: "Codex",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.AbortController = class {
              constructor() { this.signal = {}; }
              abort() {}
            };
            global.fetch = async function(url) {
              const value = String(url);
              if (value.includes("/backend-api/subscriptions")) {
                return {
                  ok: false,
                  status: 401,
                  clone() { return { text: async () => JSON.stringify({ error: "unauthorized" }) }; },
                  json: async () => ({ error: "unauthorized" })
                };
              }
              if (value.includes("/backend-api/me")) {
                return {
                  ok: true,
                  status: 200,
                  clone() { return { text: async () => JSON.stringify({ account: { active_subscription: { plan: { id: "prolite" } } } }) }; },
                  json: async () => ({ account: { active_subscription: { plan: { id: "prolite" } } } })
                };
              }
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "{}" }; },
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $codex
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              if (posted[0].plan !== "Pro 5x") {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Codex account metadata plan was not used when subscriptions returned 401:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun codexCollectorReportsUnavailableBeforeAndroidTimeout() {
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")

        assertTrue(codex.contains("attempts >= 8 && (result.sessionOk || looksLikeChatGptApp())"))
        assertTrue(codex.contains("c.fail(\"codex_usage_unavailable\""))
    }

    @Test
    fun codexInteractiveLoginCollectorWaitsForTrustedUsagePayloadBeforeReturning() {
        val codex = ProviderWebCollectorScripts.build(
            providerId = ProviderId.CODEX,
            cookies = emptyMap(),
            geminiCollectorAsset = "",
            awaitInteractiveLoginUsage = true
        )

        assertTrue(codex.contains("awaitInteractiveLoginUsage: true"))
        assertTrue(codex.contains("continueCodexInteractiveLoginUntilUsagePayload"))
        assertTrue(codex.contains("AIQuotaCodex awaiting usage payload"))
        assertTrue(codex.contains("if (continueCodexInteractiveLoginUntilUsagePayload(\"login\"))"))
        assertTrue(codex.contains("if (continueCodexInteractiveLoginUntilUsagePayload(\"usage_unavailable\"))"))
        assertTrue(codex.indexOf("continueCodexInteractiveLoginUntilUsagePayload(\"usage_unavailable\")") < codex.indexOf("c.fail(\"codex_usage_unavailable\""))
    }

    @Test
    fun codexCollectorNavigatesAuthenticatedRootToUsageDashboard() {
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")

        assertTrue(codex.contains("codexUsageDashboardUrls"))
        assertTrue(codex.contains("codexUsageNavigationAttempts"))
        assertTrue(codex.contains("__AIQuotaCodexUsageNavigationAttempts"))
        assertTrue(codex.contains("sessionStorage.setItem(codexUsageNavigationStateKey"))
        assertTrue(codex.contains("navigateCodexUsageDashboardIfNeeded"))
        assertTrue(codex.contains("location.assign"))
        assertTrue(codex.contains("codexUsageNavigationAttempts >= codexUsageDashboardUrls.length"))
        assertTrue(codex.contains("Math.min(codexUsageNavigationAttempts, codexUsageDashboardUrls.length - 1)"))
        assertTrue(codex.contains("https://chatgpt.com/codex/cloud/settings/analytics#usage"))
        assertTrue(codex.contains("https://chatgpt.com/codex/settings/usage"))
    }

    @Test
    fun codexCollectorNavigatesToUsageDashboardOnFirstAuthenticatedProbe() {
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")

        assertFalse(codex.contains("attempts < 2"))
        assertTrue(codex.contains("codexInitialProbeDelayMs = 100"))
        assertTrue(codex.contains("codexProbeRetryDelayMs = 600"))
        assertTrue(codex.contains("setTimeout(runProbe, codexInitialProbeDelayMs)"))
        assertTrue(codex.contains("setTimeout(runProbe, codexProbeRetryDelayMs)"))
    }

    @Test
    fun codexCollectorExtractsKoreanChatGptUsageText() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Codex runtime checks", node != null)

        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-codex-korean-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const kr = (...codes) => String.fromCharCode(...codes);
            const pageText = [
              "ChatGPT",
              "Codex 5" + kr(0xC2DC, 0xAC04) + " " + kr(0xC138, 0xC158),
              "98% " + kr(0xB0A8, 0xC74C),
              "4" + kr(0xC2DC, 0xAC04) + " 56" + kr(0xBD84) + " " + kr(0xD6C4) + " " + kr(0xCD08, 0xAE30, 0xD654),
              "Codex " + kr(0xC8FC, 0xAC04) + " " + kr(0xC138, 0xC158),
              "45% " + kr(0xB0A8, 0xC74C),
              "5" + kr(0xC77C) + " 12" + kr(0xC2DC, 0xAC04) + " " + kr(0xD6C4) + " " + kr(0xCD08, 0xAE30, 0xD654)
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/" };
            global.document = {
              title: "ChatGPT",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.AbortController = class {
              constructor() { this.signal = {}; }
              abort() {}
            };
            global.fetch = async function(url) {
              if (String(url).includes("/backend-api/subscriptions")) {
                return {
                  ok: true,
                  status: 200,
                  clone() { return { text: async () => JSON.stringify({ data: [{ active_subscription: { plan: { id: "prolite", display_name: "ChatGPT Pro Lite" } } }] }) }; },
                  json: async () => ({ data: [{ active_subscription: { plan: { id: "prolite", display_name: "ChatGPT Pro Lite" } } }] })
                };
              }
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $codex
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const limits = posted[0] && posted[0].usage && posted[0].usage.rate_limits;
              if (!limits || !limits.primary_window || !limits.secondary_window) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
              if (limits.primary_window.used_percent !== 2 || limits.secondary_window.used_percent !== 55) {
                console.error(JSON.stringify(limits));
                process.exit(1);
              }
              if (limits.primary_window.remaining_percent !== 98 || limits.secondary_window.remaining_percent !== 45) {
                console.error(JSON.stringify(limits));
                process.exit(1);
              }
              if (posted[0].plan !== "Pro 5x") {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Codex Korean usage text was not converted to compact payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun codexCollectorExtractsKoreanUsageRowsWithoutCodexPrefix() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Codex runtime checks", node != null)

        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-codex-prefixless-korean-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const kr = (...codes) => String.fromCharCode(...codes);
            const pageText = [
              "Codex",
              "5" + kr(0xC2DC, 0xAC04) + " " + kr(0xC138, 0xC158),
              "92% " + kr(0xB0A8, 0xC74C),
              "4" + kr(0xC2DC, 0xAC04) + " 12" + kr(0xBD84) + " " + kr(0xD6C4) + " " + kr(0xCD08, 0xAE30, 0xD654),
              kr(0xC8FC, 0xAC04) + " " + kr(0xC138, 0xC158),
              "28% " + kr(0xB0A8, 0xC74C),
              "3" + kr(0xC77C) + " 1" + kr(0xC2DC, 0xAC04) + " " + kr(0xD6C4) + " " + kr(0xCD08, 0xAE30, 0xD654)
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/codex/cloud/settings/analytics" };
            global.document = {
              title: "Codex",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.AbortController = class {
              constructor() { this.signal = {}; }
              abort() {}
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $codex
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const limits = posted[0] && posted[0].usage && posted[0].usage.rate_limits;
              if (!limits || !limits.primary_window || !limits.secondary_window) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
              if (limits.primary_window.label !== "Codex Session" || limits.secondary_window.label !== "Codex Weekly") {
                console.error(JSON.stringify(limits));
                process.exit(1);
              }
              if (limits.primary_window.used_percent !== 8 || limits.secondary_window.used_percent !== 72) {
                console.error(JSON.stringify(limits));
                process.exit(1);
              }
              if (limits.primary_window.remaining_percent !== 92 || limits.secondary_window.remaining_percent !== 28) {
                console.error(JSON.stringify(limits));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Codex prefixless Korean usage rows were not converted to compact payload:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun codexCollectorRejectsCompactAppStateRowsWithoutVisibleDom() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Codex runtime checks", node != null)

        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-codex-prefixless-state-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const kr = (...codes) => String.fromCharCode(...codes);
            global.window = global;
            global.location = { pathname: "/codex/cloud/settings/analytics" };
            global.document = {
              title: "Codex",
              documentElement: { innerText: "Codex" },
              scripts: [],
              querySelector: () => ({})
            };
            global.__APP_DATA__ = {
              usage: {
                x: [
                  { l: "5" + kr(0xC2DC, 0xAC04) + " " + kr(0xC138, 0xC158), u: 0.08 },
                  { l: kr(0xC8FC, 0xAC04) + " " + kr(0xC138, 0xC158), u: 0.72 }
                ]
              }
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.AbortController = class {
              constructor() { this.signal = {}; }
              abort() {}
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $codex
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length !== 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Codex compact app state should not be accepted without visible DOM:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun codexCollectorRejectsResetCompactRowsWithoutVisibleDom() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Codex runtime checks", node != null)

        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-codex-reset-remaining-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = { pathname: "/codex/cloud/settings/analytics" };
            global.document = {
              title: "Codex",
              documentElement: { innerText: "Codex usage" },
              scripts: [],
              querySelector: () => ({})
            };
            global.__APP_DATA__ = {
              usage: {
                x: [
                  { l: "Codex Session", remaining_percent: 100, t: "Resets in 5h" },
                  { l: "Codex Weekly", remainingFraction: 1, t: "Resets in 7d" }
                ]
              }
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.AbortController = class {
              constructor() { this.signal = {}; }
              abort() {}
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $codex
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length !== 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Codex reset compact rows should not be accepted without visible DOM:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun codexCollectorDoesNotCopyWeeklyResetIntoIdleSessionWindow() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Codex runtime checks", node != null)

        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-codex-idle-session-reset-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const pageText = [
              "Codex",
              "Codex 5 hour usage limit",
              "100% left",
              "Codex Weekly usage limit",
              "90% left",
              "Resets in 6d 2h",
              "GPT-5.3-Codex-Spark 5 hour usage limit",
              "100% left",
              "GPT-5.3-Codex-Spark Weekly usage limit",
              "100% left",
              "Resets in 6d 2h"
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/codex/cloud/settings/analytics" };
            global.document = {
              title: "Codex",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.AbortController = class {
              constructor() { this.signal = {}; }
              abort() {}
            };
            global.fetch = async function(url) {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $codex
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const limits = posted[0] && posted[0].usage && posted[0].usage.rate_limits;
              if (!limits || !limits.primary_window || !limits.secondary_window || !limits.spark_primary_window || !limits.spark_secondary_window) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
              if (Object.prototype.hasOwnProperty.call(limits.primary_window, "reset_text")) {
                console.error(JSON.stringify(limits.primary_window));
                process.exit(1);
              }
              if (limits.secondary_window.reset_text !== "Resets in 6d 2h") {
                console.error(JSON.stringify(limits.secondary_window));
                process.exit(1);
              }
              if (Object.prototype.hasOwnProperty.call(limits.spark_primary_window, "reset_text")) {
                console.error(JSON.stringify(limits.spark_primary_window));
                process.exit(1);
              }
              if (limits.spark_secondary_window.reset_text !== "Resets in 6d 2h") {
                console.error(JSON.stringify(limits.spark_secondary_window));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Codex idle session window should not inherit weekly reset text:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun codexCollectorPrefersVisibleUsageLimitDomOverInternalUsedPercent() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Codex runtime checks", node != null)

        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-codex-visible-usage-limit-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const kr = (...codes) => String.fromCharCode(...codes);
            const hour = kr(0xC2DC, 0xAC04);
            const weekly = kr(0xC8FC, 0xAC04);
            const usage = kr(0xC0AC, 0xC6A9);
            const limit = kr(0xD55C, 0xB3C4);
            const remaining = kr(0xB0A8, 0xC74C);
            const reset = kr(0xCD08, 0xAE30, 0xD654);
            const pageText = [
              "잔고",
              "Codex 사용량은 공유 에이전틱 사용 한도에서 차감됩니다",
              "5" + hour + " " + usage + " " + limit,
              "99% " + remaining,
              "오전 10:14 " + reset,
              weekly + " " + usage + " " + limit,
              "100% " + remaining,
              "오전 10:14 " + reset,
              "2026. 6. 11. 오전 09:59 " + reset,
              "GPT-5.3-Codex-Spark 5" + hour + " " + usage + " " + limit,
              "100% " + remaining,
              "GPT-5.3-Codex-Spark " + weekly + " " + usage + " " + limit,
              "100% " + remaining,
              "남은 크레딧",
              "0"
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/codex/cloud/settings/analytics" };
            global.document = {
              title: "Codex",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            global.__APP_DATA__ = {
              usage: {
                rate_limits: {
                  primary_window: { used_percent: 100, reset_after_seconds: 18000 },
                  secondary_window: { used_percent: 100 }
                }
              }
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.AbortController = class {
              constructor() { this.signal = {}; }
              abort() {}
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            Date.now = function() { return new Date(2026, 5, 7, 6, 59, 0, 0).getTime(); };
            $codex
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const payload = posted[0];
              const limits = payload && payload.usage && payload.usage.rate_limits;
              if (payload.source !== "visible-dom" || !limits) {
                console.error(JSON.stringify(payload));
                process.exit(1);
              }
              if (limits.primary_window.remaining_percent !== 99 || limits.primary_window.used_percent !== 1) {
                console.error(JSON.stringify(limits.primary_window));
                process.exit(1);
              }
              if (limits.primary_window.internal_used_percent !== undefined || limits.primary_window.internal_used_percent_mismatch !== undefined) {
                console.error(JSON.stringify(limits.primary_window));
                process.exit(1);
              }
              if (limits.primary_window.reset_text !== "Resets in 3h 15m") {
                console.error(JSON.stringify(limits.primary_window));
                process.exit(1);
              }
              if (limits.secondary_window.remaining_percent !== 100 || limits.secondary_window.used_percent !== 0) {
                console.error(JSON.stringify(limits.secondary_window));
                process.exit(1);
              }
              if (limits.secondary_window.reset_text !== "Resets in 4d 3h") {
                console.error(JSON.stringify(limits.secondary_window));
                process.exit(1);
              }
              if (limits.spark_primary_window.label !== "GPT-5.3-Codex-Spark 5h" || limits.spark_primary_window.remaining_percent !== 100) {
                console.error(JSON.stringify(limits.spark_primary_window));
                process.exit(1);
              }
              if (limits.spark_primary_window.reset_text !== "Resets in 3h 15m") {
                console.error(JSON.stringify(limits.spark_primary_window));
                process.exit(1);
              }
              if (limits.spark_secondary_window.label !== "GPT-5.3-Codex-Spark Weekly" || limits.spark_secondary_window.remaining_percent !== 100) {
                console.error(JSON.stringify(limits.spark_secondary_window));
                process.exit(1);
              }
              if (limits.spark_secondary_window.reset_text !== "Resets in 4d 3h") {
                console.error(JSON.stringify(limits.spark_secondary_window));
                process.exit(1);
              }
              if (!payload.usage.credits || payload.usage.credits.remaining !== 0) {
                console.error(JSON.stringify(payload.usage));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Codex visible usage-limit DOM did not override internal used_percent:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun codexCollectorNormalizesWeeklyTimeOnlyResetWhenWithinNextDay() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Codex runtime checks", node != null)

        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-codex-weekly-time-only-reset-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            const kr = (...codes) => String.fromCharCode(...codes);
            const hour = kr(0xC2DC, 0xAC04);
            const weekly = kr(0xC8FC, 0xAC04);
            const usage = kr(0xC0AC, 0xC6A9);
            const limit = kr(0xD55C, 0xB3C4);
            const remaining = kr(0xB0A8, 0xC74C);
            const reset = kr(0xCD08, 0xAE30, 0xD654);
            const pageText = [
              "Codex",
              "5" + hour + " " + usage + " " + limit,
              "84% " + remaining,
              "오후 2:52 " + reset,
              weekly + " " + usage + " " + limit,
              "39% " + remaining,
              "오후 2:52 " + reset,
              "GPT-5.3-Codex-Spark " + weekly + " " + usage + " " + limit,
              "100% " + remaining,
              "오후 2:52 " + reset,
              "남은 크레딧",
              "0"
            ].join("\n");
            global.window = global;
            global.location = { pathname: "/codex/cloud/settings/analytics" };
            global.document = {
              title: "Codex",
              documentElement: { innerText: pageText },
              scripts: [],
              querySelector: () => ({})
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.AbortController = class {
              constructor() { this.signal = {}; }
              abort() {}
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            Date.now = function() { return new Date(2026, 5, 16, 10, 0, 0, 0).getTime(); };
            $codex
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const limits = posted[0] && posted[0].usage && posted[0].usage.rate_limits;
              if (!limits || !limits.secondary_window || !limits.spark_secondary_window) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
              if (limits.secondary_window.reset_text !== "Resets in 4h 52m") {
                console.error(JSON.stringify(limits.secondary_window));
                process.exit(1);
              }
              if (limits.spark_secondary_window.reset_text !== "Resets in 4h 52m") {
                console.error(JSON.stringify(limits.spark_secondary_window));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Codex weekly time-only reset should be normalized:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun antigravityCollectorAcceptsRemainingFractionAliasAtResetBoundary() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Antigravity runtime checks", node != null)

        val antigravity = ProviderWebCollectorScripts.build(ProviderId.ANTIGRAVITY, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-antigravity-remaining-fraction-runtime", ".js")
        val runtime = """
            const posted = [];
            const errors = [];
            const timers = [];
            global.window = global;
            global.location = { pathname: "/" };
            global.document = {
              title: "Antigravity",
              documentElement: { innerText: "Antigravity" },
              scripts: [],
              querySelector: () => ({})
            };
            global.__APP_DATA__ = {
              usage: {
                x: [
                  { l: "Antigravity Session", remaining_fraction: 1, t: "Resets in 5h" }
                ]
              }
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: (value) => errors.push(JSON.parse(value))
            };
            global.fetch = async function() {
              return {
                ok: true,
                status: 200,
                clone() { return { text: async () => "" }; },
                text: async () => ""
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn, delay) {
              if (delay <= 1000) timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $antigravity
            (async function() {
              for (let i = 0; i < 12 && posted.length === 0 && errors.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted, errors }));
                process.exit(1);
              }
              const rows = posted[0] && posted[0].usage && posted[0].usage.x;
              if (!Array.isArray(rows) || rows.length !== 1) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
              if (rows[0].remaining_fraction !== 1) {
                console.error(JSON.stringify(rows));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Antigravity remaining fraction aliases were not accepted:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun cursorCollectorAcceptsRemainingFractionAliasAtResetBoundary() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected Cursor runtime checks", node != null)

        val cursor = ProviderWebCollectorScripts.build(ProviderId.CURSOR, emptyMap(), "")
        val path = Files.createTempFile("ai-quota-cursor-remaining-fraction-runtime", ".js")
        val runtime = """
            const posted = [];
            const timers = [];
            global.window = global;
            global.location = { pathname: "/dashboard", href: "https://cursor.com/dashboard" };
            global.document = {
              title: "Cursor",
              documentElement: { innerText: "Cursor dashboard" },
              scripts: [],
              querySelector: () => null,
              getElementById: () => null
            };
            global.__NEXT_DATA__ = {
              props: {
                pageProps: {
                  dashboard: {
                    membershipType: "Pro",
                    planUsage: { remaining_fraction: 1 }
                  }
                }
              }
            };
            class StorageMock {
              constructor(values) { this.values = values || {}; this.keys = Object.keys(this.values); this.length = this.keys.length; }
              key(index) { return this.keys[index] || null; }
              getItem(key) { return this.values[key] || ""; }
            }
            global.localStorage = new StorageMock({});
            global.sessionStorage = new StorageMock({});
            global.AIQuotaCollectorBridge = {
              postUsagePayload: (value) => posted.push(JSON.parse(value)),
              postCollectorError: () => {},
              fetchCursorJson: () => JSON.stringify({ ok: false, json: {} })
            };
            global.fetch = async function() {
              return {
                ok: false,
                status: 404,
                clone() { return { text: async () => "{}" }; },
                text: async () => "{}",
                json: async () => ({})
              };
            };
            global.XMLHttpRequest = function() {};
            global.XMLHttpRequest.prototype = {
              open() {},
              send() {},
              addEventListener() {}
            };
            global.setTimeout = function(fn) {
              timers.push(fn);
              return timers.length;
            };
            global.clearTimeout = function() {};
            $cursor
            (async function() {
              for (let i = 0; i < 14 && posted.length === 0; i += 1) {
                while (timers.length > 0) timers.shift()();
                await Promise.resolve();
                await new Promise((resolve) => setImmediate(resolve));
              }
              if (posted.length === 0) {
                console.error(JSON.stringify({ posted }));
                process.exit(1);
              }
              if (!posted[0].planUsage || posted[0].planUsage.remaining_fraction !== 1) {
                console.error(JSON.stringify(posted[0]));
                process.exit(1);
              }
            })();
        """.trimIndent()
        try {
            Files.write(path, runtime.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node!!, path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("Cursor remaining fraction alias was not accepted:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun collectorScriptsAreValidJavaScriptForRuntimeInjection() {
        val node = nodeCommandOrNull()
        assumeTrue("node is required for injected JavaScript syntax checks", node != null)

        val scripts = mapOf(
            "claude" to ProviderWebCollectorScripts.build(ProviderId.CLAUDE, mapOf("lastActiveOrg" to "org_123"), ""),
            "codex" to ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), ""),
            "glm" to ProviderWebCollectorScripts.build(ProviderId.GLM, emptyMap(), ""),
            "opencode" to ProviderWebCollectorScripts.build(ProviderId.OPENCODE, emptyMap(), ""),
            "gemini" to ProviderWebCollectorScripts.build(
                ProviderId.GEMINI,
                emptyMap(),
                "window.SAGE_USAGE_EXTRACTOR={buildGeminiUsagePayload:function(){return null;}};"
            ),
            "copilot" to ProviderWebCollectorScripts.build(ProviderId.COPILOT, emptyMap(), ""),
            "antigravity" to ProviderWebCollectorScripts.build(ProviderId.ANTIGRAVITY, emptyMap(), ""),
            "cursor" to ProviderWebCollectorScripts.build(ProviderId.CURSOR, emptyMap(), "")
        )

        scripts.forEach { (provider, script) ->
            assertValidJavaScript(node!!, provider, script)
        }
    }

    private fun nodeCommandOrNull(): String? {
        return runCatching {
            val process = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
            if (process.waitFor() == 0) "node" else null
        }.getOrNull()
    }

    private fun assertValidJavaScript(node: String, provider: String, script: String) {
        val path = Files.createTempFile("ai-quota-$provider-collector", ".js")
        try {
            Files.write(path, script.toByteArray(StandardCharsets.UTF_8))
            val process = ProcessBuilder(node, "--check", path.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            assertTrue("$provider collector JS is invalid:\n$output", process.waitFor() == 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
