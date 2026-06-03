import crypto from "node:crypto";

const TEN_MINUTES_MS = 10 * 60 * 1000;
const TOKEN_VERSION = "v1";

const forbiddenKeys = new Set([
  "accessToken",
  "refreshToken",
  "apiKey",
  "token",
  "secret",
  "cookie",
  "session",
  "localFilePath",
  "rawLog",
  "credential",
  "credentials",
  "authFile"
]);

const forbiddenProviderSecretKeys = new Set([
  "accessToken",
  "refreshToken",
  "token",
  "clientSecret",
  "cookie",
  "authorization"
]);

const validProviderStatuses = new Set(["ok", "error", "unknown"]);
const validFormatKinds = new Set(["percent", "count", "time", "text"]);

export function createPairingCodeRecord({ uid, now = new Date(), randomInt = randomSixDigitInt }) {
  if (!uid) {
    throw new Error("UID_REQUIRED");
  }

  const createdAt = toIso(now);
  const expiresAt = toIso(new Date(now.getTime() + TEN_MINUTES_MS));
  const code = String(randomInt(100000, 1000000)).padStart(6, "0");

  if (!/^\d{6}$/.test(code)) {
    throw new Error("PAIRING_CODE_INVALID");
  }

  return {
    code,
    uid,
    createdAt,
    expiresAt,
    consumedAt: null,
    consumedByDeviceId: null
  };
}

export function consumePairingCodeRecord({ codeRecord, now = new Date(), deviceId }) {
  if (!codeRecord) {
    throw new Error("PAIRING_CODE_NOT_FOUND");
  }
  if (codeRecord.consumedAt) {
    throw new Error("PAIRING_CODE_CONSUMED");
  }
  if (new Date(codeRecord.expiresAt).getTime() <= now.getTime()) {
    throw new Error("PAIRING_CODE_EXPIRED");
  }
  if (!deviceId) {
    throw new Error("DEVICE_ID_REQUIRED");
  }

  return {
    ...codeRecord,
    consumedAt: toIso(now),
    consumedByDeviceId: deviceId
  };
}

export function buildDeviceRecord({
  deviceId,
  deviceName,
  appVersion,
  now = new Date(),
  platform = "windows"
}) {
  if (!deviceId) {
    throw new Error("DEVICE_ID_REQUIRED");
  }
  if (!deviceName?.trim()) {
    throw new Error("DEVICE_NAME_REQUIRED");
  }
  if (!appVersion?.trim()) {
    throw new Error("APP_VERSION_REQUIRED");
  }

  const timestamp = toIso(now);
  return {
    deviceId,
    name: deviceName.trim(),
    platform,
    appName: "AI Quota for Windows",
    appVersion: appVersion.trim(),
    linkedAt: timestamp,
    lastSeenAt: timestamp,
    syncEnabled: true,
    revokedAt: null
  };
}

export function createUploadToken({ uid, deviceId, secret }) {
  if (!uid || !deviceId || !secret) {
    throw new Error("UPLOAD_TOKEN_INPUT_REQUIRED");
  }

  const payload = `${TOKEN_VERSION}:${uid}:${deviceId}`;
  const signature = crypto.createHmac("sha256", secret).update(payload).digest("base64url");
  return `${payload}:${signature}`;
}

export function verifyUploadToken({ token, uid, deviceId, secret, device }) {
  if (!device || device.syncEnabled !== true || device.revokedAt) {
    throw new Error("DEVICE_REVOKED");
  }

  const expected = createUploadToken({ uid, deviceId, secret });
  const tokenBytes = Buffer.from(token ?? "");
  const expectedBytes = Buffer.from(expected);

  if (
    tokenBytes.length !== expectedBytes.length ||
    !crypto.timingSafeEqual(tokenBytes, expectedBytes)
  ) {
    throw new Error("UPLOAD_TOKEN_INVALID");
  }

  return true;
}

export function assertSnapshotIsSafe(snapshot) {
  assertPlainObject(snapshot, "SNAPSHOT_INVALID");
  findForbiddenField(snapshot);

  if (snapshot.schemaVersion !== 1) {
    throw new Error("SNAPSHOT_SCHEMA_VERSION_UNSUPPORTED");
  }
  assertIsoString(snapshot.fetchedAt, "SNAPSHOT_FETCHED_AT_INVALID");
  if (snapshot.uploadedAt !== undefined) {
    assertIsoString(snapshot.uploadedAt, "SNAPSHOT_UPLOADED_AT_INVALID");
  }
  if (snapshot.source !== "ai-quota-windows") {
    throw new Error("SNAPSHOT_SOURCE_INVALID");
  }
  if (!Array.isArray(snapshot.providers)) {
    throw new Error("SNAPSHOT_PROVIDERS_INVALID");
  }

  for (const provider of snapshot.providers) {
    assertProvider(provider);
  }

  return true;
}

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
  for (const key of forbiddenProviderSecretKeys) {
    if (Object.prototype.hasOwnProperty.call(record ?? {}, key)) {
      throw new Error(`PROVIDER_SECRET_FORBIDDEN_FIELD:${key}`);
    }
  }
  return true;
}

export function withUploadedAt(snapshot, now = new Date()) {
  assertSnapshotIsSafe(snapshot);
  return {
    ...snapshot,
    uploadedAt: toIso(now)
  };
}

function assertProvider(provider) {
  assertPlainObject(provider, "PROVIDER_INVALID");
  assertNonEmptyString(provider.providerId, "PROVIDER_ID_REQUIRED");
  assertNonEmptyString(provider.displayName, "PROVIDER_DISPLAY_NAME_REQUIRED");
  assertIsoString(provider.fetchedAt, "PROVIDER_FETCHED_AT_INVALID");

  if (!validProviderStatuses.has(provider.status)) {
    throw new Error("PROVIDER_STATUS_INVALID");
  }
  if (!Array.isArray(provider.lines)) {
    throw new Error("PROVIDER_LINES_INVALID");
  }
  if (provider.errorCode !== undefined) {
    assertNonEmptyString(provider.errorCode, "PROVIDER_ERROR_CODE_INVALID");
  }
  if (provider.errorMessage !== undefined) {
    assertNonEmptyString(provider.errorMessage, "PROVIDER_ERROR_MESSAGE_INVALID");
  }

  for (const line of provider.lines) {
    assertUsageLine(line);
  }
}

function assertUsageLine(line) {
  assertPlainObject(line, "USAGE_LINE_INVALID");
  assertNonEmptyString(line.type, "USAGE_LINE_TYPE_REQUIRED");
  assertNonEmptyString(line.label, "USAGE_LINE_LABEL_REQUIRED");
  assertPlainObject(line.format, "USAGE_LINE_FORMAT_INVALID");

  if (!validFormatKinds.has(line.format.kind)) {
    throw new Error("USAGE_LINE_FORMAT_KIND_INVALID");
  }

  for (const numericField of ["used", "limit", "remaining"]) {
    if (line[numericField] !== undefined && typeof line[numericField] !== "number") {
      throw new Error("USAGE_LINE_NUMBER_INVALID");
    }
  }
  if (line.resetsAt !== undefined) {
    assertIsoString(line.resetsAt, "USAGE_LINE_RESETS_AT_INVALID");
  }
}

function findForbiddenField(value, path = []) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => findForbiddenField(item, [...path, String(index)]));
    return;
  }

  if (!value || typeof value !== "object") {
    return;
  }

  for (const [key, child] of Object.entries(value)) {
    if (forbiddenKeys.has(key)) {
      throw new Error(`SNAPSHOT_FORBIDDEN_FIELD:${[...path, key].join(".")}`);
    }
    findForbiddenField(child, [...path, key]);
  }
}

function randomSixDigitInt(min, max) {
  return crypto.randomInt(min, max);
}

function toIso(date) {
  return date.toISOString();
}

function assertPlainObject(value, code) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(code);
  }
}

function assertNonEmptyString(value, code) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(code);
  }
}

function assertIsoString(value, code) {
  if (typeof value !== "string" || Number.isNaN(new Date(value).getTime())) {
    throw new Error(code);
  }
}
