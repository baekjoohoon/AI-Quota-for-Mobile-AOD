# Wave 1: External Sources

## Findings

- Z.AI public FAQ confirms the GLM Coding Plan has 5-hour and weekly quotas, but does not document the quota API.
- OpenUsage documents the internal `GET https://api.z.ai/api/monitor/usage/quota/limit` endpoint and says the endpoint works with both OAuth tokens and API keys.
- OpenUsage documents `data.limits[].nextResetTime` as the reset timestamp and `unit: 3, number: 5` as the 5-hour window.
- A public Reddit bookmarklet example reads `localStorage.getItem("z-ai-open-platform-token-production")`, calls the same quota endpoint with `Authorization: Bearer <token>`, then reads `token.nextResetTime`.
- CodexBar docs treat z.ai as API-token based and use the same endpoint and `nextResetTime` field.

## Sources

- https://docs.z.ai/devpack/faq
- https://github.com/robinebers/openusage/blob/main/docs/providers/zai.md
- https://www.reddit.com/r/ZaiGLM/comments/1szredo/am_i_blind_or_does_zai_seriously_not_tell_you/
- https://github.com/steipete/CodexBar/blob/main/docs/zai.md

## EXPAND

none - enough external corroboration for endpoint, token source, and reset field.

