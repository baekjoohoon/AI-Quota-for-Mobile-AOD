# AI Quota Mobile Sync Phases

## Phase 1: Free-plan Android MVP

Goal:

- run on Firebase Spark
- no pairing code
- Android reads devices and latest snapshots directly from Firestore

Scope:

- Firebase Auth with Google
- Firestore owner-scoped rules
- Android device list
- selected device snapshot view
- device rename
- widget cache

Definition of done:

- Android signs in successfully
- device list loads for the signed-in user
- rename updates Firestore
- latest snapshot renders for the selected device
- widget uses local cache only

## Phase 2: Windows direct Firestore upload

Goal:

- let AI Quota for Windows sign in with the same Firebase account and write snapshots directly

Scope:

- Windows Firebase Auth
- stable `deviceId`
- direct writes to `/devices/{deviceId}`
- direct writes to `/snapshots/latest`
- end-to-end Android verification with real data

## Phase 3: Device management polish

Goal:

- make multi-device usage easier to manage

Scope:

- better device switcher
- unlink or revoke flow
- clearer stale and offline messaging
- sort and filter device list

## Deferred

- iOS implementation
- Apple sign-in
- Cloud Functions upload broker
- pairing codes
- push notifications
