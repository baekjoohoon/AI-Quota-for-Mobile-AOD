import crypto from "node:crypto";

import { hashOAuthState } from "./core.js";

const GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const TEN_MINUTES_MS = 10 * 60 * 1000;
const DEFAULT_REDIRECT_AFTER_AUTH = "aiquota://provider/gemini-oauth-complete";
const LOOPBACK_HOSTS = new Set(["127.0.0.1", "localhost"]);

export const GEMINI_CLI_SCOPES = [
  "https://www.googleapis.com/auth/cloud-platform",
  "https://www.googleapis.com/auth/userinfo.email",
  "https://www.googleapis.com/auth/userinfo.profile"
];

export function buildGeminiCliAuthorizationUrl({
  clientId,
  redirectUri,
  state,
  codeChallenge
}) {
  for (const [name, value] of Object.entries({ clientId, redirectUri, state, codeChallenge })) {
    if (!value) {
      throw new Error(`GEMINI_CLI_OAUTH_${name.toUpperCase()}_REQUIRED`);
    }
  }

  const url = new URL(GOOGLE_AUTH_URL);
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("redirect_uri", redirectUri);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", GEMINI_CLI_SCOPES.join(" "));
  url.searchParams.set("access_type", "offline");
  url.searchParams.set("prompt", "consent");
  url.searchParams.set("state", state);
  url.searchParams.set("code_challenge", codeChallenge);
  url.searchParams.set("code_challenge_method", "S256");
  return url.toString();
}

export async function createGeminiCliOAuthStart({
  uid,
  db,
  oauthClientId,
  redirectUri,
  now = new Date(),
  randomBytes = crypto.randomBytes
}) {
  if (!uid) {
    throw new Error("AUTH_REQUIRED");
  }
  if (!db || !oauthClientId || !redirectUri) {
    throw new Error("GEMINI_CLI_OAUTH_START_CONFIG_REQUIRED");
  }

  const state = randomBytes(32).toString("base64url");
  const codeVerifier = randomBytes(64).toString("base64url");
  const codeChallenge = crypto.createHash("sha256").update(codeVerifier).digest("base64url");
  const stateHash = hashOAuthState(state);
  const timestamp = now.toISOString();
  const expiresAt = new Date(now.getTime() + TEN_MINUTES_MS).toISOString();

  await db.collection("geminiCliOAuthStates").doc(stateHash).set({
    uid,
    providerId: "gemini",
    stateHash,
    codeVerifier,
    redirectAfterAuth: DEFAULT_REDIRECT_AFTER_AUTH,
    createdAt: timestamp,
    expiresAt,
    consumedAt: null
  });

  return {
    authorizationUrl: buildGeminiCliAuthorizationUrl({
      clientId: oauthClientId,
      redirectUri,
      state,
      codeChallenge
    }),
    expiresAt
  };
}

export async function completeGeminiCliOAuthCallback({
  uid,
  callbackUrl,
  db,
  oauthClientId,
  oauthClientSecret,
  redirectUri,
  now = new Date(),
  fetchImpl = fetch
}) {
  if (!uid) {
    throw new Error("AUTH_REQUIRED");
  }
  if (!db || !oauthClientId || !oauthClientSecret || !redirectUri) {
    throw new Error("GEMINI_CLI_OAUTH_COMPLETE_CONFIG_REQUIRED");
  }
  const callback = parseLoopbackCallback(callbackUrl);
  const code = callback.searchParams.get("code");
  const state = callback.searchParams.get("state");
  const error = callback.searchParams.get("error");
  if (error) {
    throw new Error("GEMINI_CLI_OAUTH_CALLBACK_DENIED");
  }
  if (!code || !state) {
    throw new Error("GEMINI_CLI_OAUTH_CALLBACK_QUERY_REQUIRED");
  }

  const stateHash = hashOAuthState(state);
  const stateRef = db.collection("geminiCliOAuthStates").doc(stateHash);
  const stateSnap = await stateRef.get();
  const stateRecord = stateSnap.exists ? stateSnap.data() : null;
  assertUsableOAuthState({ stateRecord, uid, now });

  const tokenResponse = await exchangeAuthorizationCode({
    fetchImpl,
    code,
    codeVerifier: stateRecord.codeVerifier,
    oauthClientId,
    oauthClientSecret,
    redirectUri
  });

  const consumedAt = now.toISOString();
  await stateRef.update({ consumedAt });

  return tokenResult(tokenResponse);
}

export async function refreshGeminiCliAccessToken({
  uid,
  refreshToken,
  oauthClientId,
  oauthClientSecret,
  fetchImpl = fetch
}) {
  if (!uid) {
    throw new Error("AUTH_REQUIRED");
  }
  if (!refreshToken) {
    throw new Error("GEMINI_CLI_REFRESH_TOKEN_REQUIRED");
  }
  if (!oauthClientId || !oauthClientSecret) {
    throw new Error("GEMINI_CLI_OAUTH_REFRESH_CONFIG_REQUIRED");
  }

  const tokenResponse = await refreshGoogleAccessToken({
    fetchImpl,
    refreshToken,
    oauthClientId,
    oauthClientSecret
  });
  return tokenResult({
    ...tokenResponse,
    refresh_token: tokenResponse.refresh_token ?? refreshToken
  });
}

export function createGeminiCliGatewayHandlers({
  onCall,
  HttpsError,
  db,
  config,
  fetchImpl = fetch,
  secrets = [],
  enforceAppCheck = true
}) {
  return {
    startGeminiCliOAuth: onCall(callOptionsFor("startGeminiCliOAuth"), async (request) => {
      if (!request.auth?.uid) {
        throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
      }
      return createGeminiCliOAuthStart({
        uid: request.auth.uid,
        db,
        oauthClientId: readConfigValue(config.oauthClientId),
        redirectUri: readConfigValue(config.redirectUri)
      });
    }),
    completeGeminiCliOAuth: onCall(callOptionsFor("completeGeminiCliOAuth"), async (request) => {
      if (!request.auth?.uid) {
        throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
      }
      try {
        return await completeGeminiCliOAuthCallback({
          uid: request.auth.uid,
          callbackUrl: String(request.data?.callbackUrl ?? ""),
          db,
          oauthClientId: readConfigValue(config.oauthClientId),
          oauthClientSecret: readConfigValue(config.oauthClientSecret),
          redirectUri: readConfigValue(config.redirectUri),
          fetchImpl
        });
      } catch (error) {
        throw callableError(error, HttpsError);
      }
    }),
    refreshGeminiCliAccessToken: onCall(callOptionsFor("refreshGeminiCliAccessToken"), async (request) => {
      if (!request.auth?.uid) {
        throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
      }
      try {
        return await refreshGeminiCliAccessToken({
          uid: request.auth.uid,
          refreshToken: String(request.data?.refreshToken ?? ""),
          oauthClientId: readConfigValue(config.oauthClientId),
          oauthClientSecret: readConfigValue(config.oauthClientSecret),
          fetchImpl
        });
      } catch (error) {
        throw callableError(error, HttpsError);
      }
    })
  };

  function callOptionsFor(functionName) {
    return { enforceAppCheck, secrets: secretsFor(functionName) };
  }

  function secretsFor(functionName) {
    return Array.isArray(secrets) ? secrets : secrets[functionName] ?? [];
  }
}

async function exchangeAuthorizationCode({
  fetchImpl,
  code,
  codeVerifier,
  oauthClientId,
  oauthClientSecret,
  redirectUri
}) {
  const body = new URLSearchParams({
    client_id: oauthClientId,
    client_secret: oauthClientSecret,
    code,
    code_verifier: codeVerifier,
    grant_type: "authorization_code",
    redirect_uri: redirectUri
  });
  const response = await fetchImpl(GOOGLE_TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body
  });
  const tokenResponse = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(`GOOGLE_TOKEN_EXCHANGE_FAILED:${response.status}`);
  }
  if (!tokenResponse?.access_token) {
    throw new Error("GOOGLE_TOKEN_RESPONSE_ACCESS_TOKEN_MISSING");
  }
  return tokenResponse;
}

async function refreshGoogleAccessToken({
  fetchImpl,
  refreshToken,
  oauthClientId,
  oauthClientSecret
}) {
  const body = new URLSearchParams({
    client_id: oauthClientId,
    client_secret: oauthClientSecret,
    refresh_token: refreshToken,
    grant_type: "refresh_token"
  });
  const response = await fetchImpl(GOOGLE_TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body
  });
  const tokenResponse = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(`GOOGLE_TOKEN_REFRESH_FAILED:${response.status}`);
  }
  if (!tokenResponse?.access_token) {
    throw new Error("GOOGLE_TOKEN_RESPONSE_ACCESS_TOKEN_MISSING");
  }
  return tokenResponse;
}

function tokenResult(tokenResponse) {
  const expiresIn = Number(tokenResponse.expires_in);
  return {
    ok: true,
    accessToken: tokenResponse.access_token,
    refreshToken: tokenResponse.refresh_token ?? null,
    expiresIn: Number.isFinite(expiresIn) ? expiresIn : null,
    idToken: tokenResponse.id_token ?? null,
    scope: tokenResponse.scope ?? null,
    tokenType: tokenResponse.token_type ?? null
  };
}

function parseLoopbackCallback(callbackUrl) {
  let url;
  try {
    url = new URL(callbackUrl);
  } catch {
    throw new Error("GEMINI_CLI_OAUTH_CALLBACK_INVALID");
  }
  if (!LOOPBACK_HOSTS.has(url.hostname) || url.pathname !== "/oauth2callback") {
    throw new Error("GEMINI_CLI_OAUTH_CALLBACK_INVALID");
  }
  return url;
}

function assertUsableOAuthState({ stateRecord, uid, now }) {
  if (!stateRecord) {
    throw new Error("OAUTH_STATE_NOT_FOUND");
  }
  if (stateRecord.consumedAt) {
    throw new Error("OAUTH_STATE_CONSUMED");
  }
  if (stateRecord.uid !== uid || !stateRecord.codeVerifier) {
    throw new Error("OAUTH_STATE_INVALID");
  }
  if (new Date(stateRecord.expiresAt).getTime() <= now.getTime()) {
    throw new Error("OAUTH_STATE_EXPIRED");
  }
}

function callableError(error, HttpsError) {
  const code = error instanceof Error ? error.message : "GEMINI_CLI_OAUTH_FAILED";
  if (code === "AUTH_REQUIRED") {
    return new HttpsError("unauthenticated", code);
  }
  if (
    code === "OAUTH_STATE_NOT_FOUND" ||
    code === "OAUTH_STATE_CONSUMED" ||
    code === "OAUTH_STATE_INVALID" ||
    code === "OAUTH_STATE_EXPIRED" ||
    code === "GEMINI_CLI_OAUTH_CALLBACK_INVALID" ||
    code === "GEMINI_CLI_OAUTH_CALLBACK_QUERY_REQUIRED"
  ) {
    return new HttpsError("invalid-argument", code);
  }
  if (code.startsWith("GOOGLE_TOKEN_EXCHANGE_FAILED")) {
    return new HttpsError("failed-precondition", code);
  }
  if (code.startsWith("GOOGLE_TOKEN_REFRESH_FAILED")) {
    return new HttpsError("failed-precondition", code);
  }
  return new HttpsError("internal", code);
}

function readConfigValue(value) {
  return typeof value === "function" ? value() : value;
}
