import test from "node:test";
import assert from "node:assert/strict";

import {
  buildAntigravityAuthorizationUrl,
  buildEncryptedTokenRecord,
  collectAntigravityUsageForUid,
  completeAntigravityOAuthCallback,
  completeAntigravityOAuthCallbackForUid,
  decryptRefreshToken,
  disconnectAntigravityForUid,
  encryptRefreshToken,
  mapAntigravityApiFailure,
  refreshAntigravityAccessToken
} from "../src/antigravityGateway.js";

test("buildAntigravityAuthorizationUrl requests offline consent and PKCE", () => {
  const result = buildAntigravityAuthorizationUrl({
    clientId: "client.apps.googleusercontent.com",
    redirectUri: "https://example.cloudfunctions.net/antigravityOAuthCallback",
    state: "state-123",
    codeChallenge: "challenge-123"
  });
  const url = new URL(result);

  assert.equal(url.origin + url.pathname, "https://accounts.google.com/o/oauth2/v2/auth");
  assert.equal(url.searchParams.get("access_type"), "offline");
  assert.equal(url.searchParams.get("prompt"), "consent");
  assert.equal(url.searchParams.get("response_type"), "code");
  assert.equal(url.searchParams.get("code_challenge_method"), "S256");
  assert.equal(url.searchParams.get("code_challenge"), "challenge-123");
  assert.equal(url.searchParams.get("state"), "state-123");
  assert.match(
    url.searchParams.get("scope"),
    /https:\/\/www\.googleapis\.com\/auth\/cloud-platform/
  );
});

test("refresh token decrypt requires exact AAD", async () => {
  const tokenMasterKey = Buffer.alloc(32, 7).toString("base64");

  const encrypted = await encryptRefreshToken({
    tokenMasterKey,
    refreshToken: "refresh-secret",
    aad: "uid:u:provider:antigravity:oauthClient:c:aad:v1",
    randomBytes: () => Buffer.alloc(12, 1)
  });

  assert.equal(encrypted.includes("refresh-secret"), false);
  await assert.rejects(
    () =>
      decryptRefreshToken({
        tokenMasterKey,
        encryptedRefreshToken: encrypted,
        aad: "wrong-aad"
      }),
    /AES_GCM_DECRYPT_FAILED/
  );
});

test("buildEncryptedTokenRecord rejects missing refresh token", async () => {
  await assert.rejects(
    () =>
      buildEncryptedTokenRecord({
        tokenResponse: { access_token: "access" },
        uid: "uid-123",
        oauthClientId: "client.apps.googleusercontent.com",
        tokenMasterKey: Buffer.alloc(32, 7).toString("base64")
      }),
    /REFRESH_TOKEN_MISSING/
  );
});

test("mapAntigravityApiFailure maps private api forbidden without auth retry", () => {
  assert.deepEqual(
    mapAntigravityApiFailure({
      status: 403,
      body: '{"error":{"status":"PERMISSION_DENIED"}}'
    }),
    {
      ok: false,
      provider: "antigravity",
      errorKind: "BACKEND_API_FORBIDDEN",
      requiresAuth: false,
      retryable: false
    }
  );
});

test("collectAntigravityUsageForUid trusts quotaInfo remaining fraction payloads", async () => {
  const aad = "uid:uid-123:provider:antigravity:oauthClient:client.apps.googleusercontent.com:aad:v1";
  const tokenMasterKey = Buffer.alloc(32, 7).toString("base64");
  const encryptedRefreshToken = await encryptRefreshToken({
    tokenMasterKey,
    refreshToken: "refresh-secret",
    aad,
    randomBytes: () => Buffer.alloc(12, 1)
  });
  const db = {
    doc(path) {
      assert.equal(path, "users/uid-123/providerSecrets/antigravity");
      return {
        async get() {
          return {
            exists: true,
            data() {
              return {
                oauthClientId: "client.apps.googleusercontent.com",
                encryptedRefreshToken
              };
            }
          };
        },
        async update() {}
      };
    }
  };
  const fetchImpl = async (url, options = {}) => {
    if (url === "https://oauth2.googleapis.com/token") {
      return jsonResponse(200, { access_token: "access-secret" });
    }
    if (url.endsWith("/v1internal:loadCodeAssist")) {
      const body = JSON.parse(options.body);
      assert.equal(url, "https://daily-cloudcode-pa.googleapis.com/v1internal:loadCodeAssist");
      assert.equal(body.metadata.ideName, "antigravity");
      assert.equal(body.metadata.ideType, "ANTIGRAVITY");
      assert.equal(body.metadata.pluginType, "GEMINI");
      assert.equal(body.mode, "FULL_ELIGIBILITY_CHECK");
      assert.equal(options.headers["user-agent"], "antigravity/2.0.0 android/unknown");
      return jsonResponse(200, { cloudaicompanionProject: "project-123" });
    }
    if (url.endsWith("/v1internal:fetchAvailableModels")) {
      assert.deepEqual(JSON.parse(options.body), { project: "project-123" });
      return jsonResponse(200, {
        models: {
          "gemini-3-pro": {
            displayName: "Gemini 3 Pro",
            quotaInfo: { remainingFraction: 0.42 }
          }
        }
      });
    }
    throw new Error(`UNEXPECTED_URL:${url}`);
  };

  const result = await collectAntigravityUsageForUid({
    uid: "uid-123",
    db,
    tokenMasterKey,
    oauthClientSecret: "client-secret",
    fetchImpl,
    projectResolutionDelayMs: 0,
    sleep: async () => {}
  });

  assert.equal(result.ok, true);
  assert.deepEqual(result.payload.models, [
    {
      modelId: "gemini-3-pro",
      label: "Gemini 3 Pro",
      remainingFraction: 0.42,
      isExhausted: false,
      resetTime: null,
      modelProvider: null
    }
  ]);
});

test("collectAntigravityUsageForUid onboards default tier when project is missing", async () => {
  const aad = "uid:uid-123:provider:antigravity:oauthClient:client.apps.googleusercontent.com:aad:v1";
  const tokenMasterKey = Buffer.alloc(32, 7).toString("base64");
  const encryptedRefreshToken = await encryptRefreshToken({
    tokenMasterKey,
    refreshToken: "refresh-secret",
    aad,
    randomBytes: () => Buffer.alloc(12, 1)
  });
  const db = {
    doc(path) {
      assert.equal(path, "users/uid-123/providerSecrets/antigravity");
      return {
        async get() {
          return {
            exists: true,
            data() {
              return {
                oauthClientId: "client.apps.googleusercontent.com",
                encryptedRefreshToken
              };
            }
          };
        },
        async update() {}
      };
    }
  };
  let loadCount = 0;
  const fetchImpl = async (url, options = {}) => {
    if (url === "https://oauth2.googleapis.com/token") {
      return jsonResponse(200, { access_token: "access-secret" });
    }
    if (url.endsWith("/v1internal:loadCodeAssist")) {
      loadCount += 1;
      return jsonResponse(200, loadCount === 1 ? {
        allowedTiers: [{ id: "free-tier", isDefault: true }]
      } : {
        cloudaicompanionProject: { id: "project-after-onboard" }
      });
    }
    if (url.endsWith("/v1internal:onboardUser")) {
      assert.deepEqual(JSON.parse(options.body), {
        tierId: "free-tier",
        metadata: {
          ideName: "antigravity",
          ideType: "ANTIGRAVITY",
          ideVersion: "2.0.0",
          pluginVersion: "2.0.0",
          platform: "PLATFORM_UNSPECIFIED",
          updateChannel: "stable",
          pluginType: "GEMINI"
        }
      });
      return jsonResponse(200, { ok: true });
    }
    if (url.endsWith("/v1internal:fetchAvailableModels")) {
      assert.deepEqual(JSON.parse(options.body), { project: "project-after-onboard" });
      return jsonResponse(200, {
        models: {
          "claude-opus": {
            label: "Claude Opus",
            quotaInfo: {
              remainingFraction: 0.5,
              resetTime: "2026-06-01T00:00:00Z"
            }
          },
          "chat_internal": {
            label: "Internal Chat",
            quotaInfo: { remainingFraction: 0.1 }
          }
        }
      });
    }
    throw new Error(`UNEXPECTED_URL:${url}`);
  };

  const result = await collectAntigravityUsageForUid({
    uid: "uid-123",
    db,
    tokenMasterKey,
    oauthClientSecret: "client-secret",
    fetchImpl,
    projectResolutionDelayMs: 0,
    sleep: async () => {}
  });

  assert.equal(result.ok, true);
  assert.equal(loadCount, 2);
  assert.deepEqual(result.payload.models, [
    {
      modelId: "claude-opus",
      label: "Claude Opus",
      remainingFraction: 0.5,
      isExhausted: false,
      resetTime: "2026-06-01T00:00:00Z",
      modelProvider: null
    }
  ]);
});

test("collectAntigravityUsageForUid polls loadCodeAssist after onboarding until project appears", async () => {
  const aad = "uid:uid-123:provider:antigravity:oauthClient:client.apps.googleusercontent.com:aad:v1";
  const tokenMasterKey = Buffer.alloc(32, 7).toString("base64");
  const encryptedRefreshToken = await encryptRefreshToken({
    tokenMasterKey,
    refreshToken: "refresh-secret",
    aad,
    randomBytes: () => Buffer.alloc(12, 1)
  });
  const db = {
    doc(path) {
      assert.equal(path, "users/uid-123/providerSecrets/antigravity");
      return {
        async get() {
          return {
            exists: true,
            data() {
              return {
                oauthClientId: "client.apps.googleusercontent.com",
                encryptedRefreshToken
              };
            }
          };
        },
        async update() {}
      };
    }
  };
  let loadCount = 0;
  let delayCount = 0;
  const fetchImpl = async (url) => {
    if (url === "https://oauth2.googleapis.com/token") {
      return jsonResponse(200, { access_token: "access-secret" });
    }
    if (url.endsWith("/v1internal:loadCodeAssist")) {
      loadCount += 1;
      return jsonResponse(200, loadCount < 4 ? {
        allowedTiers: [{ id: "free-tier", isDefault: true }]
      } : {
        cloudaicompanionProject: { id: "project-after-poll" }
      });
    }
    if (url.endsWith("/v1internal:onboardUser")) {
      return jsonResponse(200, { ok: true });
    }
    if (url.endsWith("/v1internal:fetchAvailableModels")) {
      return jsonResponse(200, {
        models: {
          "claude-opus": {
            label: "Claude Opus",
            quotaInfo: { remainingFraction: 0.5 }
          }
        }
      });
    }
    throw new Error(`UNEXPECTED_URL:${url}`);
  };

  const result = await collectAntigravityUsageForUid({
    uid: "uid-123",
    db,
    tokenMasterKey,
    oauthClientSecret: "client-secret",
    fetchImpl,
    projectResolutionDelayMs: 10,
    sleep: async (delayMs) => {
      assert.equal(delayMs, 10);
      delayCount += 1;
    }
  });

  assert.equal(result.ok, true);
  assert.equal(loadCount, 4);
  assert.equal(delayCount, 3);
});

test("collectAntigravityUsageForUid stores resolved Antigravity project id", async () => {
  const aad = "uid:uid-123:provider:antigravity:oauthClient:client.apps.googleusercontent.com:aad:v1";
  const tokenMasterKey = Buffer.alloc(32, 7).toString("base64");
  const encryptedRefreshToken = await encryptRefreshToken({
    tokenMasterKey,
    refreshToken: "refresh-secret",
    aad,
    randomBytes: () => Buffer.alloc(12, 1)
  });
  let updatePayload = null;
  const db = {
    doc(path) {
      assert.equal(path, "users/uid-123/providerSecrets/antigravity");
      return {
        async get() {
          return {
            exists: true,
            data() {
              return {
                oauthClientId: "client.apps.googleusercontent.com",
                encryptedRefreshToken
              };
            }
          };
        },
        async update(payload) {
          updatePayload = payload;
        }
      };
    }
  };
  const fetchImpl = async (url) => {
    if (url === "https://oauth2.googleapis.com/token") {
      return jsonResponse(200, { access_token: "access-secret" });
    }
    if (url.endsWith("/v1internal:loadCodeAssist")) {
      return jsonResponse(200, { cloudaicompanionProject: { id: "project-123" } });
    }
    if (url.endsWith("/v1internal:fetchAvailableModels")) {
      return jsonResponse(200, {
        models: {
          "claude-opus": {
            label: "Claude Opus",
            quotaInfo: { remainingFraction: 0.5 }
          }
        }
      });
    }
    throw new Error(`UNEXPECTED_URL:${url}`);
  };

  const result = await collectAntigravityUsageForUid({
    uid: "uid-123",
    db,
    tokenMasterKey,
    oauthClientSecret: "client-secret",
    fetchImpl,
    now: new Date("2026-05-30T00:00:00.000Z")
  });

  assert.equal(result.ok, true);
  assert.equal(updatePayload.antigravityProjectId, "project-123");
  assert.equal(updatePayload.lastStatus, "CONNECTED");
});

test("collectAntigravityUsageForUid returns TOKEN_MISSING when backend token doc is absent", async () => {
  const db = {
    doc(path) {
      assert.equal(path, "users/uid-123/providerSecrets/antigravity");
      return {
        async get() {
          return { exists: false };
        }
      };
    }
  };

  assert.deepEqual(
    await collectAntigravityUsageForUid({
      uid: "uid-123",
      db,
      tokenMasterKey: Buffer.alloc(32, 7).toString("base64"),
      oauthClientSecret: "unused",
      fetchImpl: async () => {
        throw new Error("SHOULD_NOT_FETCH");
      }
    }),
    {
      ok: false,
      provider: "antigravity",
      errorKind: "TOKEN_MISSING",
      requiresAuth: true,
      retryable: false
    }
  );
});

test("collectAntigravityUsageForUid maps private api forbidden without leaking tokens", async () => {
  const aad = "uid:uid-123:provider:antigravity:oauthClient:client.apps.googleusercontent.com:aad:v1";
  const tokenMasterKey = Buffer.alloc(32, 7).toString("base64");
  const encryptedRefreshToken = await encryptRefreshToken({
    tokenMasterKey,
    refreshToken: "refresh-secret",
    aad,
    randomBytes: () => Buffer.alloc(12, 1)
  });
  const db = {
    doc(path) {
      assert.equal(path, "users/uid-123/providerSecrets/antigravity");
      return {
        async get() {
          return {
            exists: true,
            data() {
              return {
                oauthClientId: "client.apps.googleusercontent.com",
                encryptedRefreshToken
              };
            }
          };
        },
        async update() {}
      };
    }
  };
  const fetchImpl = async (url) => {
    if (url === "https://oauth2.googleapis.com/token") {
      return jsonResponse(200, { access_token: "access-secret" });
    }
    if (url.endsWith("/v1internal:loadCodeAssist")) {
      return jsonResponse(200, { cloudaicompanionProject: "project-123" });
    }
    if (url.endsWith("/v1internal:fetchAvailableModels")) {
      return jsonResponse(403, { error: { status: "PERMISSION_DENIED" } });
    }
    throw new Error(`UNEXPECTED_URL:${url}`);
  };

  const result = await collectAntigravityUsageForUid({
    uid: "uid-123",
    db,
    tokenMasterKey,
    oauthClientSecret: "client-secret",
    fetchImpl
  });

  assert.deepEqual(result, {
    ok: false,
    provider: "antigravity",
    errorKind: "BACKEND_API_FORBIDDEN",
    requiresAuth: false,
    retryable: false
  });
  assert.equal(JSON.stringify(result).includes("refresh-secret"), false);
  assert.equal(JSON.stringify(result).includes("access-secret"), false);
  assert.equal(JSON.stringify(result).includes("client-secret"), false);
});

test("completeAntigravityOAuthCallback treats consumed state with stored token as success", async () => {
  const db = {
    collection(name) {
      assert.equal(name, "antigravityOAuthStates");
      return {
        doc() {
          return {
            async get() {
              return {
                exists: true,
                data() {
                  return {
                    uid: "uid-123",
                    codeVerifier: "verifier",
                    expiresAt: "2026-04-29T01:10:00.000Z",
                    consumedAt: "2026-04-29T01:01:00.000Z",
                    redirectAfterAuth: "aiquota://provider/antigravity"
                  };
                }
              };
            }
          };
        }
      };
    },
    doc(path) {
      assert.equal(path, "users/uid-123/providerSecrets/antigravity");
      return {
        async get() {
          return { exists: true };
        }
      };
    }
  };

  const result = await completeAntigravityOAuthCallback({
    query: { code: "already-used-code", state: "state-123" },
    db,
    tokenMasterKey: Buffer.alloc(32, 7).toString("base64"),
    oauthClientId: "client.apps.googleusercontent.com",
    oauthClientSecret: "client-secret",
    redirectUri: "https://example.cloudfunctions.net/antigravityOAuthCallback",
    fetchImpl: async () => {
      throw new Error("SHOULD_NOT_EXCHANGE_CODE");
    }
  });

  assert.deepEqual(result, {
    ok: true,
    redirectAfterAuth: "aiquota://provider/antigravity"
  });
});

test("completeAntigravityOAuthCallbackForUid exchanges loopback code with client secret", async () => {
  const db = fakeStateCollectionDb("antigravityOAuthStates", {
    uid: "uid-123",
    codeVerifier: "verifier-secret",
    expiresAt: "2026-05-30T00:10:00.000Z",
    consumedAt: null
  });
  let tokenRequestBody = null;
  const fetchImpl = async (url, options = {}) => {
    assert.equal(url, "https://oauth2.googleapis.com/token");
    tokenRequestBody = new URLSearchParams(options.body.toString());
    return jsonResponse(200, {
      access_token: "access-secret",
      refresh_token: "refresh-secret",
      expires_in: 3599,
      token_type: "Bearer"
    });
  };

  const result = await completeAntigravityOAuthCallbackForUid({
    uid: "uid-123",
    callbackUrl: "http://127.0.0.1:8080/callback?code=code-123&state=state-123",
    db,
    oauthClientId: "client.apps.googleusercontent.com",
    oauthClientSecret: "client-secret",
    redirectUri: "http://127.0.0.1:8080/callback",
    now: new Date("2026-05-30T00:01:00.000Z"),
    fetchImpl
  });

  assert.equal(tokenRequestBody.get("client_id"), "client.apps.googleusercontent.com");
  assert.equal(tokenRequestBody.get("client_secret"), "client-secret");
  assert.equal(tokenRequestBody.get("code_verifier"), "verifier-secret");
  assert.equal(tokenRequestBody.get("redirect_uri"), "http://127.0.0.1:8080/callback");
  assert.equal(result.ok, true);
  assert.equal(result.accessToken, "access-secret");
  assert.equal(result.refreshToken, "refresh-secret");
  assert.equal(JSON.stringify(result).includes("client-secret"), false);
  assert.equal(db.updated.consumedAt, "2026-05-30T00:01:00.000Z");
});

test("refreshAntigravityAccessToken refreshes through Firebase secret without returning it", async () => {
  let tokenRequestBody = null;
  const fetchImpl = async (url, options = {}) => {
    assert.equal(url, "https://oauth2.googleapis.com/token");
    tokenRequestBody = new URLSearchParams(options.body.toString());
    return jsonResponse(200, {
      access_token: "new-access-secret",
      expires_in: 3600,
      token_type: "Bearer"
    });
  };

  const result = await refreshAntigravityAccessToken({
    uid: "uid-123",
    refreshToken: "refresh-secret",
    oauthClientId: "client.apps.googleusercontent.com",
    oauthClientSecret: "client-secret",
    fetchImpl
  });

  assert.equal(tokenRequestBody.get("client_id"), "client.apps.googleusercontent.com");
  assert.equal(tokenRequestBody.get("client_secret"), "client-secret");
  assert.equal(tokenRequestBody.get("refresh_token"), "refresh-secret");
  assert.equal(tokenRequestBody.get("grant_type"), "refresh_token");
  assert.equal(result.ok, true);
  assert.equal(result.accessToken, "new-access-secret");
  assert.equal(result.refreshToken, "refresh-secret");
  assert.equal(JSON.stringify(result).includes("client-secret"), false);
});

test("disconnectAntigravityForUid deletes token doc even when revoke fails", async () => {
  const aad = "uid:uid-123:provider:antigravity:oauthClient:client.apps.googleusercontent.com:aad:v1";
  const tokenMasterKey = Buffer.alloc(32, 7).toString("base64");
  const encryptedRefreshToken = await encryptRefreshToken({
    tokenMasterKey,
    refreshToken: "refresh-secret",
    aad,
    randomBytes: () => Buffer.alloc(12, 1)
  });
  let deleted = false;
  const db = {
    doc(path) {
      assert.equal(path, "users/uid-123/providerSecrets/antigravity");
      return {
        async get() {
          return {
            exists: true,
            data() {
              return {
                oauthClientId: "client.apps.googleusercontent.com",
                encryptedRefreshToken
              };
            }
          };
        },
        async delete() {
          deleted = true;
        }
      };
    }
  };
  const fetchImpl = async (url) => {
    assert.equal(url, "https://oauth2.googleapis.com/revoke");
    return jsonResponse(500, { error: "temporary" });
  };

  const result = await disconnectAntigravityForUid({
    uid: "uid-123",
    db,
    tokenMasterKey,
    fetchImpl
  });

  assert.equal(deleted, true);
  assert.deepEqual(result, {
    ok: true,
    provider: "antigravity",
    revokeWarning: "GOOGLE_REVOKE_FAILED:500"
  });
  assert.equal(JSON.stringify(result).includes("refresh-secret"), false);
});

function fakeStateCollectionDb(collectionName, stateRecord) {
  const db = {
    updated: null,
    collection(name) {
      assert.equal(name, collectionName);
      return {
        doc() {
          return {
            async get() {
              return {
                exists: true,
                data() {
                  return stateRecord;
                }
              };
            },
            async update(payload) {
              db.updated = payload;
            }
          };
        }
      };
    }
  };
  return db;
}

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return body;
    }
  };
}
