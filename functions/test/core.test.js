import test from "node:test";
import assert from "node:assert/strict";

import {
  antigravityTokenAad,
  assertSnapshotIsSafe,
  assertNoPlaintextProviderSecret,
  buildDeviceRecord,
  consumePairingCodeRecord,
  createPairingCodeRecord,
  createUploadToken,
  hashOAuthState,
  verifyUploadToken
} from "../src/core.js";

const fixedNow = new Date("2026-04-29T01:00:00.000Z");

test("createPairingCodeRecord creates a 6 digit one-time code that expires in 10 minutes", () => {
  const record = createPairingCodeRecord({
    uid: "user_123",
    now: fixedNow,
    randomInt: () => 482193
  });

  assert.equal(record.code, "482193");
  assert.equal(record.uid, "user_123");
  assert.equal(record.consumedAt, null);
  assert.equal(record.createdAt, "2026-04-29T01:00:00.000Z");
  assert.equal(record.expiresAt, "2026-04-29T01:10:00.000Z");
});

test("consumePairingCodeRecord rejects expired or already consumed codes", () => {
  const active = createPairingCodeRecord({
    uid: "user_123",
    now: fixedNow,
    randomInt: () => 123456
  });

  assert.throws(
    () =>
      consumePairingCodeRecord({
        codeRecord: { ...active, expiresAt: "2026-04-29T00:59:59.000Z" },
        now: fixedNow,
        deviceId: "dev_test"
      }),
    /PAIRING_CODE_EXPIRED/
  );

  assert.throws(
    () =>
      consumePairingCodeRecord({
        codeRecord: { ...active, consumedAt: "2026-04-29T01:01:00.000Z" },
        now: fixedNow,
        deviceId: "dev_test"
      }),
    /PAIRING_CODE_CONSUMED/
  );
});

test("buildDeviceRecord creates a Windows sync device with enabled sync", () => {
  const device = buildDeviceRecord({
    deviceId: "dev_home_pc_abc123",
    deviceName: "Home PC",
    appVersion: "1.4.0",
    now: fixedNow
  });

  assert.deepEqual(device, {
    deviceId: "dev_home_pc_abc123",
    name: "Home PC",
    platform: "windows",
    appName: "AI Quota for Windows",
    appVersion: "1.4.0",
    linkedAt: "2026-04-29T01:00:00.000Z",
    lastSeenAt: "2026-04-29T01:00:00.000Z",
    syncEnabled: true,
    revokedAt: null
  });
});

test("upload token verification rejects mismatched and revoked devices", () => {
  const token = createUploadToken({
    uid: "user_123",
    deviceId: "dev_home_pc_abc123",
    secret: "test-secret"
  });

  assert.equal(
    verifyUploadToken({
      token,
      uid: "user_123",
      deviceId: "dev_home_pc_abc123",
      secret: "test-secret",
      device: { syncEnabled: true, revokedAt: null }
    }),
    true
  );

  assert.throws(
    () =>
      verifyUploadToken({
        token,
        uid: "user_123",
        deviceId: "dev_other",
        secret: "test-secret",
        device: { syncEnabled: true, revokedAt: null }
      }),
    /UPLOAD_TOKEN_INVALID/
  );

  assert.throws(
    () =>
      verifyUploadToken({
        token,
        uid: "user_123",
        deviceId: "dev_home_pc_abc123",
        secret: "test-secret",
        device: { syncEnabled: true, revokedAt: "2026-04-29T02:00:00.000Z" }
      }),
    /DEVICE_REVOKED/
  );
});

test("assertSnapshotIsSafe rejects provider credentials and local artifacts", () => {
  const safeSnapshot = {
    schemaVersion: 1,
    fetchedAt: "2026-04-29T01:00:00.000Z",
    source: "ai-quota-windows",
    providers: [
      {
        providerId: "codex",
        displayName: "Codex",
        status: "ok",
        fetchedAt: "2026-04-29T01:00:00.000Z",
        lines: [
          {
            type: "progress",
            label: "Session",
            used: 42,
            limit: 100,
            format: { kind: "percent" }
          }
        ]
      },
      {
        providerId: "claude",
        displayName: "Claude",
        status: "error",
        fetchedAt: "2026-04-29T01:00:00.000Z",
        errorCode: "auth_expired",
        errorMessage: "PC에서 다시 로그인 필요",
        lines: []
      }
    ]
  };

  assert.equal(assertSnapshotIsSafe(safeSnapshot), true);

  assert.throws(
    () =>
      assertSnapshotIsSafe({
        ...safeSnapshot,
        providers: [
          {
            providerId: "codex",
            displayName: "Codex",
            status: "ok",
            fetchedAt: "2026-04-29T01:00:00.000Z",
            accessToken: "secret",
            lines: []
          }
        ]
      }),
    /SNAPSHOT_FORBIDDEN_FIELD/
  );

  assert.throws(
    () =>
      assertSnapshotIsSafe({
        ...safeSnapshot,
        localFilePath: "C:\\Users\\user\\.config\\provider"
      }),
    /SNAPSHOT_FORBIDDEN_FIELD/
  );
});

test("hashOAuthState returns stable sha256 base64url without exposing raw state", () => {
  const rawState = "state-secret-value";
  const hash = hashOAuthState(rawState);

  assert.equal(hash.includes(rawState), false);
  assert.match(hash, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(hashOAuthState(rawState), hash);
});

test("antigravityTokenAad binds token encryption to uid provider client and version", () => {
  assert.equal(
    antigravityTokenAad({
      uid: "uid-123",
      oauthClientId: "client.apps.googleusercontent.com"
    }),
    "uid:uid-123:provider:antigravity:oauthClient:client.apps.googleusercontent.com:aad:v1"
  );
});

test("assertNoPlaintextProviderSecret rejects plaintext token fields", () => {
  assert.throws(
    () =>
      assertNoPlaintextProviderSecret({
        encryptedRefreshToken: "ciphertext",
        refreshToken: "plain"
      }),
    /PROVIDER_SECRET_FORBIDDEN_FIELD:refreshToken/
  );
});
