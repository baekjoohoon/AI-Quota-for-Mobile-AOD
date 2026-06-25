# Expansion Log

## Wave 0

- Core question: Can GLM Web OAuth collect the 5-hour reset time?
- Axes:
  - Codebase collector path: GLM WebView script, isolated WebView service, normalizer.
  - Runtime evidence: current emulator GLM mode, saved snapshot, WebView storage/cache.
  - External corroboration: public notes and OSS docs for z.ai quota endpoint and reset fields.

## Leads

- LEAD: Web UI quota endpoint may return nextResetTime even when page UI does not display it.
  - Status: supported by local collector code and external OSS docs.
- LEAD: Web OAuth may need frontend token from localStorage rather than browser cookies.
  - Status: supported by current direct API test returning header-auth errors with cookies only, and external bookmarklet example using localStorage token.
- LEAD: Current emulator cannot prove Web OAuth live call because GLM is in API Key mode and known Web OAuth token key is not present.
  - Status: closed as environment limitation for this turn.

