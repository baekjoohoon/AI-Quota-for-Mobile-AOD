import test from "node:test";
import assert from "node:assert/strict";

import {
  buildGeminiCliAuthorizationUrl,
  completeGeminiCliOAuthCallback,
  createGeminiCliOAuthStart,
  refreshGeminiCliAccessToken
} from "../src/geminiCliGateway.js";

test("buildGeminiCliAuthorizationUrl requests offline consent and PKCE", () => {
  const result = buildGeminiCliAuthorizationUrl({
    clientId: "client.apps.googleusercontent.com",
    redirectUri: "http://127.0.0.1:46417/oauth2callback",
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

test("createGeminiCliOAuthStart stores verifier server-side", async () => {
  let storedPath = null;
  let storedRecord = null;
  const db = {
    collection(name) {
      assert.equal(name, "geminiCliOAuthStates");
      return {
        doc(stateHash) {
          storedPath = stateHash;
          return {
            async set(record) {
              storedRecord = record;
            }
          };
        }
      };
    }
  };

  const result = await createGeminiCliOAuthStart({
    uid: "uid-123",
    db,
    oauthClientId: "client.apps.googleusercontent.com",
    redirectUri: "http://127.0.0.1:46417/oauth2callback",
    now: new Date("2026-05-30T00:00:00.000Z"),
    randomBytes: (count) => Buffer.alloc(count, 7)
  });
  const url = new URL(result.authorizationUrl);

  assert.equal(storedPath, url.searchParams.get("state") && storedRecord.stateHash);
  assert.equal(storedRecord.uid, "uid-123");
  assert.equal(storedRecord.providerId, "gemini");
  assert.equal(storedRecord.consumedAt, null);
  assert.equal(storedRecord.codeVerifier.length > 20, true);
  assert.equal(url.searchParams.get("code_challenge_method"), "S256");
  assert.equal(url.searchParams.get("client_id"), "client.apps.googleusercontent.com");
});

test("completeGeminiCliOAuthCallback exchanges code with client secret without returning it", async () => {
  const db = fakeStateDb({
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

  const result = await completeGeminiCliOAuthCallback({
    uid: "uid-123",
    callbackUrl: "http://127.0.0.1:46417/oauth2callback?code=code-123&state=state-123",
    db,
    oauthClientId: "client.apps.googleusercontent.com",
    oauthClientSecret: "client-secret",
    redirectUri: "http://127.0.0.1:46417/oauth2callback",
    now: new Date("2026-05-30T00:01:00.000Z"),
    fetchImpl
  });

  assert.equal(tokenRequestBody.get("client_id"), "client.apps.googleusercontent.com");
  assert.equal(tokenRequestBody.get("client_secret"), "client-secret");
  assert.equal(tokenRequestBody.get("code_verifier"), "verifier-secret");
  assert.equal(tokenRequestBody.get("redirect_uri"), "http://127.0.0.1:46417/oauth2callback");
  assert.equal(result.ok, true);
  assert.equal(result.accessToken, "access-secret");
  assert.equal(result.refreshToken, "refresh-secret");
  assert.equal(JSON.stringify(result).includes("client-secret"), false);
  assert.equal(db.updated.consumedAt, "2026-05-30T00:01:00.000Z");
});

test("refreshGeminiCliAccessToken refreshes through Firebase secret without returning it", async () => {
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

  const result = await refreshGeminiCliAccessToken({
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

function fakeStateDb(stateRecord) {
  const db = {
    updated: null,
    collection(name) {
      assert.equal(name, "geminiCliOAuthStates");
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
