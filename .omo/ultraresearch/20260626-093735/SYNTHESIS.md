# GLM lightweight collection research

Core question: Can GLM Web OAuth usage collection avoid broad DOM scanning and reduce WebView performance pressure?

Evidence:
- In-app browser selected tab was https://chat.z.ai/.
- Direct navigation to https://z.ai/manage-apikey/coding-plan/personal/usage redirected to https://chat.z.ai/auth... and displayed login choices, so live usage endpoint body could not be verified from this browser session.
- Code review found GLM collector hooks network responses but clones/texts fetch responses before URL filtering.
- Code review found GLM fallback scans window state, network rows, storage/script rows, and visible text repeatedly.

Conclusion:
- Optimize by prefiltering network URLs before reading response bodies, trying known quota JSON endpoint first, and delaying or narrowing heavy DOM fallback.
