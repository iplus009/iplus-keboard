# iPlus Keyboard

Bangla and English Android keyboard project. The iPlus app identity and settings experience are being developed around the existing keyboard engine.

## Project goals

- Preserve the upstream typing engine and supported language layouts.
- Keep all existing built-in themes available; new themes must be additive.
- Keep core typing usable offline.
- Add an improved Custom Theme Studio for personal photo backgrounds.
- Publish source and reproducible APK builds.

## Current implementation status

- [x] iPlus app label and settings title.
- [x] iPlus home-screen branding and About section.
- [x] iPlus project links for the Telegram channel and GitHub repository.
- [x] Existing theme registry retained.
- [x] Custom photo selection, persisted image URI and keyboard background rendering already exist in the imported source.
- [x] Adjustable photo-background dim control, saved locally in DataStore and applied by the keyboard theme provider.
- [x] Photo zoom and horizontal/vertical positioning controls, persisted locally and applied by the keyboard background renderer.
- [x] Reset action for custom photo adjustments.
- [ ] Remaining Custom Theme Studio controls (blur, key opacity and saved theme presets).
- [ ] Complete app-facing branding and permission audit.
- [ ] Clean Android build and device regression tests.
- [ ] Release signing and public APK workflow.

## Custom photo theme: current behavior

The current code uses Android's document picker to select an image, persists read access to the selected URI where supported, stores the URI in app preferences and renders it as the keyboard background with a fixed dark overlay. Photo dim, zoom and horizontal/vertical positioning controls are available while the Custom Photo theme is selected. Values are saved locally and applied by the keyboard background renderer; a reset action restores defaults. This is still not the full editor described in the product goals.

## Upstream source and legal notices

This fork is based on the public source of [Orbit Board](https://github.com/fahitakrim/Orbit-Board). Its typing engine and third-party components have upstream provenance. Required copyright notices, Apache-2.0 license terms, NOTICE content and third-party attributions must remain available. App-facing branding is iPlus; legal attribution is kept separately in the Credits / Open Source Notices screen and license files.

The public upstream README states that source is available through v1.0.8 while later releases are distributed as binaries from private development. This project therefore works from the available public source and does not claim to contain the private source for later upstream releases.

## Official links

- Telegram: https://t.me/ismailq01appsstore
- GitHub: https://github.com/iplus009/iplus-keboard

## Build status

No release APK is claimed by this repository until a clean build and device tests have passed. Keep signing keys and passwords outside Git.
