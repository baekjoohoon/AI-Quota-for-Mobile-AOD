# Ultraresearch Synthesis: GLM 장기 세션/성능 문제 해결안

## Executive Summary

GLM 문제의 핵심은 timeout 길이가 아니라 프로세스 경계입니다. GLM 로그인 Activity와 GLM isolated WebView Service는 `:glm_webview` 프로세스에서 실행되고, 여기서 얻은 z.ai 쿠키를 `SharedPreferences` 기반 `SecureStringStore`에 저장합니다. 하지만 메인 refresh는 메인 프로세스에서 같은 값을 읽습니다. Android WebView 데이터는 `setDataDirectorySuffix()` 사용 시 프로세스별로 분리되며, Android `SharedPreferences`도 멀티프로세스 사용을 지원하지 않습니다. 따라서 현재 direct cookie refresh가 계속 `glm_web_cookie_missing`으로 실패하는 구조는 우연이 아니라 설계상 취약점입니다.

확실한 해결은 GLM Web OAuth refresh를 매번 isolated WebView fallback으로 보내지 않는 것입니다. isolated WebView가 payload를 얻었을 때 cookie header를 `ResultReceiver` 결과에 같이 실어 메인 프로세스로 반환하고, 메인 프로세스가 직접 `GlmWebSessionCookieStore`에 저장해야 합니다. 로그인 성공 경로도 같은 방식으로 메인 프로세스 저장을 보장해야 합니다.

## Findings

- `GlmWebLoginActivity`와 `GlmIsolatedWebSessionService`는 둘 다 `:glm_webview`에서 실행됩니다. 그래서 이 둘이 저장한 SharedPreferences 값을 메인 refresh가 즉시/항상 볼 수 있다고 가정하면 안 됩니다.
- Android WebView 문서는 여러 프로세스 WebView가 웹 데이터를 직접 공유하지 않으며 쿠키를 프로세스 간 직접 복사해야 한다고 설명합니다.
- Android SharedPreferences 문서는 멀티프로세스 사용을 지원하지 않는다고 명시합니다.
- 15:14 monitor log는 GLM이 payload를 얻고 cookie를 저장했다고 로그를 남긴 직후에도 다음 cycle에서 main process direct refresh가 `glm_web_cookie_missing`을 낸 것을 보여줍니다.
- 40분 실패는 GLM 세션 만료가 아니라 `usage_data_glm.xml` 읽기 실패입니다. 해당 시점은 APK 재설치 전입니다.

## Recommended Fix

1. `GlmIsolatedUsageResult.Payload`에 `cookieHeader` 필드를 추가한다.
2. `GlmIsolatedWebSessionService.completePayload()`가 `CookieManager`에서 얻은 GLM cookie header를 `EXTRA_COOKIE_HEADER`로 결과 bundle에 포함한다.
3. `GlmIsolatedWebSession.usageResultFor()`가 payload와 cookie header를 같이 반환한다.
4. `ProviderBackgroundRefreshService.collectWebProviderUsage()`의 GLM branch에서 isolated payload를 받으면 즉시 메인 프로세스에서 `GlmUsageRepository.saveWebSessionCookieHeader(cookieHeader)`를 호출한다.
5. GLM interactive login 성공 경로도 쿠키 저장을 isolated process 내부 `SharedPreferences`에 맡기지 않는다. 가장 작은 안정안은 로그인 성공 후 main-process `ProviderUsageCollectionService`에 raw payload와 cookie header를 같이 넘겨 main process에서 저장하는 것이다.
6. `ProviderScopedStateRepository.saveSnapshot()`의 provider scoped usage write는 최소한 GLM에 대해 `commit()`으로 바꾸거나, 모든 provider에 영향이 부담되면 GLM usage snapshot만 별도 synchronous save를 적용한다.
7. monitor script는 product 상태 판정 시 main XML이 없고 `.bak`가 있으면 `.bak`를 읽는 복구 경로를 둔다. 이건 테스트 도구 보강이고 앱 기능 대체가 아니다.

## Non-Fixes

- GLM timeout을 늘리는 것은 확실한 해결이 아니다. fallback WebView가 더 오래 붙잡힐 뿐, direct cookie path 실패는 그대로다.
- GLM refresh 한 턴을 쉬는 cooldown도 확실한 해결이 아니다. 부하 빈도만 줄이고 root cause는 남긴다.
- isolated process kill만 강화하는 것도 충분하지 않다. 매 cycle isolated WebView를 띄우는 구조 자체가 문제다.

## Verification Criteria

- GLM Web OAuth 로그인 직후 main process 로그에서 `glmWebSessionDirectRefresh payload=true`가 반복되어야 한다.
- 120분 감시 동안 `glm_web_cookie_missing`이 첫 fallback 이후 반복되면 실패로 본다.
- GLM refresh cycle에서 `Start proc ... :glm_webview`가 매분 반복되지 않아야 한다.
- GLM timeout이 발생해도 기존 snapshot은 유지되고 connection state는 끊김으로 바뀌면 안 된다.
- 60분 이후 APK reinstall check에서도 GLM direct path 또는 1회 fallback 후 direct path 복구가 확인되어야 한다.
