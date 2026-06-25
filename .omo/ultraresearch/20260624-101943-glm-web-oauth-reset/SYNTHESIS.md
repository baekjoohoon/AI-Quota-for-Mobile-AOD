# Ultraresearch Synthesis: GLM Web OAuth 5-hour Reset

## Executive Summary

GLM Web OAuth can collect the 5-hour reset time if the collector captures the same internal quota API response used by the z.ai subscription UI, or if it can read the Web OAuth frontend token and call that endpoint in page context. The reset does not come from visible DOM text.

The relevant endpoint is `https://api.z.ai/api/monitor/usage/quota/limit`. The important field is `data.limits[].nextResetTime` on a `TOKENS_LIMIT` item with `unit: 3, number: 5`.

## Codebase Findings

- Web OAuth GLM collector already hooks `fetch` and XHR and scans `/api/monitor/usage` responses.
- It already copies reset fields into normalized output when hidden JSON contains reset metadata.
- Visible text parsing does not produce reset fields.
- Current API Key mode snapshot already proves the normalizer stores `nextResetTime` as `resetsAt`.

## Runtime Findings

- Current emulator is in API Key mode, not Web OAuth mode.
- Current isolated GLM WebView storage does not contain the known Web OAuth localStorage token key.
- Therefore this run could not live-prove Web OAuth token reuse without switching modes.

## External Findings

- Z.AI FAQ confirms GLM Coding has 5-hour and weekly quotas.
- OpenUsage documents the quota endpoint, `nextResetTime`, and claims it works with OAuth tokens and API keys.
- A public bookmarklet example uses `localStorage` token `z-ai-open-platform-token-production` to call the same endpoint and read `nextResetTime`.

## Conclusion

The practical Web OAuth solution is not to parse visible DOM for reset time. It is to capture or invoke the internal quota API in the authenticated z.ai page context, then use the `TOKENS_LIMIT` item with `unit: 3, number: 5` and its `nextResetTime`.

If z.ai stops exposing the token in WebView localStorage or the page no longer calls the quota endpoint, Web OAuth reset collection will fail unless the collector can observe the page's own network response.

