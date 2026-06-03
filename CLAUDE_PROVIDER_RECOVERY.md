# Claude Provider Recovery Notes

理쒖쥌 ?뺣━ 湲곗?: 2026-05-19

??臾몄꽌??Claude provider???꾩옱 濡쒓렇?? ?몄뀡 寃利? ?ъ슜???섏쭛, 以묐났 諛⑹? 援ы쁽 ?곹깭瑜?蹂듦뎄?⑹쑝濡??뺣━??臾몄꽌??
Claude 愿??肄붾뱶媛 瑗ъ씠硫???臾몄꽌? `PROVIDER_USAGE_COLLECTION.md`??Claude ?뱀뀡??癒쇱? 鍮꾧탳?쒕떎.

## ?꾩옱 ?곹깭

- Claude reset timer 臾몄젣???닿껐?먮떎.
- reset 媛믪? `https://claude.ai/api/organizations/{organizationId}/usage`??`resets_at`??湲곗??쇰줈 ?쒖떆?쒕떎.
- `由ъ뀑 ??대㉧ ?湲?以?? ?ㅼ젣 reset 媛믪씠 ?녿뒗 line?먮쭔 ?쒖떆?댁빞 ?쒕떎.
- Claude 濡쒓렇?몄? WebView ?대? Google OAuth瑜??ъ슜?쒕떎.
- Google OAuth ?댄썑 Claude媛 `/login`??癒몃Ъ嫄곕굹 ??shell濡???쾶 ?섏뼱媛??寃쎌슦媛 ?덉쑝誘濡?URL/DOM留뚯쑝濡??꾨즺 ?먮떒?섏? ?딅뒗??
- Claude ?ъ슜??line 以묐났? canonical quota key濡??쒓굅?쒕떎.

## 愿???뚯씪

| ??븷 | ?뚯씪 |
| --- | --- |
| 濡쒓렇??WebView 諛?Claude ?몄뀡 寃利?| `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt` |
| 濡쒓렇??transient error 蹂듦뎄 ?뺤콉 | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderLoginRecoveryPolicy.kt` |
| Claude 濡쒓렇??URL rewrite | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderLoginUrlRewriter.kt` |
| Claude 濡쒓렇?????몄뀡 ?뺣━ | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderLoginSessionPreparer.kt` |
| Claude direct usage API ?섏쭛 | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt` |
| Claude usage API payload ?뚯떛 | `android/app/src/main/java/com/aiquota/mobile/providers/TextUsageExtractor.kt` |
| Claude line dedupe 怨듯넻 濡쒖쭅 | `android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt` |
| ??μ냼 read/write normalization | `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt` |
| Claude/WebLogin ?⑥쐞 ?뚯뒪??| `android/app/src/test/java/com/aiquota/mobile/providers/WebLoginActivityTest.kt` |
| provider model/dedupe ?⑥쐞 ?뚯뒪??| `android/app/src/test/java/com/aiquota/mobile/local/ProviderModelsTest.kt` |
| ?섏쭛 ?쒕퉬???⑥쐞 ?뚯뒪??| `android/app/src/test/java/com/aiquota/mobile/providers/ProviderUsageCollectionServiceTest.kt` |

## 濡쒓렇???먮쫫

```text
Claude ?곌껐 踰꾪듉
  -> WebLoginActivity
  -> ProviderLoginSessionPreparer.prepare(CLAUDE)
  -> Claude 荑좏궎/WebStorage 以?sessionKey, lastActiveOrg, activitySessionId ?뺣━
  -> cf_clearance, __cf_bm 蹂댁〈
  -> https://claude.ai/ 濡쒕뱶
  -> Google OAuth ?쒖옉 URL 媛먯?
  -> ProviderLoginUrlRewriter媛 prompt=select_account 異붽?
  -> Google 怨꾩젙 ?좏깮/濡쒓렇??  -> Google OAuth main-frame transient error 諛쒖깮 ??理쒕? 2??https://claude.ai/ 濡?蹂듦뎄 濡쒕뱶
  -> Claude host濡?蹂듦?
  -> /api/organizations, /api/organizations/me ?몄뀡 寃利?  -> organization id ?뺤씤
  -> lastActiveOrg 荑좏궎 蹂닿컯 ???  -> ?깆쑝濡?蹂듦?
  -> ProviderUsageCollectionService媛 background usage ?섏쭛
```

## 濡쒓렇???꾨즺 ?먮떒 ?곗꽑?쒖쐞

1. WebView ?대? fetch濡?`/api/organizations` ?먮뒗 `/api/organizations/me`媛 200?닿퀬 organization id瑜?李얠쓬
2. Claude ??寃쎈줈(`/new` ??濡??대룞?덇퀬 `lastActiveOrg` 荑좏궎媛 ?덉쓬
3. JS payload媛 `authenticatedApp=true`, `appShellConfirmed=true`, ?먮뒗 limit data瑜??쒓났??4. main frame error媛 諛쒖깮?덉?留?`lastActiveOrg` 荑좏궎媛 ?덉쓬
5. Google OAuth host?먯꽌 main frame error媛 諛쒖깮?덉?留??꾩쭅 ?몄뀡 寃利??꾩씠硫?利됱떆 ?ㅽ뙣 ??ν븯吏 ?딄퀬 `https://claude.ai/`濡?蹂듦뎄??
`/login` URL ?먯껜???꾨즺 泥섎━?섏? ?딅뒗??
`/logout` URL? ?꾨즺 泥섎━?섏? ?딅뒗??

## Claude ?몄뀡 寃利?肄붾뱶 怨꾩빟

- `WebLoginActivity.maybeVerifyClaudeSessionWithApi()`媛 Claude host?먯꽌留??ㅽ뻾?쒕떎.
- `claudeSessionVerificationScript()`??WebView origin?먯꽌 ?ㅼ쓬 endpoint瑜??쒖꽌?濡??몄텧?쒕떎.
  - `/api/organizations`
  - `/api/organizations/me`
- fetch ?듭뀡? `credentials: "include"`瑜??ъ슜?쒕떎.
- `claudeOrganizationIdFromVerificationPayload()`媛 JSON body?먯꽌 UUID ?뺤떇 organization id瑜?李얜뒗??
- organization id瑜?李얠쑝硫?`lastActiveOrg={organizationId}; Path=/; Secure; SameSite=Lax` 荑좏궎瑜???ν븳??

?뺤긽 濡쒓렇 marker:

```text
login claudeApiSessionVerified provider=claude org={organizationId}
login claudeOrgStored provider=claude
```

## ?ъ슜???섏쭛 ?먮쫫

```text
ProviderUsageCollectionService
  -> CookieManager?먯꽌 lastActiveOrg ?뺤씤
  -> https://claude.ai/api/organizations/{organizationId}/usage 濡쒕뱶
  -> TextUsageExtractor.extractClaudeUsageApiResponse()
  -> ProviderUsageLine 3媛??뺢퇋??  -> ProviderId.CLAUDE.deduplicateUsageLinesForStorage()
  -> LocalUsageRepository.saveSnapshot()
```

?뺤긽 usage line:

| API key | ?쒖떆 | window | reset |
| --- | --- | --- | --- |
| `five_hour` | Claude 5?쒓컙 ?쒕룄 | `5 hours` | `resets_at` |
| `seven_day` | Claude 二쇨컙 ?쒕룄 | `7 days` | `resets_at` |
| `seven_day_omelette` | Claude Design | `7 days` | ?놁쓣 ???덉쓬 |

?뺤긽 濡쒓렇 marker:

```text
collection claudeDirectUsageWebView
/api/organizations/{organizationId}/usage
```

## 以묐났 諛쒖깮 ?먯씤

媛숈? Claude usage API 媛믪씠 ?쒕줈 ?ㅻⅨ raw label/source ?뺥깭濡??ㅼ뼱?ㅻ㈃ 湲곗〈 dedupe key媛 ?ㅻⅤ寃?怨꾩궛?????덉뿀??

?덉떆:

```text
Five_hour | 5 hours | /api/organizations/:id/usage
Claude 5?쒓컙 ?쒕룄 | 5 hours | /api/organizations/{organizationId}/usage
```

UI?먯꽌??????`Claude 5?쒓컙 ?쒕룄`濡??쒖떆?섎?濡?以묐났泥섎읆 蹂댁씤??
?ㅼ젣 吏꾩쭨 媛믪? ??以??섎굹媛 ?꾨땲??媛숈? `/usage` 媛믪씠 ??踰???λ맂 寃껋씠??

## 以묐났 諛⑹? 怨꾩빟

Claude line? ??????ㅼ쓬 canonical key濡?dedupe?쒕떎.

| canonical key | ?덉슜 raw ?뺥깭 |
| --- | --- |
| `claude:five_hour` | `Five_hour`, `five_hour`, `Claude 5?쒓컙 ?쒕룄`, `5 hours` |
| `claude:seven_day` | `Seven_day`, `seven_day`, `Claude 二쇨컙 ?쒕룄`, `weekly`, `7 days` |
| `claude:seven_day_omelette` | `Seven_day_omelette`, `Claude Design`, `omelette`, `design` |

?숈씪 canonical key媛 ?щ윭 媛쒖씠硫????좊ː???믪? line???④릿??

?곗꽑?쒖쐞:

1. `/api/organizations/{id}/usage` source
2. `remainingPercent` ?덉쓬
3. `resetsAt` ?덉쓬
4. `resetText` ?덉쓬
5. `sourceLabel` ?덉쓬
6. confidence ?믪쓬

??濡쒖쭅? ?섏쭛 ?④퀎? ??μ냼 normalization ?④퀎 紐⑤몢?먯꽌 ?곸슜?쒕떎.
?곕씪???대? 6媛?line????λ릺???덉뼱???깆씠 snapshot???ㅼ떆 ?쎌쑝硫?3媛쒕줈 ?뺣━?섏뼱???쒕떎.

## 湲덉? ?ы빆

- Claude `/new`, `/`, 鍮?source?먯꽌 ?섏삩 `Session`, `Weekly` placeholder line????ν븯吏 ?딅뒗??
- ?뺤긽 `/usage` line??鍮?fallback?쇰줈 ??뼱?곗? ?딅뒗??
- ?ㅼ젣 `resetsAt`???덈뒗??`由ъ뀑 ??대㉧ ?湲?以??쇰줈 ?泥댄븯吏 ?딅뒗??
- `/login` URL留?蹂닿퀬 濡쒓렇???꾨즺 泥섎━?섏? ?딅뒗??
- Claude OAuth?먯꽌 Google `AccountChooser` wrapper瑜??ㅼ떆 ?ъ슜?섏? ?딅뒗??

## 寃利?紐낅졊

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest `
  --tests com.aiquota.mobile.local.ProviderModelsTest `
  --tests com.aiquota.mobile.providers.WebLoginActivityTest `
  --tests com.aiquota.mobile.providers.ProviderUsageCollectionServiceTest `
  --tests com.aiquota.mobile.providers.TextUsageExtractorTest

.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug
```

## ?먮??덉씠??snapshot ?뺤씤

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$xml = & $adb -s emulator-5556 exec-out run-as com.aiquota.mobile cat shared_prefs/ai_quota_local_usage.xml
$raw = [regex]::Match(($xml -join ''), '<string name="provider_snapshots">(.*?)</string>', 'Singleline').Groups[1].Value
$decoded = [System.Net.WebUtility]::HtmlDecode($raw)
$json = $decoded | ConvertFrom-Json
$json.providers | Where-Object providerId -eq 'claude' | ConvertTo-Json -Depth 20
```

?뺤긽 ?곹깭:

- Claude line? 3媛쒕떎.
- `Five_hour` ?먮뒗 `Claude 5?쒓컙 ?쒕룄` 怨꾩뿴??1媛쒕쭔 ?덈떎.
- `Seven_day` ?먮뒗 `Claude 二쇨컙 ?쒕룄` 怨꾩뿴??1媛쒕쭔 ?덈떎.
- `Seven_day_omelette` ?먮뒗 `Claude Design` 怨꾩뿴??1媛쒕쭔 ?덈떎.
- 5?쒓컙/二쇨컙 line? ?ㅼ젣 `resetsAt`???덉뼱???쒕떎.
