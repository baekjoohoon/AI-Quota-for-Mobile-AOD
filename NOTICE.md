# Attribution and distribution notice

This repository is an unofficial derivative fork of [datell1357/AI-Quota-for-Mobile](https://github.com/datell1357/AI-Quota-for-Mobile).

The upstream README describes the project license as **MIT**. At the time of this cleanup, the inspected upstream default branch (`codex/phase1-mobile-sync-foundation`) had no root `LICENSE`, `LICENSE.*`, `COPYING`, or `NOTICE` file containing the MIT text. This notice does not invent or replace upstream copyright information. Upstream code remains attributable to its original copyright holders, and any third-party material remains subject to its own terms.

The Galaxy S26 / One UI 8.5 / Good Lock LockStar AOD-related changes in this fork are modifications by **Baek Joohoon (백주훈)**. To the extent that those original, separable changes are copyrightable and owned by that author, they are made available under the following MIT terms. This scoped grant does not relicense upstream code, copied/generated material, third-party dependencies, bundled assets, trademarks, or provider data:

```text
Copyright (c) 2026 Baek Joohoon

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Branding and public distribution

The current source intentionally keeps the upstream-compatible `com.aiquota.mobile` package/applicationId and the existing `AI Quota` name and icon. A source build is not an official upstream or Google Play update and may not be signed with the same key. Public APK or Play distribution should use an independent package/applicationId, signing key, Firebase project, OAuth clients/configuration, app name, icon, privacy/store identity, and any required provider notices. This repository does not imply endorsement or official affiliation with the upstream maintainer, Google, Samsung, Good Lock/LockStar, Anthropic, OpenAI, or the providers named in the app.

Gradle/Maven dependencies are declared dependencies, not copied source, and their own licenses apply. The repository also contains directly bundled fonts, icons, screenshots, and provider artwork whose provenance is inherited from earlier project history or the fork; this notice makes no unverified third-party license claim for them. Verify the relevant upstream/asset licenses before redistributing an APK or registering a store listing.

## Reverse-engineered material

The current branch removes `.omo/reverse/`, which was an APK extraction/decompilation tree containing apktool/jadx output, reconstructed Android resources and manifests, compiled artifacts, images, and Anthropic-named fonts. The path was inherited from upstream commit `10c6635247305fecce6888a25ffd6df898cc4c0c` (`chore(release): 24버전 코드 상태 저장`), which is present in the inspected upstream history; it was not created by the Galaxy S26 AOD fork. This cleanup does not rewrite past commits, so the material remains in historical objects and any other public branches that contain that history.

The repository's provider analysis documents are retained because they contain implementation summaries and small schemas/endpoint examples, not bulk decompiled source, XML, images, or fonts. They should not be treated as a license for provider apps or extracted resources.
