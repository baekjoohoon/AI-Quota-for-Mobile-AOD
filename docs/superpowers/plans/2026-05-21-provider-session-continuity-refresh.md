# Provider Session Continuity Refresh impl Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Superseded: 사용자 UX/데이터 신뢰성 기준을 반영해 이 계획은 `D:/Vibe Project/AI Usage for Mobile/docs/superpowers/plans/2026-05-21-provider-usage-freshness-auth-continuity.md`로 대체한다. 새 기준은 세션 유지보다 usage freshness와 stale 표시 정책을 우선한다.

**Goal:** 사용자는 provider별로 한 번 로그인하면 된다. 이후 앱은 보이지 않는 collector WebView에서 세션을 유지하고, 가능한 한 1분 단위로 usage를 수집한다. 사용자에게 다시 로그인 요청을 노출하는 것은 silent renewal이 실패하고 provider가 interactive auth를 강제할 때만 허용한다.

**Architecture:** 현재 앱은 login WebView와 hidden collector WebView가 같은 앱 WebView profile, `CookieManager`, DOM storage를 사용하도록 구성되어 있다. 그러나 hidden collector가 로그인 페이지를 만나면 즉시 실패하고, 이전 usage가 있으면 `STALE`로 저장되어 자동 refresh loop가 계속 `/login`을 조회한다. 새 구조는 refresh를 `warm-up -> collect -> silent recovery -> interactive fallback` 단계로 나눈다.

**Tech Stack:** Kotlin, Android WebView, CookieManager, Compose AndroidView, foreground refresh loop, JUnit.

## UX Principle

사용자 기준:

- 한 번 로그인하면 끝이어야 한다.
- 사용량 수집은 앱이 조용히 유지해야 한다.
- 세션 만료를 매번 사용자에게 떠넘기면 제품 UX가 깨진다.
- 다시 로그인 요청은 마지막 수단이다.

엔지니어링 기준:

- provider가 refresh 가능한 cookie/session을 남겨둔 경우 hidden WebView가 조용히 회복해야 한다.
- provider가 보안 정책으로 interactive login을 강제하면 앱이 몰래 뚫을 수 없다.
- 이 경우에도 즉시 큰 에러를 띄우지 말고, 이전 usage를 유지하고 조용한 reconnect cue를 보여준다.

Android 제약:

- 사용자가 앱을 닫아도 정확히 1분 단위로 계속 수집하려면 foreground service 또는 동등한 실행 보장이 필요하다.
- 완전한 invisible background work만으로 1분 주기를 보장하는 것은 Android 정책상 신뢰하기 어렵다.
- Store APK도 usage service/notification/foreground refresh source를 갖고 있으므로, 제품적으로는 "앱 화면을 열 필요 없음"에 가깝게 설계하고 시스템 실행 제약은 foreground service로 흡수한다.

## Current Evidence

현재 앱에서 확인된 사실:

- `WebLoginActivity`는 login 성공 시 `CookieManager.getInstance().flush()`를 호출한다.
- `BackgroundProviderWebCollector`는 hidden 1dp WebView를 만들고 `CookieManager.setAcceptCookie(true)`, third-party cookie, DOM storage를 켠다.
- `ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS`는 60초다.
- `AIUsageAppShell`은 60초 loop에서 `ProviderRefreshPlan.automaticJobsFor(...)`를 요청한다.
- hidden refresh 시작 URL은 이미 로그인 URL이 아니다.
- 문제는 login redirect를 만났을 때 즉시 실패 처리하고, 그 실패가 `STALE`로 저장되어 자동 refresh 대상에 계속 남는 것이다.

Store APK 분석 기준:

- Store APK는 provider별 foreground usage service와 offscreen WebView collector를 사용한다.
- Store APK collector plaintext는 아직 확보되지 않았다.
- 이 계획은 Store APK collector parity 완료가 아니라 current app의 session continuity UX 개선이다.
- hardcoded usage value, preview/sample usage, fake fallback은 사용하지 않는다.

## Desired Flow

정상 refresh:

1. Automatic refresh loop가 60초마다 connected provider job을 만든다.
2. Hidden WebView가 provider별 warm-up URL을 로드한다.
3. WebView cookie/session이 유지된 상태에서 authenticated page에 도달한다.
4. collector를 주입한다.
5. trusted usage payload가 오면 repository에 저장하고 cookie를 flush한다.

세션이 약해진 refresh:

1. Hidden WebView가 login/auth URL로 리다이렉트된다.
2. 즉시 사용자에게 다시 로그인하라고 하지 않는다.
3. 같은 request 안에서 silent session recovery를 제한 횟수만큼 시도한다.
4. recovery URL은 provider root 또는 기존 authenticated target이다.
5. recovery 후 authenticated page에 도달하면 collector를 실행한다.
6. 성공하면 사용자는 아무 것도 보지 않는다.

interactive auth가 필요한 경우:

1. silent recovery가 제한 횟수만큼 실패한다.
2. 이전 usage lines는 유지한다.
3. 내부 상태는 `INTERACTIVE_AUTH_REQUIRED` 또는 `SESSION_EXPIRED` fallback으로 저장한다.
4. 자동 refresh 대상에서는 제외한다.
5. UI는 큰 실패가 아니라 "연결 확인 필요" 수준의 reconnect action을 제공한다.

## State Model

권장 내부 상태:

- `CONNECTED`: 최근 trusted usage 수집 성공.
- `COLLECTING`: 현재 수집 중.
- `STALE`: 세션은 아직 유효할 수 있으나 network/parser/collector transient failure 발생.
- `SESSION_RECOVERING`: hidden WebView가 login redirect를 보고 silent recovery 중.
- `INTERACTIVE_AUTH_REQUIRED`: silent recovery 실패. 사용자 action 필요.

대안:

- enum 증가를 줄이고 싶으면 `SESSION_RECOVERING`은 UI snapshot state로 저장하지 않고 collector 내부 request state로만 둔다.
- persisted state는 `INTERACTIVE_AUTH_REQUIRED` 하나만 추가해도 충분하다.
- 기존 계획의 `SESSION_EXPIRED` 명칭을 유지한다면 UI에는 이 문구를 직접 노출하지 않는다.

Recommended:

- persisted state: `INTERACTIVE_AUTH_REQUIRED`
- collector-only state: `SESSION_RECOVERING`
- `SESSION_EXPIRED`라는 사용자-facing wording은 쓰지 않는다.

## Implementation Tasks

### 1. Add testable session continuity policy

- [ ] Add `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderSessionContinuityPolicy.kt`.
- [ ] Add `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/providers/ProviderSessionContinuityPolicyTest.kt`.

Implementation intent:

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId

object ProviderSessionContinuityPolicy {
    const val MAX_SILENT_RECOVERY_ATTEMPTS = 2

    fun warmupUrl(providerId: ProviderId): String = when (providerId) {
        ProviderId.CLAUDE -> "https://claude.ai/"
        ProviderId.CODEX -> "https://chatgpt.com/"
        ProviderId.COPILOT -> "https://github.com/settings/copilot"
        ProviderId.CURSOR -> "https://cursor.com/dashboard"
        ProviderId.GEMINI -> ""
    }

    fun recoveryUrl(providerId: ProviderId, attempt: Int): String = when (providerId) {
        ProviderId.CLAUDE -> "https://claude.ai/"
        ProviderId.CODEX -> "https://chatgpt.com/"
        ProviderId.COPILOT -> if (attempt == 0) "https://github.com/" else "https://github.com/settings/copilot"
        ProviderId.CURSOR -> if (attempt == 0) "https://cursor.com/" else "https://cursor.com/dashboard"
        ProviderId.GEMINI -> ""
    }
}
```

Tests:

- [ ] warm-up URLs never contain `/login` or `/auth/login`.
- [ ] Copilot recovery first hits GitHub root, then Copilot settings.
- [ ] max recovery attempts is bounded.

### 2. Use continuity policy in refresh plan

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshPlan.kt`.
- [ ] Replace private duplicated hidden collector URL mapping with `ProviderSessionContinuityPolicy.warmupUrl(providerId)`.
- [ ] Keep Gemini as `NATIVE_API`.

Expected behavior:

```kotlin
private fun hiddenCollectorUrl(providerId: ProviderId): String =
    ProviderSessionContinuityPolicy.warmupUrl(providerId)
```

Verification:

- [ ] `ProviderRefreshPlanTest` still proves hidden collector start URLs are non-login URLs.
- [ ] Add assertion that every hidden Web collector provider has a non-blank warm-up URL.

### 3. Add typed refresh failure, but keep it UX-neutral

- [ ] Add `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshFailure.kt`.

Implementation intent:

```kotlin
package com.aiusage.mobile.providers

enum class ProviderRefreshFailureKind {
    TRANSIENT_PAGE_LOAD,
    TRANSIENT_HTTP,
    TIMEOUT,
    COLLECTOR_ERROR,
    NO_TRUSTED_PAYLOAD,
    INTERACTIVE_AUTH_REQUIRED,
}

data class ProviderRefreshFailure(
    val kind: ProviderRefreshFailureKind,
    val message: String,
)
```

Message rule:

- Internal logs may say login/auth redirect.
- User-facing message should be softer: `Connection needs attention. Reconnect when convenient.`
- Korean UI copy can be: `연결 확인이 필요합니다.`

### 4. Change hidden collector from fail-fast to silent recovery

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/BackgroundProviderWebCollector.kt`.
- [ ] Change `onError` callback to receive `ProviderRefreshFailure`.
- [ ] Track silent recovery attempts per request ID.
- [ ] When `ProviderWebCollectorScripts.isRefreshLoginPage(providerId, url)` is true, try recovery before terminal failure.

Implementation intent:

```kotlin
private val recoveryAttemptsByRequestId = mutableMapOf<Long, Int>()
```

```kotlin
private fun handleLoginRedirectDuringRefresh(view: WebView, job: QueuedProviderRefreshJob): Boolean {
    val attempts = recoveryAttemptsByRequestId[job.requestId] ?: 0
    if (attempts < ProviderSessionContinuityPolicy.MAX_SILENT_RECOVERY_ATTEMPTS) {
        recoveryAttemptsByRequestId[job.requestId] = attempts + 1
        CookieManager.getInstance().flush()
        view.loadUrl(
            ProviderSessionContinuityPolicy.recoveryUrl(
                providerId = job.job.providerId,
                attempt = attempts,
            ),
        )
        return true
    }
    return false
}
```

```kotlin
if (ProviderWebCollectorScripts.isRefreshLoginPage(providerId, url)) {
    if (handleLoginRedirectDuringRefresh(view, job)) return
    finishWithErrorOnce(
        job = job,
        failure = ProviderRefreshFailure(
            kind = ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED,
            message = "Connection needs attention. Reconnect when convenient.",
        ),
    )
    return
}
```

Cleanup:

- [ ] Remove `recoveryAttemptsByRequestId[job.requestId]` when request finishes.
- [ ] Keep timeout bounded so recovery cannot loop forever.
- [ ] Flush cookies after successful trusted payload.

### 5. Persist interactive-required only after recovery fails

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`.
- [ ] Add `INTERACTIVE_AUTH_REQUIRED` to `ProviderConnectionState`.
- [ ] Update primary action so this state offers connect/reconnect.
- [ ] Add helper preserving previous usage lines.

Implementation intent:

```kotlin
fun interactiveAuthRequiredKeepingPrevious(
    providerId: ProviderId,
    previous: ProviderUsageSnapshot?,
    message: String,
): ProviderUsageSnapshot {
    val now = Instant.now().toString()
    return previous?.copy(
        connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
        refreshState = ProviderRefreshState.IDLE,
        updatedAt = now,
        message = message,
    ) ?: disconnected(providerId).copy(
        connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
        refreshState = ProviderRefreshState.IDLE,
        updatedAt = now,
        message = message,
    )
}
```

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`.

```kotlin
fun markInteractiveAuthRequired(providerId: ProviderId, message: String) {
    updateSnapshot(providerId) { previous ->
        ProviderUsageSnapshot.interactiveAuthRequiredKeepingPrevious(
            providerId = providerId,
            previous = previous,
            message = message,
        )
    }
}
```

### 6. Keep automatic refresh active for recoverable states

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/sync/ForegroundRefreshPolicy.kt`.

Policy:

- Include `CONNECTED`, `COLLECTING`, `STALE`.
- Exclude `INTERACTIVE_AUTH_REQUIRED`.
- Do not persist `SESSION_RECOVERING` unless the UI needs it.

Reason:

- `STALE` is still useful for transient failure retry.
- `INTERACTIVE_AUTH_REQUIRED` means hidden recovery already failed.

### 7. Route shell failures by kind

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`.
- [ ] In collector `onError`, route typed failures.

Implementation intent:

```kotlin
when (failure.kind) {
    ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED -> {
        localUsageRepository.markInteractiveAuthRequired(
            providerId = queuedJob.job.providerId,
            message = failure.message,
        )
    }
    else -> {
        localUsageRepository.failKeepingPrevious(
            providerId = queuedJob.job.providerId,
            message = failure.message,
        )
    }
}
finishProviderRefreshSurfaces()
```

### 8. Make manual refresh user-friendly

- [ ] Update manual refresh path in `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`.

Behavior:

- If provider is `INTERACTIVE_AUTH_REQUIRED`, tapping refresh should open the login/connect flow.
- If provider is `STALE`, tapping refresh should still attempt hidden refresh.
- If hidden refresh silently recovers, the user never sees login.

Implementation intent:

```kotlin
if (snapshot.connectionState == ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED) {
    connectProvider(providerId)
    return
}
```

### 9. UI wording

- [ ] Search UI rendering for snapshot messages and provider card action text.
- [ ] Do not expose `session expired` as the default user-facing phrase.
- [ ] Preferred short copy:
  - Korean: `연결 확인 필요`
  - English fallback: `Connection needs attention`
- [ ] Preserve previous usage lines and last-updated time.
- [ ] Show reconnect as a small action, not a blocking full-screen failure.

### 10. Tests

- [ ] `ProviderSessionContinuityPolicyTest`
  - warm-up URLs are non-login.
  - recovery attempts are bounded.
  - Copilot recovery order is GitHub root then Copilot settings.
- [ ] `ProviderRefreshPlanTest`
  - automatic hidden jobs use warm-up URLs.
  - no hidden job starts at a login URL.
- [ ] `ProviderUsageSnapshotTest`
  - interactive auth required keeps previous usage lines.
  - primary action is connect/reconnect.
- [ ] `ForegroundRefreshPolicyTest`
  - `STALE` remains refreshable.
  - `INTERACTIVE_AUTH_REQUIRED` is not auto-refreshed.
- [ ] `ProviderSnapshotCodecTest`
  - `INTERACTIVE_AUTH_REQUIRED` round-trips with previous lines.
- [ ] Add focused test around extracted recovery policy logic rather than trying to unit-test Android `WebViewClient` directly.

Commands:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderSessionContinuityPolicyTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderRefreshPlanTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderUsageSnapshotTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.sync.ForegroundRefreshPolicyTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderSnapshotCodecTest"
.\gradlew.bat :app:testDebugUnitTest
```

### 11. Runtime validation

- [ ] Login once to Claude, Codex, and Copilot through in-app WebView.
- [ ] Confirm usage is collected once.
- [ ] Leave app running long enough to trigger multiple 60-second refreshes.
- [ ] Confirm logs show warm-up and collector success without visible login.
- [ ] Force a weak session case by clearing only short-lived provider session state if possible while preserving broader WebView profile.
- [ ] Confirm hidden recovery attempts occur before any user-facing reconnect state.
- [ ] Fully sign out from the provider.
- [ ] Confirm hidden recovery fails boundedly and UI moves to `연결 확인 필요`.
- [ ] Reconnect once and confirm automatic 60-second collection resumes.

Do not record raw tokens, cookies, auth headers, account IDs, OAuth codes, emails, usernames, or full provider HTML in runtime docs.

### 12. Documentation updates after implementation

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`.
- [ ] Record this as current-app UX mitigation, not Store APK collector parity completion.
- [ ] Update `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-analysis-journal-2026-05-20.md` with test/runtime results.
- [ ] Update `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-context-handoff-2026-05-20.md` only if the APK analysis next action changes.

## Design Notes

- The user-facing goal is not "detect session expired"; it is "avoid making the user care about session expiry."
- Silent recovery should happen inside the same hidden WebView profile that collector uses.
- `INTERACTIVE_AUTH_REQUIRED` exists because some providers intentionally require real user interaction after risk checks, password changes, two-factor prompts, or revoked sessions.
- That fallback state should be rare, quiet, and recoverable.
- Store APK final parity still depends on decrypted collector/runtime evidence.

## Self-Review

- UX coverage: login once, hidden session maintenance, 60-second collection, quiet fallback.
- Privacy: no secret values or raw provider account artifacts are written.
- Store APK boundary: does not claim decrypted collector parity.
- Testability: policy and state transitions are unit-testable without Android WebView instrumentation.
- Android realism: notes that strict 1-minute background collection requires execution guarantees such as foreground service.
