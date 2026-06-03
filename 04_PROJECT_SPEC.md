# AI Quota Mobile Sync Project Spec

## Decision

The project now uses the free Firebase model.

- Firebase Auth is the only identity boundary.
- Android and Windows sign in with the same Firebase account.
- Firestore is the single source of truth.
- Cloud Functions and pairing codes are not required for the MVP.

## Firestore model

- `/users/{uid}`
- `/users/{uid}/devices/{deviceId}`
- `/users/{uid}/devices/{deviceId}/snapshots/latest`

## Ownership model

- A signed-in user may read and write only their own `uid` subtree.
- Each Windows install writes to one `deviceId`.
- Multiple PCs under the same account are supported.
- Android may rename `devices/{deviceId}.name`.

## Android requirements

- Sign in with Google
- Read the device list from Firestore
- Select the most recent device by default
- Show remaining usage limits on the home screen
- Allow manual device switching from settings
- Allow renaming the selected device from settings
- Read and render active providers from the selected device latest snapshot
- Cache display-only widget data locally

## Snapshot rules

Windows uploads must never store:

- provider access tokens
- provider refresh tokens
- API keys
- cookies or session files
- raw logs
- local file paths

## Firestore rules intent

- `/users/{uid}`: owner read/write
- `/devices/{deviceId}`: owner read/write
- `/snapshots/{snapshotId}`: owner read/write
- no deletes for MVP
- everything else denied

## Windows contract for the free plan

- Windows signs in with Firebase Auth using the same account as Android
- Windows writes its device document directly
- Windows writes `snapshots/latest` directly
- Windows updates `lastSeenAt`
- Windows keeps a stable local `deviceId`

## Device UX contract

- Android home must focus on remaining AI quota limits
- Android settings must show connected devices
- Android settings must show which device is currently selected
- Android settings must allow device rename
- Android must show snapshot freshness and provider error state

## Verification

- source tests for device-list flow
- Firestore rules deployed for owner-scoped client writes
- Android debug build passes
- emulator launch verifies sign-in entry and device-list shell
