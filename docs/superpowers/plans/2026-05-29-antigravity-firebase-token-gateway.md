# Antigravity Firebase Token Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Firebase Functions gateway for Antigravity OAuth, encrypted refresh-token custody, backend usage collection, and Android snapshot integration without exposing client secrets or plaintext refresh tokens in the APK or Firestore.

**2026-05-29 decision update:** First validation phase uses AES-256-GCM with `ANTIGRAVITY_TOKEN_MASTER_KEY` in Secret Manager instead of Cloud KMS. Cloud KMS remains the later hardening path after real Antigravity OAuth and usage collection prove viable.

**Architecture:** Android authenticates with Firebase, requests an Antigravity OAuth URL, opens the browser, then asks Firebase Functions to collect usage. Functions stores only encrypted refresh-token ciphertext and calls the Antigravity API adapter server-side; Android only receives trusted normalized provider payloads or typed errors.

**Tech Stack:** Android Kotlin, Firebase Auth, Firebase Functions client, Firebase App Check, Firebase Functions v2 Node.js, Firestore, Secret Manager, AES-256-GCM, Google OAuth token endpoint, existing `ProviderUsageNormalizer`, JUnit, Node test runner, Gradle.

---

## File Structure

- Modify: `functions/package.json`
  - Add test/build dependencies only if needed by the existing Node test setup.
- Modify: `functions/src/core.js`
  - Add pure helpers for state hashing, PKCE generation, AAD construction, safe error mapping, and provider snapshot safety.
- Create: `functions/src/antigravityGateway.js`
  - OAuth URL creation, callback token exchange, KMS encryption/decryption wrappers, Antigravity API adapter, and callable handlers.
- Modify: `functions/src/index.js`
  - Export `startAntigravityOAuth`, `antigravityOAuthCallback`, `collectAntigravityUsage`, and `disconnectAntigravity`.
- Modify: `functions/test/core.test.js`
  - Unit tests for helpers and forbidden plaintext checks.
- Create: `functions/test/antigravityGateway.test.js`
  - Unit tests with mocked token endpoint, mocked KMS, mocked Firestore, and mocked Antigravity API responses.
- Modify: `android/build.gradle.kts`
  - Keep Google services plugin available.
- Modify: `android/app/build.gradle.kts`
  - Apply Google services plugin and add Firebase Auth, Functions, and App Check dependencies.
- Create: `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt`
  - Android gateway wrapper for Functions calls and typed result parsing.
- Create: `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseConnector.kt`
  - Provider connector for connect, refresh, and disconnect.
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderConnectorRegistry.kt`
  - Register Firebase connector for Antigravity.
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderAuthStrategy.kt`
  - Add backend auth/collection kinds if needed.
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt`
  - Mark Antigravity as backend-gateway backed instead of WebView collector after connector support exists.
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt`
  - Route Antigravity automatic refresh to native/backend connector mode instead of hidden WebView.
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`
  - Call the Antigravity connector in native/backend mode and map typed failures.
- Modify: `android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt`
  - Use connector connect flow for Antigravity instead of `WebLoginActivity`.
- Modify: `android/app/src/test/java/com/aiquota/mobile/providers/*`
  - Add focused tests for Antigravity gateway routing, no Android client secret, status mapping, and refresh eligibility.
- Update: `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`
  - Add entries after each task.
- Update: `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`
  - Add issue entries for reproduced failures.

---

### Task 1: Lock Backend Gateway Security Helpers With Tests

**Files:**
- Modify: `functions/src/core.js`
- Modify: `functions/test/core.test.js`
- Update: `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`

- [ ] **Step 1: Add failing tests for state hash, AAD, and forbidden token fields**

Add to `functions/test/core.test.js`:

```javascript
import {
  antigravityTokenAad,
  hashOAuthState,
  assertNoPlaintextProviderSecret
} from "../src/core.js";

test("hashOAuthState returns stable sha256 base64url without exposing raw state", () => {
  const rawState = "state-secret-value";
  const hash = hashOAuthState(rawState);

  expect(hash).not.toContain(rawState);
  expect(hash).toMatch(/^[A-Za-z0-9_-]{43}$/);
  expect(hashOAuthState(rawState)).toBe(hash);
});

test("antigravityTokenAad binds token encryption to uid provider client and version", () => {
  expect(
    antigravityTokenAad({
      uid: "uid-123",
      oauthClientId: "client.apps.googleusercontent.com"
    })
  ).toBe("uid:uid-123:provider:antigravity:oauthClient:client.apps.googleusercontent.com:aad:v1");
});

test("assertNoPlaintextProviderSecret rejects plaintext token fields", () => {
  expect(() =>
    assertNoPlaintextProviderSecret({
      encryptedRefreshToken: "ciphertext",
      refreshToken: "plain"
    })
  ).toThrow("PROVIDER_SECRET_FORBIDDEN_FIELD:refreshToken");
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
npm --prefix functions test -- --runInBand
```

Expected: FAIL because the helper exports do not exist.

- [ ] **Step 3: Implement minimal helpers**

Add to `functions/src/core.js`:

```javascript
export function hashOAuthState(state) {
  if (!state || typeof state !== "string") {
    throw new Error("OAUTH_STATE_REQUIRED");
  }
  return crypto.createHash("sha256").update(state).digest("base64url");
}

export function antigravityTokenAad({ uid, oauthClientId }) {
  if (!uid || !oauthClientId) {
    throw new Error("TOKEN_AAD_INPUT_REQUIRED");
  }
  return `uid:${uid}:provider:antigravity:oauthClient:${oauthClientId}:aad:v1`;
}

export function assertNoPlaintextProviderSecret(record) {
  const forbidden = ["accessToken", "refreshToken", "token", "clientSecret", "cookie", "authorization"];
  for (const key of forbidden) {
    if (Object.prototype.hasOwnProperty.call(record ?? {}, key)) {
      throw new Error(`PROVIDER_SECRET_FORBIDDEN_FIELD:${key}`);
    }
  }
  return true;
}
```

- [ ] **Step 4: Run tests and verify they pass**

Run:

```powershell
npm --prefix functions test -- --runInBand
```

Expected: PASS.

- [ ] **Step 5: Update progress log**

Append a progress entry with command, PASS/FAIL, changed files, and next task.

---

### Task 2: Add Firebase Function Gateway Module With Mockable Boundaries

**Files:**
- Create: `functions/src/antigravityGateway.js`
- Create: `functions/test/antigravityGateway.test.js`
- Modify: `functions/src/index.js`
- Update: `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`

- [ ] **Step 1: Write failing OAuth URL test**

Create `functions/test/antigravityGateway.test.js`:

```javascript
import { buildAntigravityAuthorizationUrl } from "../src/antigravityGateway.js";

test("buildAntigravityAuthorizationUrl requests offline consent and PKCE", () => {
  const result = buildAntigravityAuthorizationUrl({
    clientId: "client.apps.googleusercontent.com",
    redirectUri: "https://example.cloudfunctions.net/antigravityOAuthCallback",
    state: "state-123",
    codeChallenge: "challenge-123"
  });
  const url = new URL(result);

  expect(url.origin + url.pathname).toBe("https://accounts.google.com/o/oauth2/v2/auth");
  expect(url.searchParams.get("access_type")).toBe("offline");
  expect(url.searchParams.get("prompt")).toBe("consent");
  expect(url.searchParams.get("response_type")).toBe("code");
  expect(url.searchParams.get("code_challenge_method")).toBe("S256");
  expect(url.searchParams.get("code_challenge")).toBe("challenge-123");
  expect(url.searchParams.get("state")).toBe("state-123");
  expect(url.searchParams.get("scope")).toContain("https://www.googleapis.com/auth/cloud-platform");
});
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```powershell
npm --prefix functions test -- --runInBand
```

Expected: FAIL because `antigravityGateway.js` does not exist.

- [ ] **Step 3: Implement OAuth URL builder**

Create `functions/src/antigravityGateway.js`:

```javascript
const GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";

export const ANTIGRAVITY_SCOPES = [
  "https://www.googleapis.com/auth/cloud-platform",
  "https://www.googleapis.com/auth/userinfo.email",
  "https://www.googleapis.com/auth/userinfo.profile"
];

export function buildAntigravityAuthorizationUrl({ clientId, redirectUri, state, codeChallenge }) {
  for (const [name, value] of Object.entries({ clientId, redirectUri, state, codeChallenge })) {
    if (!value) throw new Error(`ANTIGRAVITY_OAUTH_${name.toUpperCase()}_REQUIRED`);
  }
  const url = new URL(GOOGLE_AUTH_URL);
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("redirect_uri", redirectUri);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", ANTIGRAVITY_SCOPES.join(" "));
  url.searchParams.set("access_type", "offline");
  url.searchParams.set("prompt", "consent");
  url.searchParams.set("state", state);
  url.searchParams.set("code_challenge", codeChallenge);
  url.searchParams.set("code_challenge_method", "S256");
  return url.toString();
}
```

- [ ] **Step 4: Export placeholder handlers from `index.js`**

Add exports in `functions/src/index.js` after existing exports:

```javascript
export {
  startAntigravityOAuth,
  antigravityOAuthCallback,
  collectAntigravityUsage,
  disconnectAntigravity
} from "./antigravityGateway.js";
```

In `antigravityGateway.js`, add temporary handler exports that return typed unavailable responses until later tasks implement them:

```javascript
export const startAntigravityOAuth = null;
export const antigravityOAuthCallback = null;
export const collectAntigravityUsage = null;
export const disconnectAntigravity = null;
```

If Firebase requires real function objects at import time, skip the export from `index.js` until Task 4 and note that in the progress log.

- [ ] **Step 5: Run tests**

Run:

```powershell
npm --prefix functions test -- --runInBand
```

Expected: PASS for helper tests. If `index.js` export of null handlers breaks module loading, remove those exports and log the issue.

---

### Task 3: Implement KMS Encryption Wrapper And Token Record Safety

**Files:**
- Modify: `functions/src/antigravityGateway.js`
- Modify: `functions/test/antigravityGateway.test.js`
- Update: `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`

- [ ] **Step 1: Add failing KMS AAD mismatch test with fake KMS**

Add:

```javascript
import { encryptRefreshToken, decryptRefreshToken } from "../src/antigravityGateway.js";

test("refresh token decrypt requires exact AAD", async () => {
  const fakeKms = {
    async encrypt({ plaintext, additionalAuthenticatedData }) {
      return {
        ciphertext: Buffer.from(`${additionalAuthenticatedData.toString("utf8")}:${plaintext.toString("utf8")}`)
      };
    },
    async decrypt({ ciphertext, additionalAuthenticatedData }) {
      const text = ciphertext.toString("utf8");
      const prefix = `${additionalAuthenticatedData.toString("utf8")}:`;
      if (!text.startsWith(prefix)) throw new Error("AAD_MISMATCH");
      return { plaintext: Buffer.from(text.slice(prefix.length)) };
    }
  };

  const encrypted = await encryptRefreshToken({
    kms: fakeKms,
    keyName: "projects/p/locations/global/keyRings/r/cryptoKeys/k",
    refreshToken: "refresh-secret",
    aad: "uid:u:provider:antigravity:oauthClient:c:aad:v1"
  });

  await expect(
    decryptRefreshToken({
      kms: fakeKms,
      keyName: "projects/p/locations/global/keyRings/r/cryptoKeys/k",
      encryptedRefreshToken: encrypted,
      aad: "wrong-aad"
    })
  ).rejects.toThrow("AAD_MISMATCH");
});
```

- [ ] **Step 2: Implement wrappers**

Add:

```javascript
export async function encryptRefreshToken({ kms, keyName, refreshToken, aad }) {
  if (!kms || !keyName || !refreshToken || !aad) throw new Error("KMS_ENCRYPT_INPUT_REQUIRED");
  const [result] = Array.isArray(await kms.encrypt({
    name: keyName,
    plaintext: Buffer.from(refreshToken, "utf8"),
    additionalAuthenticatedData: Buffer.from(aad, "utf8")
  }))
    ? await kms.encrypt({
        name: keyName,
        plaintext: Buffer.from(refreshToken, "utf8"),
        additionalAuthenticatedData: Buffer.from(aad, "utf8")
      })
    : [await kms.encrypt({
        name: keyName,
        plaintext: Buffer.from(refreshToken, "utf8"),
        additionalAuthenticatedData: Buffer.from(aad, "utf8")
      })];
  return Buffer.from(result.ciphertext).toString("base64");
}

export async function decryptRefreshToken({ kms, keyName, encryptedRefreshToken, aad }) {
  if (!kms || !keyName || !encryptedRefreshToken || !aad) throw new Error("KMS_DECRYPT_INPUT_REQUIRED");
  const [result] = Array.isArray(await kms.decrypt({
    name: keyName,
    ciphertext: Buffer.from(encryptedRefreshToken, "base64"),
    additionalAuthenticatedData: Buffer.from(aad, "utf8")
  }))
    ? await kms.decrypt({
        name: keyName,
        ciphertext: Buffer.from(encryptedRefreshToken, "base64"),
        additionalAuthenticatedData: Buffer.from(aad, "utf8")
      })
    : [await kms.decrypt({
        name: keyName,
        ciphertext: Buffer.from(encryptedRefreshToken, "base64"),
        additionalAuthenticatedData: Buffer.from(aad, "utf8")
      })];
  return Buffer.from(result.plaintext).toString("utf8");
}
```

If the double call pattern is undesirable after checking the actual KMS client return shape, replace it with a small `awaitKmsResult()` helper and add a test for both array and object shapes.

- [ ] **Step 3: Run tests and update troubleshooting if KMS shape differs**

Run:

```powershell
npm --prefix functions test -- --runInBand
```

Expected: PASS after wrapper shape is corrected.

---

### Task 4: Implement OAuth Start And Callback Functions

**Files:**
- Modify: `functions/src/antigravityGateway.js`
- Modify: `functions/src/index.js`
- Modify: `functions/test/antigravityGateway.test.js`
- Update: `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`

- [ ] **Step 1: Test callback rejects missing refresh token**

Add a pure helper test for `buildEncryptedTokenRecord`:

```javascript
import { buildEncryptedTokenRecord } from "../src/antigravityGateway.js";

test("buildEncryptedTokenRecord rejects missing refresh token", async () => {
  await expect(
    buildEncryptedTokenRecord({
      tokenResponse: { access_token: "access" },
      uid: "uid-123",
      oauthClientId: "client.apps.googleusercontent.com",
      kms: { encrypt: async () => ({ ciphertext: Buffer.from("x") }) },
      kmsKeyName: "key"
    })
  ).rejects.toThrow("REFRESH_TOKEN_MISSING");
});
```

- [ ] **Step 2: Implement token record builder**

Add:

```javascript
import { antigravityTokenAad, assertNoPlaintextProviderSecret } from "./core.js";

export async function buildEncryptedTokenRecord({ tokenResponse, uid, oauthClientId, kms, kmsKeyName, now = new Date() }) {
  const refreshToken = tokenResponse?.refresh_token;
  if (!refreshToken) throw new Error("REFRESH_TOKEN_MISSING");
  const aad = antigravityTokenAad({ uid, oauthClientId });
  const encryptedRefreshToken = await encryptRefreshToken({
    kms,
    keyName: kmsKeyName,
    refreshToken,
    aad
  });
  const record = {
    providerId: "antigravity",
    oauthClientId,
    scopes: ANTIGRAVITY_SCOPES,
    encryptedRefreshToken,
    kmsKeyName,
    aadVersion: "v1",
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
    lastRefreshAt: null,
    lastCollectAt: null,
    lastStatus: "TOKEN_STORED",
    lastErrorKind: null
  };
  assertNoPlaintextProviderSecret(record);
  return record;
}
```

- [ ] **Step 3: Implement callable and callback handlers**

Use Firebase Functions v2:

```javascript
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onRequest } from "firebase-functions/v2/https";

export const startAntigravityOAuth = onCall({ enforceAppCheck: true }, async (request) => {
  if (!request.auth?.uid) throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
  // Generate state and PKCE, store oauth state, return authorization URL.
});

export const antigravityOAuthCallback = onRequest(async (request, response) => {
  // Validate state, exchange code, KMS-encrypt refresh token, store token doc, mark state consumed.
});
```

Implement the body with dependency injection friendly helpers so unit tests can cover logic without a live Google token endpoint.

- [ ] **Step 4: Run tests**

Run:

```powershell
npm --prefix functions test -- --runInBand
```

Expected: PASS.

---

### Task 5: Implement Collect And Disconnect Functions

**Files:**
- Modify: `functions/src/antigravityGateway.js`
- Modify: `functions/test/antigravityGateway.test.js`
- Update: `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`

- [ ] **Step 1: Add failing 403 mapping test**

Add:

```javascript
import { mapAntigravityApiFailure } from "../src/antigravityGateway.js";

test("mapAntigravityApiFailure maps private api forbidden without auth retry", () => {
  expect(mapAntigravityApiFailure({ status: 403, body: '{"error":{"status":"PERMISSION_DENIED"}}' })).toEqual({
    ok: false,
    provider: "antigravity",
    errorKind: "BACKEND_API_FORBIDDEN",
    requiresAuth: false,
    retryable: false
  });
});
```

- [ ] **Step 2: Implement failure mapper and trusted payload mapper**

Add:

```javascript
export function mapAntigravityApiFailure({ status }) {
  if (status === 401) {
    return { ok: false, provider: "antigravity", errorKind: "GOOGLE_REFRESH_FAILED", requiresAuth: true, retryable: false };
  }
  if (status === 403) {
    return { ok: false, provider: "antigravity", errorKind: "BACKEND_API_FORBIDDEN", requiresAuth: false, retryable: false };
  }
  if (status >= 500 || status === 408 || status === 429) {
    return { ok: false, provider: "antigravity", errorKind: "BACKEND_TEMPORARY_FAILURE", requiresAuth: false, retryable: true };
  }
  return { ok: false, provider: "antigravity", errorKind: "NO_TRUSTED_PAYLOAD", requiresAuth: false, retryable: true };
}
```

- [ ] **Step 3: Implement `collectAntigravityUsage`**

Use the stored encrypted token doc:

1. Read `users/{uid}/providerSecrets/antigravity`.
2. If missing, return `TOKEN_MISSING` with `requiresAuth=true`.
3. Decrypt refresh token.
4. Refresh access token through Google token endpoint.
5. Call `loadCodeAssist` and `fetchAvailableModels`.
6. Return trusted payload only when model quota count is positive.

- [ ] **Step 4: Implement `disconnectAntigravity`**

Behavior:

1. Read encrypted token doc.
2. Best-effort revoke token with Google revoke endpoint.
3. Delete `users/{uid}/providerSecrets/antigravity`.
4. Return `{ ok: true }` with optional redacted `revokeWarning`.

- [ ] **Step 5: Run tests and update troubleshooting**

Run:

```powershell
npm --prefix functions test -- --runInBand
```

Expected: PASS. If 403 mapping is changed, update Issue 1 in troubleshooting.

---

### Task 6: Add Android Firebase Gateway Client

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt`
- Create: `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt`
- Update: `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`

- [ ] **Step 1: Add source test proving Android has no client secret**

Create `AntigravityFirebaseGatewayTest.kt`:

```kotlin
package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntigravityFirebaseGatewayTest {
    @Test
    fun androidGatewayDoesNotContainOauthClientSecret() {
        val gateway = File("src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt").readText()
        val build = File("build.gradle.kts").readText()

        assertFalse(gateway.contains("client_secret"))
        assertFalse(gateway.contains("ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET"))
        assertFalse(build.contains("ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET"))
        assertTrue(gateway.contains("startAntigravityOAuth"))
        assertTrue(gateway.contains("collectAntigravityUsage"))
        assertTrue(gateway.contains("disconnectAntigravity"))
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```powershell
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest
```

Expected: FAIL because gateway file does not exist.

- [ ] **Step 3: Add Firebase dependencies**

Modify `android/app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
}
```

Use the newest compatible Firebase BoM only if the project already resolves it locally. Keep the version stable in the progress log.

- [ ] **Step 4: Create gateway wrapper**

Create `AntigravityFirebaseGateway.kt`:

```kotlin
package com.aiquota.mobile.providers

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class AntigravityFirebaseGateway(context: Context) {
    private val functions = FirebaseFunctions.getInstance()

    suspend fun startOAuth(): String {
        val result = functions
            .getHttpsCallable("startAntigravityOAuth")
            .call(mapOf("returnToApp" to true))
            .await()
        val data = result.data as? Map<*, *> ?: error("antigravity_oauth_response_invalid")
        return data["authorizationUrl"]?.toString()?.takeIf { it.isNotBlank() }
            ?: error("antigravity_oauth_url_missing")
    }

    suspend fun collectUsage(): GatewayResult {
        val result = functions
            .getHttpsCallable("collectAntigravityUsage")
            .call(mapOf("force" to false))
            .await()
        return GatewayResult.from(result.data)
    }

    suspend fun disconnect() {
        functions.getHttpsCallable("disconnectAntigravity").call(emptyMap<String, Any>()).await()
    }
}

data class GatewayResult(
    val ok: Boolean,
    val payload: String?,
    val errorKind: String?,
    val requiresAuth: Boolean,
    val retryable: Boolean
) {
    companion object {
        fun from(value: Any?): GatewayResult {
            val data = value as? Map<*, *> ?: return GatewayResult(false, null, "gateway_response_invalid", false, true)
            val payload = data["payload"]?.let { JSONObject(it as Map<*, *>).toString() }
            return GatewayResult(
                ok = data["ok"] == true,
                payload = payload,
                errorKind = data["errorKind"]?.toString(),
                requiresAuth = data["requiresAuth"] == true,
                retryable = data["retryable"] != false
            )
        }
    }
}
```

- [ ] **Step 5: Run focused test**

Run:

```powershell
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest
```

Expected: PASS.

---

### Task 7: Route Antigravity Connect And Refresh Through Gateway

**Files:**
- Create: `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseConnector.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderConnectorRegistry.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt`
- Add/modify tests under `android/app/src/test/java/com/aiquota/mobile/providers`

- [ ] **Step 1: Add routing tests**

Add tests that assert:

```kotlin
assertTrue(ProviderConnectorRegistry.defaultSource().contains("AntigravityFirebaseConnector"))
assertFalse(appShellConnectProviderMethod.contains("providerId != ProviderId.ANTIGRAVITY"))
assertTrue(refreshPlanSource.contains("ProviderId.ANTIGRAVITY -> ProviderRefreshMode.NATIVE_API"))
```

If a `defaultSource()` helper is not desirable, use source-level tests like existing provider routing tests.

- [ ] **Step 2: Implement connector**

Create:

```kotlin
class AntigravityFirebaseConnector(
    private val context: Context
) : ProviderConnector {
    override val providerId: ProviderId = ProviderId.ANTIGRAVITY
    override val startUrl: String = ""

    suspend fun beginAuthorizationUrl(): String {
        return AntigravityFirebaseGateway(context).startOAuth()
    }

    override suspend fun refreshUsage(rawPayload: String): ProviderUsageSnapshot {
        val result = AntigravityFirebaseGateway(context).collectUsage()
        if (result.ok && !result.payload.isNullOrBlank()) {
            return ProviderUsageNormalizer.normalize(
                ProviderId.ANTIGRAVITY,
                result.payload,
                ProviderPayloadSource.PROVIDER_API
            ) ?: ProviderUsageSnapshot.connectedWithoutUsage(
                ProviderId.ANTIGRAVITY,
                "Antigravity gateway returned no trusted usage payload."
            )
        }
        return when {
            result.requiresAuth -> ProviderUsageSnapshot.disconnected(ProviderId.ANTIGRAVITY).copy(message = result.errorKind)
            result.errorKind == "BACKEND_API_FORBIDDEN" -> ProviderUsageSnapshot.unavailable(
                ProviderId.ANTIGRAVITY,
                "Antigravity private API is not available for this backend project."
            )
            else -> ProviderUsageSnapshot.failedKeepingPrevious(
                ProviderId.ANTIGRAVITY,
                previous = null,
                message = result.errorKind ?: "Antigravity gateway collection failed."
            )
        }
    }

    override suspend fun disconnect() {
        AntigravityFirebaseGateway(context).disconnect()
    }
}
```

Adjust `previous = null` after wiring the service/repository so failures keep the current snapshot instead of losing lines.

- [ ] **Step 3: Route connect flow**

In `AIQuotaAppShell.connectProvider`, branch Antigravity to:

1. mark connecting,
2. call `beginAuthorizationUrl`,
3. open returned URL,
4. keep provider pending until collect succeeds.

Do not start `WebLoginActivity` for Antigravity gateway flow.

- [ ] **Step 4: Route automatic refresh**

In `ProviderBackgroundRefreshService.collectNativeProviderUsage`, call the connector for Antigravity and return payload/failure based on `ProviderUsageSnapshot`.

- [ ] **Step 5: Run targeted Android tests**

Run:

```powershell
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.ProviderRefreshPlanTest --tests com.aiquota.mobile.providers.GoogleProviderWebViewFirstCollectionTest
```

Expected: PASS after tests are updated to the new Antigravity backend-gateway strategy.

---

### Task 8: Emulator And Firebase Runtime Verification

**Files:**
- Update: `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`
- Update: `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`

- [ ] **Step 1: Run all unit tests**

Run:

```powershell
npm --prefix functions test -- --runInBand
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:testDebugUnitTest
```

Expected: both pass.

- [ ] **Step 2: Run Firebase emulator with mocked provider adapter**

Run:

```powershell
firebase emulators:start --only functions,firestore,auth
```

Expected: Functions load without missing Secret Manager/KMS config when emulator mock mode is enabled.

- [ ] **Step 3: Install Android debug APK**

Run:

```powershell
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:installDebug
```

Expected: install succeeds.

- [ ] **Step 4: Verify redacted logs**

Run:

```powershell
adb logcat -c
adb logcat -d -s AIQuotaAntigravity AIQuotaFirebaseGateway AIQuotaBgRefreshService
```

Expected: logs include provider, function name, status/error kind, and no token/code/cookie/raw body.

- [ ] **Step 5: Update docs**

Add final entries to progress and troubleshooting. Include:

- exact commands,
- PASS/FAIL,
- whether backend returned trusted payload or `BACKEND_API_FORBIDDEN`,
- APK install result,
- foreground refresh state if automatic refresh was exercised.

---

## Self-Review Checklist

- Every token path stores ciphertext only.
- Android source contains no OAuth client secret.
- Private API 403 is not treated as user auth failure.
- `CONNECTED` requires trusted normalized usage lines.
- Disconnect clears backend token before local snapshot removal.
- Progress and troubleshooting documents are updated before ending work.
