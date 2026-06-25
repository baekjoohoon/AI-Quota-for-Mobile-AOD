# Android Provider Code Review

codeQualityStatus: BLOCK
recommendation: REQUEST_CHANGES
reportPath: .omo/evidence/android-provider-code-review.md

## 검토 범위

- Workspace: `D:/Vibe Project/AI Usage for Mobile`
- Production 21 reference: `C:/Users/datell1357/AppData/Local/Temp/aiquota-21-jadx`
- 확인한 변경 파일: `ProviderRefreshPlan.kt`, `ProviderWebCollectorScripts.kt`, 관련 provider 테스트
- 사용자 지정 제외: GLM/OpenCode provider 추가, `ProviderSessionResetter`, `ProviderWebSessionCleaner`, `ProviderWebSessionClearPolicy`, settings disconnect/session-clear UI 동작, 이미 수락된 broad Gemini migration
- 앱/소스 수정 없음. 이 파일은 리뷰 artifact만 기록한다.

## Skill Perspective Check

- `remove-ai-slops` 로드 완료: 테스트와 프로덕션 코드에서 deletion-only/요청 제거 검증, tautological test, 구현 상수 복제, 불필요한 parsing/normalization을 점검했다.
- `programming` 로드 완료: Kotlin 전용 reference는 없었지만, 일반 기준인 brittle implementation-mirroring tests, needless abstraction, parse/validate boundary 원칙, untyped escape hatch 관점으로 적용했다.
- 위 관점 위반 있음: 새 테스트 일부가 실제 provider behavior가 아니라 함수명/문자열 부재를 검증한다. 프로덕션 코드에서는 reset text 결여를 신뢰도 실패로 취급하는 새 guard가 production 21보다 보수적으로 valid usage payload를 버릴 수 있다.

## 검증

- `git status --short`: provider/session 관련 8개 파일 modified, `.omo/` untracked.
- `git diff --stat`: 8 files changed, 285 insertions(+), 51 deletions(-).
- Production 21 비교:
  - `C:/Users/datell1357/AppData/Local/Temp/aiquota-21-jadx/sources/com/aiquota/mobile/providers/G0.java:110`은 Codex hidden collector URL을 `https://chatgpt.com/`로 반환한다.
  - `C:/Users/datell1357/AppData/Local/Temp/aiquota-21-jadx/sources/com/aiquota/mobile/providers/f1.java:108`의 Codex collector 문자열에는 `codexVisibleActiveLineMissingReset`가 없고, `if (primary) limits.primary_window = primary;`, `if (sparkPrimary) limits.spark_primary_window = sparkPrimary;`, `return extractCodexVisibleDomUsage(accountId);`가 존재한다.
  - 같은 production 21 문자열에는 `function hasCodexNavigationAuth`와 `missing_navigation_auth`가 없다.
- 테스트 실행: `gradle :app:testDebugUnitTest --tests "com.aiquota.mobile.providers.ProviderWebCollectorScriptsTest" --tests "com.aiquota.mobile.providers.ProviderRefreshPlanTest"` from `android/` passed, `BUILD SUCCESSFUL in 10s`.

## CRITICAL

- 없음.

## HIGH / Important

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1654`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1683`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1689`  
  Codex visible DOM collection now returns `null` whenever primary or spark primary line has `remaining_percent < 99.999` but no `reset_text`. This is stricter than production 21, which accepted the same primary/spark primary usage lines without requiring reset text (`f1.java:108`). In the current flow, a `null` visible DOM payload means `scanCodexPageState` has no usage (`ProviderWebCollectorScripts.kt:1825`), and a valid logged-in Codex page can end in `codex_usage_unavailable` (`ProviderWebCollectorScripts.kt:2009`). The new test `ProviderWebCollectorScriptsTest.kt:3098` explicitly locks this rejection for a plausible page with `Codex 5 hour usage limit`, `84% left`, and weekly reset text. That is a concrete regression risk against production 21 behavior: existing Codex sessions whose UI omits or hides the 5h reset text stop producing usage even though visible usage percentages are present.

## MEDIUM

- `android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebCollectorScriptsTest.kt:2296`, `android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebCollectorScriptsTest.kt:2302`, `android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebCollectorScriptsTest.kt:2433`, `android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebCollectorScriptsTest.kt:2580`  
  새 테스트 일부가 behavior 대신 구현 문자열 부재를 검증한다. `extractCodexUsageFromRows(accountId)`, `hasCodexNavigationAuth`, `missing_navigation_auth`가 없다는 사실만으로는 navigation/auth/session behavior가 안전하다는 것을 보장하지 않는다. `remove-ai-slops` 관점에서는 요청된 제거를 확인하는 deletion-only/implementation-mirroring test이고, `programming` 관점에서는 brittle prompt/string test에 가깝다. 같은 파일의 Node runtime tests는 더 의미 있지만, 이 문자열 검증들은 false confidence와 유지보수 비용을 만든다.

## LOW / Minor

- 없음.

## Non-Findings

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt:100`의 Codex hidden start URL `https://chatgpt.com/`은 production 21 `G0.java:110`과 일치하므로 회귀로 보지 않았다.
- `ProviderWebCollectorScripts.kt:1825`의 `scanCodexPageState`가 visible DOM을 반환하는 방향 자체는 production 21 `f1.java:108`과 일치하므로, row fallback 제거 자체는 별도 finding으로 보지 않았다.
- `hasCodexNavigationAuth`/`missing_navigation_auth` 제거도 production 21 문자열에 없는 동작과 일치하므로, 프로덕션 코드 회귀로 보지 않았다.

## Blockers

- Codex visible DOM에서 active 5h/spark 5h reset text가 없다는 이유만으로 전체 payload를 버리는 guard를 재검토해야 한다. Production 21처럼 reset text는 optional field로 두거나, 적어도 percentage가 있는 valid usage payload는 저장하면서 reset 누락은 diagnostics로만 남기는 쪽이 기존 provider collection behavior를 덜 깨뜨린다.
