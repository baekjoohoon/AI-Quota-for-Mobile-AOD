# Windows Sync Contract for the Free Firebase Model

This document describes the direct Firestore contract for AI Quota for Windows when the project runs on the Firebase Spark plan.

## Authentication

- Windows signs in with Firebase Auth using Google.
- Android signs in with the same Firebase project.
- The same Firebase account produces the same `uid`.
- No pairing code is used.

## Device identity

Each Windows install owns a stable `deviceId`.

Recommended examples:

- `home-pc`
- `work-laptop`
- `office-desktop`

The exact value may be generated locally and stored by the Windows app, but it must stay stable across launches.

## Device document

Path:

`/users/{uid}/devices/{deviceId}`

Example:

```json
{
  "deviceId": "home-pc",
  "name": "Home PC",
  "platform": "windows",
  "appName": "AI Quota for Windows",
  "appVersion": "1.4.0",
  "linkedAt": "2026-04-29T12:00:00.000Z",
  "lastSeenAt": "2026-04-29T12:05:00.000Z",
  "syncEnabled": true,
  "revokedAt": null
}
```

## Snapshot document

Path:

`/users/{uid}/devices/{deviceId}/snapshots/latest`

Example:

```json
{
  "schemaVersion": 1,
  "fetchedAt": "2026-04-29T12:05:00.000Z",
  "uploadedAt": "2026-04-29T12:05:05.000Z",
  "source": "ai-quota-windows",
  "providers": [
    {
      "providerId": "codex",
      "displayName": "Codex",
      "status": "ok",
      "fetchedAt": "2026-04-29T12:05:00.000Z",
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

## Upload rules

Windows must never write:

- provider access tokens
- provider refresh tokens
- API keys
- local file paths
- raw logs
- credential files

## Android assumptions

- Android reads the connected device list from `/users/{uid}/devices`
- Android reads `snapshots/latest` for the selected device
- Android may update `devices/{deviceId}.name`
