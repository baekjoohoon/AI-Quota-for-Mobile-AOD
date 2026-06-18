# Claude JS Extractor Flow

현재 Claude extractor는 API 우선 수집에 DOM, page-state, network row fallback을 섞는 방식이다.

## 전체 순서

1. Claude용 JS 선택

   `ProviderScriptProviders.kt`의 `ClaudeScriptProvider`가 `ProviderWebCollectorScripts.claude()`를 반환한다.

2. JS 삽입 조건 확인

   `ProviderWebCollectorScripts.shouldRunCollector()`에서 다음 조건을 만족해야 한다.

   - host가 `claude.ai` 또는 하위 host
   - `login`, `logout` 경로가 아님
   - `lastActiveOrg` 쿠키가 있거나, path가 `/` 또는 `/new`이거나, 페이지 텍스트에 `claude`가 포함됨

3. JS 실행 시작

   삽입된 JS는 약 1.2초 후 `runProbe()`를 실행하고, 내부에서 `probeClaudeSession()`을 호출한다.

4. 사용량 1차 수집: page-state, DOM, network row scan

   먼저 브라우저 전역 상태와 수집된 network row를 훑는다.

   - `window.__NEXT_DATA__`
   - `window.__remixContext`
   - `window.__reactRouterContext`
   - `window.__APP_DATA__`
   - `window.__INITIAL_STATE__`
   - fetch/XHR hook으로 쌓은 Claude network row
   - collector 공통 row

   여기서 다음 usage 키를 찾는다.

   - `five_hour`
   - `seven_day`
   - `seven_day_omelette`
   - `session`
   - `weekly`
   - `opus`
   - `sonnet`
   - `cowork`
   - `design`

5. plan명 1차 수집: 화면 텍스트

   페이지 텍스트에서 `Claude Free`, `Claude Pro`, `Claude Max`, `Claude Team`, `Claude Enterprise` 형태를 찾는다.

   또한 `plan`, `subscription`, `membership`, `요금제`, `플랜` 주변의 plan 텍스트도 정규식으로 찾는다.

6. 조직/계정 API 조회

   다음 순서로 Claude API를 호출한다.

   1. `https://claude.ai/api/organizations`
   2. orgId가 없으면 `https://claude.ai/api/organizations/me`

   여기서 `orgId`, `account`, `plan` 후보를 얻는다.

7. plan명 2차 수집: organization/subscription JSON

   JSON에서 다음 직접 키를 찾는다.

   - `plan`
   - `plan_name`
   - `planName`
   - `plan_type`
   - `planType`
   - `subscription_plan`
   - `subscriptionPlan`
   - `tier`
   - `membershipType`
   - `product_name`
   - `productName`

   그리고 다음 container를 재귀 탐색한다.

   - `subscription`
   - `subscription_details`
   - `subscriptionDetails`
   - `organization`
   - `billing`
   - `plan_info`
   - `planInfo`

8. subscription API 조회

   orgId가 있으면 다음 API를 호출한다.

   ```text
   https://claude.ai/api/organizations/{orgId}/subscription_details
   ```

   응답 JSON에서도 plan명을 다시 찾는다.

9. 사용량 API 조회

   orgId가 있으면 다음 API를 호출한다.

   ```text
   https://claude.ai/api/organizations/{orgId}/usage
   ```

   앞 단계에서 usage를 못 찾았고 이 응답이 성공하면, 해당 JSON을 `usage`로 사용한다.

10. payload 전송

    다음 조건을 모두 만족할 때만 native bridge로 payload를 보낸다.

    - `result.loggedIn == true`
    - `result.usageOk == true`
    - `hasClaudeUsagePayload(result.usage) == true`

    payload 형태:

    ```json
    {
      "provider": "claude",
      "account": "...",
      "plan": "...",
      "organizationId": "...",
      "usage": {}
    }
    ```

11. Kotlin 정규화

    `ProviderUsageNormalizer.normalizeClaude()`에서 raw payload를 앱 표시용 라인으로 바꾼다.

    - `five_hour` 또는 `session` -> `Claude Session`
    - `seven_day` 또는 `weekly` -> `Claude Weekly`
    - `opus` -> `Claude Opus`
    - `sonnet` -> `Claude Sonnet`
    - `cowork` -> `Claude Cowork`
    - `seven_day_omelette` 또는 `design` -> `Claude Design`

## 퍼센트와 reset 처리

Claude 라인은 `preferRemainingPercent = true`로 정규화된다.

우선 remaining 계열 값을 사용한다.

- `remaining_percent`
- `remainingPercent`
- `remainingPercentage`
- `remaining_percentage`
- `percent_remaining`
- `percentRemaining`
- `remainingFraction`
- `remaining_fraction`

remaining 계열이 없으면 used 계열 값을 사용한다.

- `used_percent`
- `usedPercent`
- `usedPercentage`
- `used_percentage`
- `percent_used`
- `totalPercentUsed`
- `total_percent_used`
- `utilization`
- `u`

reset은 다음 키에서 가져온다.

- `resets_at`
- `reset_at`
- `resetAt`
- `r`
- `reset_text`
- `resetText`
- `t`
- `reset_after_seconds`

## 핵심 요약

- plan명은 화면 텍스트, organizations JSON, subscription_details JSON 순서로 보강된다.
- 사용량은 page-state/network scan을 먼저 보고, 실패하면 `/usage` API 응답을 사용한다.
- native로 넘어온 뒤에는 Kotlin normalizer가 Claude 전용 usage key를 앱의 usage line으로 매핑한다.
