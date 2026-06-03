# AI Quota Mobile Sync Data Model

## Overview

The free Firebase model stores everything under the signed-in user's `uid`. There is no pairing code collection.

## Firestore paths

- `/users/{uid}`
- `/users/{uid}/devices/{deviceId}`
- `/users/{uid}/devices/{deviceId}/snapshots/latest`

## User document

Path:

`/users/{uid}`

Example fields:

```json
{
  "uid": "firebase_uid_123",
  "email": "user@example.com",
  "displayName": "Min",
  "authProviders": ["google.com"],
  "createdAt": "2026-04-29T10:00:00.000Z"
}
```

## Device document

Path:

`/users/{uid}/devices/{deviceId}`

Example fields:

```json
{
  "deviceId": "home-pc",
  "name": "Home PC",
  "platform": "windows",
  "appName": "AI Quota for Windows",
  "appVersion": "1.4.0",
  "linkedAt": "2026-04-29T10:04:00.000Z",
  "lastSeenAt": "2026-04-29T10:12:00.000Z",
  "syncEnabled": true,
  "revokedAt": null
}
```

Notes:

- `deviceId` must be stable for one Windows install.
- `name` is user-facing and may be changed from Android.
- multiple devices may exist under one `uid`.

## Snapshot document

Path:

`/users/{uid}/devices/{deviceId}/snapshots/latest`

Example fields:

```json
{
  "schemaVersion": 1,
  "fetchedAt": "2026-04-29T10:00:00.000Z",
  "uploadedAt": "2026-04-29T10:00:05.000Z",
  "source": "ai-quota-windows",
  "providers": [
    {
      "providerId": "codex",
      "displayName": "Codex",
      "status": "ok",
      "fetchedAt": "2026-04-29T10:00:00.000Z",
      "lines": [
        {
          "type": "progress",
          "label": "Session",
          "used": 42,
          "limit": 100,
          "format": { "kind": "percent" },
          "resetsAt": "2026-04-29T15:00:00.000Z"
        }
      ]
    }
  ]
}
```

## Snapshot constraints

Snapshots must never include:

- provider access tokens
- provider refresh tokens
- API keys
- cookies or sessions
- raw logs
- local file paths
- credential files

## Client behavior

- Android reads `devices` and selects the newest `lastSeenAt` by default.
- Android writes `devices/{deviceId}.name` when renaming a device.
- Windows writes its own device document and `snapshots/latest`.
