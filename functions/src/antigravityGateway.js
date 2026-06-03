import crypto from "node:crypto";

import { antigravityTokenAad, assertNoPlaintextProviderSecret, hashOAuthState } from "./core.js";

const GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke";
const ANTIGRAVITY_API_BASE = "https://daily-cloudcode-pa.googleapis.com";
const ANTIGRAVITY_USER_AGENT = "antigravity/2.0.0 android/unknown";
const ANTIGRAVITY_CLIENT_VERSION = "2.0.0";
const TEN_MINUTES_MS = 10 * 60 * 1000;
const ANTIGRAVITY_PROJECT_RESOLUTION_ATTEMPTS = 5;
const ANTIGRAVITY_PROJECT_RESOLUTION_DELAY_MS = 2_000;
const DEFAULT_REDIRECT_AFTER_AUTH = "aiquota://provider/antigravity";
const AES_GCM_TOKEN_VERSION = "v1";
const AES_GCM_SECRET_PROVIDER = "aes-gcm-secret";
const ANTIGRAVITY_METADATA = {
  ideName: "antigravity",
  ideType: "ANTIGRAVITY",
  ideVersion: ANTIGRAVITY_CLIENT_VERSION,
  pluginVersion: ANTIGRAVITY_CLIENT_VERSION,
  platform: "PLATFORM_UNSPECIFIED",
  updateChannel: "stable",
  pluginType: "GEMINI"
};
const ANTIGRAVITY_CODE_ASSIST_BODY = {
  metadata: ANTIGRAVITY_METADATA,
  mode: "FULL_ELIGIBILITY_CHECK"
};
const ANTIGRAVITY_IGNORED_MODEL_ID_PREFIXES = ["chat_", "tab_", "rev"];
const ANTIGRAVITY_IGNORED_MODEL_ID_PARTS = ["image", "mquery"];
const ANTIGRAVITY_IGNORED_MODEL_LABEL_PARTS = [
  "GPT-OSS 120B",
  "Gemini 2.5",
  "Flash Lite",
  "Flash Image"
];

export const ANTIGRAVITY_SCOPES = [
  "https://www.googleapis.com/auth/cloud-platform",
  "https://www.googleapis.com/auth/userinfo.email",
  "https://www.googleapis.com/auth/userinfo.profile"
];

export function buildAntigravityAuthorizationUrl({
  clientId,
  redirectUri,
  state,
  codeChallenge
}) {
  for (const [name, value] of Object.entries({ clientId, redirectUri, state, codeChallenge })) {
    if (!value) {
      throw new Error(`ANTIGRAVITY_OAUTH_${name.toUpperCase()}_REQUIRED`);
    }
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

export async function encryptRefreshToken({
  tokenMasterKey,
  refreshToken,
  aad,
  randomBytes = crypto.randomBytes
}) {
  if (!tokenMasterKey || !refreshToken || !aad) {
    throw new Error("AES_GCM_ENCRYPT_INPUT_REQUIRED");
  }

  const key = decodeAes256MasterKey(tokenMasterKey);
  const iv = randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  cipher.setAAD(Buffer.from(aad, "utf8"));
  const ciphertext = Buffer.concat([
    cipher.update(Buffer.from(refreshToken, "utf8")),
    cipher.final()
  ]);
  const authTag = cipher.getAuthTag();

  return [
    AES_GCM_TOKEN_VERSION,
    iv.toString("base64url"),
    authTag.toString("base64url"),
    ciphertext.toString("base64url")
  ].join(".");
}

export async function decryptRefreshToken({ tokenMasterKey, encryptedRefreshToken, aad }) {
  if (!tokenMasterKey || !encryptedRefreshToken || !aad) {
    throw new Error("AES_GCM_DECRYPT_INPUT_REQUIRED");
  }

  const parts = String(encryptedRefreshToken).split(".");
  if (parts.length !== 4 || parts[0] !== AES_GCM_TOKEN_VERSION) {
    throw new Error("AES_GCM_CIPHERTEXT_INVALID");
  }

  try {
    const key = decodeAes256MasterKey(tokenMasterKey);
    const iv = Buffer.from(parts[1], "base64url");
    const authTag = Buffer.from(parts[2], "base64url");
    const ciphertext = Buffer.from(parts[3], "base64url");
    const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
    decipher.setAAD(Buffer.from(aad, "utf8"));
    decipher.setAuthTag(authTag);
    return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8");
  } catch {
    throw new Error("AES_GCM_DECRYPT_FAILED");
  }
}

export async function buildEncryptedTokenRecord({
  tokenResponse,
  uid,
  oauthClientId,
  tokenMasterKey,
  tokenKeyVersion = AES_GCM_TOKEN_VERSION,
  now = new Date()
}) {
  const refreshToken = tokenResponse?.refresh_token;
  if (!refreshToken) {
    throw new Error("REFRESH_TOKEN_MISSING");
  }

  const aad = antigravityTokenAad({ uid, oauthClientId });
  const encryptedRefreshToken = await encryptRefreshToken({
    tokenMasterKey,
    refreshToken,
    aad
  });
  const timestamp = now.toISOString();
  const record = {
    providerId: "antigravity",
    oauthClientId,
    scopes: ANTIGRAVITY_SCOPES,
    encryptedRefreshToken,
    tokenEncryptionProvider: AES_GCM_SECRET_PROVIDER,
    tokenKeyVersion,
    aadVersion: "v1",
    createdAt: timestamp,
    updatedAt: timestamp,
    lastRefreshAt: null,
    lastCollectAt: null,
    lastStatus: "TOKEN_STORED",
    lastErrorKind: null
  };
  assertNoPlaintextProviderSecret(record);
  return record;
}

export function mapAntigravityApiFailure({ status }) {
  if (status === 401) {
    return {
      ok: false,
      provider: "antigravity",
      errorKind: "GOOGLE_REFRESH_FAILED",
      requiresAuth: true,
      retryable: false
    };
  }
  if (status === 403) {
    return {
      ok: false,
      provider: "antigravity",
      errorKind: "BACKEND_API_FORBIDDEN",
      requiresAuth: false,
      retryable: false
    };
  }
  if (status >= 500 || status === 408 || status === 429) {
    return {
      ok: false,
      provider: "antigravity",
      errorKind: "BACKEND_TEMPORARY_FAILURE",
      requiresAuth: false,
      retryable: true
    };
  }
  return {
    ok: false,
    provider: "antigravity",
    errorKind: "NO_TRUSTED_PAYLOAD",
    requiresAuth: false,
    retryable: true
  };
}

async function mapAndRecordAntigravityApiFailure({ step, response, tokenRef, now }) {
  const result = mapAntigravityApiFailure({ status: response.status });
  await recordAntigravityCollectFailure({ tokenRef, result, now });
  await logAntigravityApiFailure({ step, response, errorKind: result.errorKind });
  return result;
}

async function recordAntigravityCollectFailure({ tokenRef, result, now }) {
  if (typeof tokenRef?.update !== "function") {
    return;
  }
  await tokenRef.update({
    lastCollectAt: now.toISOString(),
    lastStatus: result.requiresAuth ? "AUTH_REQUIRED" : "COLLECT_FAILED",
    lastErrorKind: result.errorKind ?? null
  });
}

async function logAntigravityApiFailure({ step, response, errorKind }) {
  const body = await safeErrorBody(response);
  console.info("antigravity_api_failure", JSON.stringify({
    step,
    status: response.status,
    errorKind,
    body
  }));
}

async function safeErrorBody(response) {
  if (typeof response?.clone !== "function" || typeof response?.text !== "function") {
    return null;
  }
  const text = await response.clone().text().catch(() => "");
  if (!text) {
    return null;
  }
  const redactedText = redactDiagnosticText(text);
  try {
    const parsed = JSON.parse(redactedText);
    const error = parsed?.error ?? parsed;
    return {
      status: diagnosticValue(error?.status),
      code: diagnosticValue(error?.code),
      reason: diagnosticValue(error?.reason ?? error?.details?.[0]?.reason),
      message: diagnosticValue(error?.message)
    };
  } catch {
    return { text: redactedText.slice(0, 240) };
  }
}

function diagnosticValue(value) {
  return typeof value === "string" || typeof value === "number" ? value : null;
}

function redactDiagnosticText(value) {
  return String(value)
    .replace(/Bearer\s+[A-Za-z0-9._~+/-]+/gi, "Bearer <redacted>")
    .replace(/[A-Za-z0-9_-]{24,}/g, "<redacted>");
}

export async function collectAntigravityUsageForUid({
  uid,
  db,
  tokenMasterKey,
  oauthClientSecret,
  fetchImpl = fetch,
  now = new Date(),
  sleep = delay,
  projectResolutionDelayMs = ANTIGRAVITY_PROJECT_RESOLUTION_DELAY_MS
}) {
  if (!uid) {
    throw new Error("AUTH_REQUIRED");
  }
  if (!db || !tokenMasterKey || !oauthClientSecret) {
    throw new Error("ANTIGRAVITY_COLLECT_CONFIG_REQUIRED");
  }

  const tokenRef = db.doc(`users/${uid}/providerSecrets/antigravity`);
  const tokenSnap = await tokenRef.get();
  if (!tokenSnap.exists) {
    return {
      ok: false,
      provider: "antigravity",
      errorKind: "TOKEN_MISSING",
      requiresAuth: true,
      retryable: false
    };
  }

  const tokenRecord = tokenSnap.data();
  assertNoPlaintextProviderSecret(tokenRecord);
  if (!tokenRecord?.encryptedRefreshToken || !tokenRecord?.oauthClientId) {
    return {
      ok: false,
      provider: "antigravity",
      errorKind: "TOKEN_MISSING",
      requiresAuth: true,
      retryable: false
    };
  }

  const aad = antigravityTokenAad({ uid, oauthClientId: tokenRecord.oauthClientId });
  let refreshToken;
  try {
    refreshToken = await decryptRefreshToken({
      tokenMasterKey,
      encryptedRefreshToken: tokenRecord.encryptedRefreshToken,
      aad
    });
  } catch {
    return {
      ok: false,
      provider: "antigravity",
      errorKind: "TOKEN_DECRYPT_FAILED",
      requiresAuth: false,
      retryable: false
    };
  }

  const accessTokenResult = await refreshGoogleAccessToken({
    fetchImpl,
    refreshToken,
    oauthClientId: tokenRecord.oauthClientId,
    oauthClientSecret
  });
  if (!accessTokenResult.ok) {
    return accessTokenResult;
  }

  const loadResponse = await callAntigravityApi({
    fetchImpl,
    accessToken: accessTokenResult.accessToken,
    methodName: "loadCodeAssist",
    body: ANTIGRAVITY_CODE_ASSIST_BODY
  });
  if (!loadResponse.ok) {
    return mapAndRecordAntigravityApiFailure({
      step: "loadCodeAssist",
      response: loadResponse,
      tokenRef,
      now
    });
  }
  const loadPayload = await safeJson(loadResponse);
  const projectId = await resolveAntigravityProjectId({
    fetchImpl,
    accessToken: accessTokenResult.accessToken,
    loadPayload,
    sleep,
    delayMs: projectResolutionDelayMs
  });
  if (!projectId) {
    return {
      ok: false,
      provider: "antigravity",
      errorKind: "ANTIGRAVITY_PROJECT_UNRESOLVED",
      requiresAuth: false,
      retryable: true
    };
  }

  const modelsResponse = await callAntigravityApi({
    fetchImpl,
    accessToken: accessTokenResult.accessToken,
    methodName: "fetchAvailableModels",
    body: fetchAvailableModelsBody(projectId)
  });
  if (!modelsResponse.ok) {
    return mapAndRecordAntigravityApiFailure({
      step: "fetchAvailableModels",
      response: modelsResponse,
      tokenRef,
      now
    });
  }

  const payload = trustedAntigravityPayload(await safeJson(modelsResponse));
  if (!payload) {
    const result = mapAntigravityApiFailure({ status: 200 });
    await recordAntigravityCollectFailure({ tokenRef, result, now });
    console.info("antigravity_api_failure", JSON.stringify({
      step: "fetchAvailableModelsPayload",
      status: 200,
      errorKind: result.errorKind,
      body: null
    }));
    return result;
  }

  if (typeof tokenRef.update === "function") {
    await tokenRef.update({
      antigravityProjectId: projectId,
      lastRefreshAt: now.toISOString(),
      lastCollectAt: now.toISOString(),
      lastStatus: "CONNECTED",
      lastErrorKind: null
    });
  }

  return {
    ok: true,
    provider: "antigravity",
    source: "firebase_gateway",
    payload
  };
}

export async function disconnectAntigravityForUid({ uid, db, tokenMasterKey, fetchImpl = fetch }) {
  if (!uid) {
    throw new Error("AUTH_REQUIRED");
  }
  if (!db || !tokenMasterKey) {
    throw new Error("ANTIGRAVITY_DISCONNECT_CONFIG_REQUIRED");
  }

  const tokenRef = db.doc(`users/${uid}/providerSecrets/antigravity`);
  const tokenSnap = await tokenRef.get();
  let revokeWarning = null;

  if (tokenSnap.exists) {
    const tokenRecord = tokenSnap.data();
    assertNoPlaintextProviderSecret(tokenRecord);
    try {
      const aad = antigravityTokenAad({ uid, oauthClientId: tokenRecord.oauthClientId });
      const refreshToken = await decryptRefreshToken({
        tokenMasterKey,
        encryptedRefreshToken: tokenRecord.encryptedRefreshToken,
        aad
      });
      const response = await fetchImpl(GOOGLE_REVOKE_URL, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ token: refreshToken })
      });
      if (!response.ok) {
        revokeWarning = `GOOGLE_REVOKE_FAILED:${response.status}`;
      }
    } catch (error) {
      revokeWarning = error instanceof Error ? sanitizeWarningCode(error.message) : "GOOGLE_REVOKE_FAILED";
    }
  }

  if (typeof tokenRef.delete === "function") {
    await tokenRef.delete();
  }

  return {
    ok: true,
    provider: "antigravity",
    ...(revokeWarning ? { revokeWarning } : {})
  };
}

export async function createAntigravityOAuthStart({
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
    throw new Error("ANTIGRAVITY_OAUTH_START_CONFIG_REQUIRED");
  }

  const state = randomBytes(32).toString("base64url");
  const codeVerifier = randomBytes(32).toString("base64url");
  const codeChallenge = crypto.createHash("sha256").update(codeVerifier).digest("base64url");
  const stateHash = hashOAuthState(state);
  const expiresAt = new Date(now.getTime() + TEN_MINUTES_MS).toISOString();
  const createdAt = now.toISOString();

  await db.collection("antigravityOAuthStates").doc(stateHash).set({
    uid,
    providerId: "antigravity",
    stateHash,
    codeVerifier,
    redirectAfterAuth: DEFAULT_REDIRECT_AFTER_AUTH,
    createdAt,
    expiresAt,
    consumedAt: null
  });

  return {
    authorizationUrl: buildAntigravityAuthorizationUrl({
      clientId: oauthClientId,
      redirectUri,
      state,
      codeChallenge
    }),
    expiresAt
  };
}

export async function completeAntigravityOAuthCallback({
  query,
  db,
  tokenMasterKey,
  oauthClientId,
  oauthClientSecret,
  redirectUri,
  now = new Date(),
  fetchImpl = fetch
}) {
  const code = firstQueryValue(query?.code);
  const state = firstQueryValue(query?.state);
  if (!code || !state) {
    throw new Error("OAUTH_CALLBACK_QUERY_REQUIRED");
  }
  if (!db || !tokenMasterKey || !oauthClientId || !oauthClientSecret || !redirectUri) {
    throw new Error("ANTIGRAVITY_OAUTH_CALLBACK_CONFIG_REQUIRED");
  }

  const stateHash = hashOAuthState(state);
  const stateRef = db.collection("antigravityOAuthStates").doc(stateHash);
  const stateSnap = await stateRef.get();
  const stateRecord = stateSnap.exists ? stateSnap.data() : null;
  if (stateRecord?.consumedAt) {
    assertCompletedOAuthState({ stateRecord });
    const tokenSnap = await db.doc(`users/${stateRecord.uid}/providerSecrets/antigravity`).get();
    if (tokenSnap.exists) {
      return {
        ok: true,
        redirectAfterAuth: stateRecord.redirectAfterAuth ?? DEFAULT_REDIRECT_AFTER_AUTH
      };
    }
  }
  assertUsableOAuthState({ stateRecord, now });

  const tokenResponse = await exchangeAuthorizationCode({
    fetchImpl,
    code,
    codeVerifier: stateRecord.codeVerifier,
    oauthClientId,
    oauthClientSecret,
    redirectUri
  });
  const tokenRecord = await buildEncryptedTokenRecord({
    tokenResponse,
    uid: stateRecord.uid,
    oauthClientId,
    tokenMasterKey,
    now
  });
  const tokenRef = db.doc(`users/${stateRecord.uid}/providerSecrets/antigravity`);
  const consumedAt = now.toISOString();

  if (typeof db.runTransaction === "function") {
    await db.runTransaction(async (transaction) => {
      const latestSnap = await transaction.get(stateRef);
      const latest = latestSnap.exists ? latestSnap.data() : null;
      assertUsableOAuthState({ stateRecord: latest, now });
      transaction.set(tokenRef, tokenRecord);
      transaction.update(stateRef, { consumedAt });
    });
  } else {
    await tokenRef.set(tokenRecord);
    await stateRef.update({ consumedAt });
  }

  return {
    ok: true,
    redirectAfterAuth: stateRecord.redirectAfterAuth ?? DEFAULT_REDIRECT_AFTER_AUTH
  };
}

export async function completeAntigravityOAuthCallbackForUid({
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
    throw new Error("ANTIGRAVITY_OAUTH_COMPLETE_CONFIG_REQUIRED");
  }
  const callback = parseLoopbackCallback(callbackUrl);
  const code = callback.searchParams.get("code");
  const state = callback.searchParams.get("state");
  const error = callback.searchParams.get("error");
  if (error) {
    throw new Error("ANTIGRAVITY_OAUTH_CALLBACK_DENIED");
  }
  if (!code || !state) {
    throw new Error("OAUTH_CALLBACK_QUERY_REQUIRED");
  }

  const stateHash = hashOAuthState(state);
  const stateRef = db.collection("antigravityOAuthStates").doc(stateHash);
  const stateSnap = await stateRef.get();
  const stateRecord = stateSnap.exists ? stateSnap.data() : null;
  assertUsableOAuthStateForUid({ stateRecord, uid, now });

  const tokenResponse = await exchangeAuthorizationCode({
    fetchImpl,
    code,
    codeVerifier: stateRecord.codeVerifier,
    oauthClientId,
    oauthClientSecret,
    redirectUri
  });
  if (!tokenResponse?.access_token) {
    throw new Error("GOOGLE_TOKEN_RESPONSE_ACCESS_TOKEN_MISSING");
  }

  await stateRef.update({ consumedAt: now.toISOString() });
  return tokenResult(tokenResponse);
}

export async function refreshAntigravityAccessToken({
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
    throw new Error("ANTIGRAVITY_REFRESH_TOKEN_REQUIRED");
  }
  if (!oauthClientId || !oauthClientSecret) {
    throw new Error("ANTIGRAVITY_OAUTH_REFRESH_CONFIG_REQUIRED");
  }

  const tokenResponse = await refreshGoogleAccessToken({
    fetchImpl,
    refreshToken,
    oauthClientId,
    oauthClientSecret
  });
  if (!tokenResponse.ok) {
    throw new Error(tokenResponse.errorKind ?? "GOOGLE_REFRESH_FAILED");
  }
  return tokenResult({
    access_token: tokenResponse.accessToken,
    refresh_token: refreshToken
  });
}

export function createAntigravityGatewayHandlers({
  onCall,
  onRequest,
  HttpsError,
  db,
  config,
  fetchImpl = fetch,
  secrets = [],
  enforceAppCheck = true
}) {
  return {
    startAntigravityOAuth: onCall(callOptionsFor("startAntigravityOAuth"), async (request) => {
      if (!request.auth?.uid) {
        throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
      }
      return createAntigravityOAuthStart({
        uid: request.auth.uid,
        db,
        oauthClientId: readConfigValue(config.oauthClientId),
        redirectUri: readConfigValue(config.redirectUri)
      });
    }),
    antigravityOAuthCallback: onRequest(requestOptionsFor("antigravityOAuthCallback"), async (request, response) => {
      try {
        const result = await completeAntigravityOAuthCallback({
          query: request.query,
          db,
          tokenMasterKey: readConfigValue(config.tokenMasterKey),
          oauthClientId: readConfigValue(config.oauthClientId),
          oauthClientSecret: readConfigValue(config.oauthClientSecret),
          redirectUri: readConfigValue(config.redirectUri),
          fetchImpl
        });
        response.status(200).send(successHtml(result.redirectAfterAuth));
      } catch (error) {
        const code = error instanceof Error ? error.message : "OAUTH_CALLBACK_FAILED";
        response.status(statusForCallbackError(code)).send(errorHtml(code));
      }
    }),
    completeAntigravityOAuth: onCall(callOptionsFor("completeAntigravityOAuth"), async (request) => {
      if (!request.auth?.uid) {
        throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
      }
      try {
        return await completeAntigravityOAuthCallbackForUid({
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
    refreshAntigravityAccessToken: onCall(callOptionsFor("refreshAntigravityAccessToken"), async (request) => {
      if (!request.auth?.uid) {
        throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
      }
      try {
        return await refreshAntigravityAccessToken({
          uid: request.auth.uid,
          refreshToken: String(request.data?.refreshToken ?? ""),
          oauthClientId: readConfigValue(config.oauthClientId),
          oauthClientSecret: readConfigValue(config.oauthClientSecret),
          fetchImpl
        });
      } catch (error) {
        throw callableError(error, HttpsError);
      }
    }),
    collectAntigravityUsage: onCall(callOptionsFor("collectAntigravityUsage"), async (request) => {
      if (!request.auth?.uid) {
        throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
      }
      return collectAntigravityUsageForUid({
        uid: request.auth.uid,
        db,
        tokenMasterKey: readConfigValue(config.tokenMasterKey),
        oauthClientSecret: readConfigValue(config.oauthClientSecret),
        fetchImpl
      });
    }),
    disconnectAntigravity: onCall(callOptionsFor("disconnectAntigravity"), async (request) => {
      if (!request.auth?.uid) {
        throw new HttpsError("unauthenticated", "AUTH_REQUIRED");
      }
      return disconnectAntigravityForUid({
        uid: request.auth.uid,
        db,
        tokenMasterKey: readConfigValue(config.tokenMasterKey),
        fetchImpl
      });
    })
  };

  function callOptionsFor(functionName) {
    return { enforceAppCheck, secrets: secretsFor(functionName) };
  }

  function requestOptionsFor(functionName) {
    return { secrets: secretsFor(functionName) };
  }

  function secretsFor(functionName) {
    return Array.isArray(secrets) ? secrets : secrets[functionName] ?? [];
  }
}

async function refreshGoogleAccessToken({ fetchImpl, refreshToken, oauthClientId, oauthClientSecret }) {
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
  const tokenResponse = await safeJson(response);
  if (!response.ok || !tokenResponse?.access_token) {
    return {
      ok: false,
      provider: "antigravity",
      errorKind: "GOOGLE_REFRESH_FAILED",
      requiresAuth: true,
      retryable: false
    };
  }
  return { ok: true, accessToken: tokenResponse.access_token };
}

async function callAntigravityApi({ fetchImpl, accessToken, methodName, body }) {
  return fetchImpl(`${ANTIGRAVITY_API_BASE}/v1internal:${methodName}`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
      "user-agent": ANTIGRAVITY_USER_AGENT
    },
    body: JSON.stringify(body ?? {})
  });
}

async function safeJson(response) {
  return response.json().catch(() => null);
}

function delay(delayMs) {
  return new Promise((resolve) => setTimeout(resolve, delayMs));
}

function trustedAntigravityPayload(body) {
  const models = body?.models ?? body?.availableModels;
  const trustedModels = collectTrustedModels(models);
  if (trustedModels.length === 0) {
    return null;
  }
  return {
    provider: "antigravity",
    source: "firebase_gateway",
    models: trustedModels
  };
}

function fetchAvailableModelsBody(projectId) {
  return projectId ? { project: projectId } : {};
}

async function resolveAntigravityProjectId({ fetchImpl, accessToken, loadPayload, sleep, delayMs }) {
  const loadedProjectId = antigravityProjectIdFromLoad(loadPayload);
  if (loadedProjectId) {
    return loadedProjectId;
  }

  const tierId = antigravityTierIdFromLoad(loadPayload);
  if (!tierId) {
    return null;
  }

  const onboardResponse = await callAntigravityApi({
    fetchImpl,
    accessToken,
    methodName: "onboardUser",
    body: {
      tierId,
      metadata: ANTIGRAVITY_METADATA
    }
  });
  if (!onboardResponse.ok) {
    return null;
  }

  const onboardPayload = await safeJson(onboardResponse);
  const onboardProjectId = antigravityProjectIdFromLoad(onboardPayload?.response ?? onboardPayload);
  if (onboardProjectId) {
    return onboardProjectId;
  }

  for (let attempt = 1; attempt <= ANTIGRAVITY_PROJECT_RESOLUTION_ATTEMPTS; attempt += 1) {
    await sleep(delayMs);
    const reloadedResponse = await callAntigravityApi({
      fetchImpl,
      accessToken,
      methodName: "loadCodeAssist",
      body: ANTIGRAVITY_CODE_ASSIST_BODY
    });
    if (!reloadedResponse.ok) {
      return null;
    }
    const reloadedProjectId = antigravityProjectIdFromLoad(await safeJson(reloadedResponse));
    if (reloadedProjectId) {
      return reloadedProjectId;
    }
  }
  return null;
}

function antigravityProjectIdFromLoad(body) {
  return stringValue(body?.cloudaicompanionProject) ??
    stringValue(body?.cloudaicompanionProject?.id) ??
    stringValue(body?.project) ??
    stringValue(body?.project?.id);
}

function antigravityTierIdFromLoad(body) {
  const defaultTier = Array.isArray(body?.allowedTiers)
    ? body.allowedTiers.find((tier) => tier?.isDefault && stringValue(tier?.id))
    : null;
  return stringValue(defaultTier?.id) ??
    stringValue(body?.allowedTiers?.find?.((tier) => stringValue(tier?.id))?.id) ??
    stringValue(body?.paidTier?.id) ??
    stringValue(body?.currentTier?.id);
}

function collectTrustedModels(models) {
  if (Array.isArray(models)) {
    return models.map((model) => trustedModelFromValue(model)).filter(Boolean);
  }
  if (models && typeof models === "object") {
    return Object.entries(models)
      .map(([id, model]) => trustedModelFromValue(model, id))
      .filter(Boolean);
  }
  return [];
}

function trustedModelFromValue(model, fallbackId = null) {
  if (!model || typeof model !== "object") {
    return null;
  }
  const label = stringValue(
    model.label ?? model.displayName ?? model.display_name ?? model.name ?? model.model ?? fallbackId
  );
  const quota = model.quotaInfo ?? model.quota ?? model;
  const remainingFraction = numberValue(
    quota.remainingFraction ?? quota.remaining_fraction
  );
  if (!label || label.startsWith("_") || label.toLowerCase().includes("internal")) {
    return null;
  }
  if (isIgnoredAntigravityModel(fallbackId, label)) {
    return null;
  }
  if (remainingFraction === null) {
    return null;
  }
  return {
    modelId: stringValue(model.modelId ?? model.model_id ?? fallbackId),
    label,
    remainingFraction,
    isExhausted: Boolean(quota.isExhausted ?? quota.is_exhausted ?? false),
    resetTime: stringValue(quota.resetTime ?? quota.reset_time),
    modelProvider: stringValue(model.modelProvider ?? model.model_provider)
  };
}

function isIgnoredAntigravityModel(modelId, label) {
  const id = String(modelId ?? "");
  const normalizedId = id.toLowerCase();
  if (ANTIGRAVITY_IGNORED_MODEL_ID_PREFIXES.some((prefix) => normalizedId.startsWith(prefix))) {
    return true;
  }
  if (ANTIGRAVITY_IGNORED_MODEL_ID_PARTS.some((part) => normalizedId.includes(part))) {
    return true;
  }
  return ANTIGRAVITY_IGNORED_MODEL_LABEL_PARTS.some((part) => label.includes(part));
}

function stringValue(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function numberValue(value) {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function sanitizeWarningCode(value) {
  return String(value).replace(/[^A-Z0-9_:.-]/gi, "") || "GOOGLE_REVOKE_FAILED";
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
  return tokenResponse;
}

function assertUsableOAuthState({ stateRecord, now }) {
  if (!stateRecord) {
    throw new Error("OAUTH_STATE_NOT_FOUND");
  }
  if (stateRecord.consumedAt) {
    throw new Error("OAUTH_STATE_CONSUMED");
  }
  if (!stateRecord.uid || !stateRecord.codeVerifier) {
    throw new Error("OAUTH_STATE_INVALID");
  }
  if (new Date(stateRecord.expiresAt).getTime() <= now.getTime()) {
    throw new Error("OAUTH_STATE_EXPIRED");
  }
}

function assertUsableOAuthStateForUid({ stateRecord, uid, now }) {
  assertUsableOAuthState({ stateRecord, now });
  if (stateRecord.uid !== uid) {
    throw new Error("OAUTH_STATE_INVALID");
  }
}

function assertCompletedOAuthState({ stateRecord }) {
  if (!stateRecord.uid) {
    throw new Error("OAUTH_STATE_INVALID");
  }
}

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value;
}

function parseLoopbackCallback(callbackUrl) {
  let url;
  try {
    url = new URL(callbackUrl);
  } catch {
    throw new Error("ANTIGRAVITY_OAUTH_CALLBACK_INVALID");
  }
  if (!["127.0.0.1", "localhost"].includes(url.hostname) || url.pathname !== "/callback") {
    throw new Error("ANTIGRAVITY_OAUTH_CALLBACK_INVALID");
  }
  return url;
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

function readConfigValue(value) {
  return typeof value === "function" ? value() : value;
}

function decodeAes256MasterKey(value) {
  const key = String(value ?? "").trim();
  if (/^[0-9a-f]{64}$/i.test(key)) {
    return Buffer.from(key, "hex");
  }

  for (const encoding of ["base64", "base64url"]) {
    const decoded = Buffer.from(key, encoding);
    if (decoded.length === 32) {
      return decoded;
    }
  }

  const raw = Buffer.from(key, "utf8");
  if (raw.length === 32) {
    return raw;
  }

  throw new Error("AES_MASTER_KEY_INVALID");
}

function statusForCallbackError(code) {
  if (
    code === "OAUTH_CALLBACK_QUERY_REQUIRED" ||
    code === "OAUTH_STATE_NOT_FOUND" ||
    code === "OAUTH_STATE_CONSUMED" ||
    code === "OAUTH_STATE_INVALID" ||
    code === "OAUTH_STATE_EXPIRED" ||
    code === "REFRESH_TOKEN_MISSING"
  ) {
    return 400;
  }
  if (code.startsWith("GOOGLE_TOKEN_EXCHANGE_FAILED")) {
    return 502;
  }
  return 500;
}

function callableError(error, HttpsError) {
  const code = error instanceof Error ? error.message : "ANTIGRAVITY_OAUTH_FAILED";
  if (code === "AUTH_REQUIRED") {
    return new HttpsError("unauthenticated", code);
  }
  if (
    code === "OAUTH_CALLBACK_QUERY_REQUIRED" ||
    code === "OAUTH_STATE_NOT_FOUND" ||
    code === "OAUTH_STATE_CONSUMED" ||
    code === "OAUTH_STATE_INVALID" ||
    code === "OAUTH_STATE_EXPIRED" ||
    code === "ANTIGRAVITY_OAUTH_CALLBACK_INVALID" ||
    code === "ANTIGRAVITY_REFRESH_TOKEN_REQUIRED"
  ) {
    return new HttpsError("invalid-argument", code);
  }
  if (code.startsWith("GOOGLE_TOKEN_EXCHANGE_FAILED") || code === "GOOGLE_REFRESH_FAILED") {
    return new HttpsError("failed-precondition", code);
  }
  return new HttpsError("internal", code);
}

function successHtml(redirectAfterAuth) {
  const safeRedirect = String(redirectAfterAuth ?? DEFAULT_REDIRECT_AFTER_AUTH).startsWith("aiquota://")
    ? redirectAfterAuth
    : DEFAULT_REDIRECT_AFTER_AUTH;
  return `<!doctype html><html><body><p>Antigravity connected.</p><a href="${safeRedirect}">Return to AI Quota</a></body></html>`;
}

function errorHtml(code) {
  const safeCode = String(code).replace(/[^A-Z0-9_:.-]/gi, "");
  return `<!doctype html><html><body><p>Antigravity connection failed.</p><p>${safeCode}</p></body></html>`;
}
