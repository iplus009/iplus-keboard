# Keybird

<p align="center">
  <img src="docs/assets/iplus-logo.svg" alt="Keybird logo" width="200" />
</p>

<p align="center">
  <a href="https://github.com/iplus009/iplus-keboard/releases/latest">
    <img src="https://img.shields.io/github/v/release/iplus009/iplus-keboard?label=Download%20APK&color=0EA5E9&style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
  <a href="https://github.com/iplus009/iplus-keboard/releases">
    <img src="https://img.shields.io/badge/GitHub-Releases-1E293B?style=for-the-badge&logo=github" alt="GitHub Releases" />
  </a>
</p>

**Keybird** (formerly *iPlus Keyboard*) is an Android keyboard project focused on Bangla and English input, a customizable typing experience, and a clean app-facing interface built on top of the open-source keyboard engine while preserving upstream licensing and attribution.

> **Download:** Get the latest universal Android APK (`Keybird-v2.0.4.apk` via CI release) from **[Releases (Latest APK)](https://github.com/iplus009/iplus-keboard/releases/latest)** or browse all builds on the **[Releases page](https://github.com/iplus009/iplus-keboard/releases)**.
>
> **Development status:** Universal APK builds are generated via GitHub Actions. Physical-device regression testing is ongoing.

## Project at a glance

| Item | Details |
|---|---|
| Platform | Android (API 24–35) |
| App name | Keybird |
| Version | `v2.0.4` (`versionCode 20004`) |
| Main input focus | Bangla and English |
| Source branch | `main` |
| Application ID | `com.iplus.keyboard` (retained for compatibility) |
| Build channel | GitHub Actions — stable debug universal APK |
| Official Telegram | [@ismailq01appsstore](https://t.me/ismailq01appsstore) |

## Feature overview

### Typing, comfort, and layout controls

- **Option B ergonomic default portrait height:** Raises `config_default_keyboard_height` to `292.0dp` and `config_max_keyboard_height` to `55%p` so standard phones render a comfortable ~5.2–5.5 cm keyboard without clamping back down to ~243dp.
- **Single-bar inline ActionBar:** Merges the expandable shortcut strip cleanly inside the single `40dp` top bar—tapping the chevron switches inline between Suggestions/Pinned actions and Favorite shortcuts without adding a stacked second row.
- **One-Handed Mode (`off` / `right` / `left`):** Fixes settings-to-IME layout refreshes, syncs cleanly with Split Keyboard, adds a quick ActionBar shortcut (`OneHandedAction`), and renders an opposite-side `18%`-width control rail with one-tap Swap Left/Right and Expand Full Width controls.
- **Denser Emoji Picker (`1,979` emojis including Unicode 16.0):** Refines cell sizing (`35dp`) and compact category headers (`emoji_category.xml`) so ~60+ emojis fit comfortably on screen at once without trimming the full emoji catalog.
- **Bangla & English support:** Retains Avro, Probhat, Jatiyo, and Akkhor alongside English QWERTY with support for up to 5 active subtypes, stuck/repeating key touch fixes, and conservative Banglish/short-word auto-correction safeguards.

### Custom photo themes & Keybird Matte

- Includes **Keybird Matte** as well as AMOLED Nothing, Dynamic Auto/Light/Dark, and **Custom Photo** themes.
- Select a personal background image with document picker URI persistence, dim (`0–85%`), zoom (`1.0x–2.5x`), X/Y positioning, lightweight softening blur approximation, and saved preset management.

### Voice input settings & app identity

- Includes mixed Bangla-English voice recognition toggle, live transcription partial results, and voice punctuation preferences (dependent on available voice models and device environment).
- Features **Keybird** app branding, launcher adaptive/monochrome vector icons, and project links to GitHub & `@ismailq01appsstore` while preserving upstream provenance notices.

## Implementation and verification status

| Area | Current status |
|---|---|
| Keybird app-facing branding & launcher logo | Implemented |
| Option B portrait keyboard height (`292.0dp`, `55%p` max) | Implemented & unit-tested (`testStableDebugUnitTest`) |
| Single `40dp` inline ActionBar merge | Implemented |
| One-Handed Mode refresh, side control rail & quick toggle | Implemented & cycle helpers unit-tested |
| Denser Emoji picker layout (`35dp` cells, full 1,979 emojis) | Implemented & `EmojiAdversarialTest` / `EmojiIntegrationTest` checked |
| UpdateChecker `Keybird-*.apk` and legacy `iPlus-*.apk` matching | Implemented & unit-tested |
| Full on-device typing, voice, and UI interaction walkthrough | Requires physical Android device validation |
| Release signing / Play Store production signing | Not included in CI `stableDebug` universal APK |

For the detailed architecture and changelog notes, see [Feature and QA notes](docs/FEATURES.md).

## Build locally

On macOS/Linux:

```bash
./gradlew --no-daemon testStableDebugUnitTest assembleStableDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat --no-daemon testStableDebugUnitTest assembleStableDebug
```

Generated APKs are placed under `build/outputs/apk/stable/debug/` (with universal builds named `keybird-v2.0.4-stable-universal-debug.apk`). CI packages and publishes `Keybird-v<version>.apk` to GitHub Releases on push to `main`.

## Upstream source and legal notices

This project is based on the publicly available source of [Orbit Board](https://github.com/fahitakrim/Orbit-Board) and AOSP `LatinIME`. Copyright notices, Apache-2.0 license terms, the [NOTICE](NOTICE) file, and [LICENSE.md](LICENSE.md) remain intact.

## Official links

- Telegram channel: https://t.me/ismailq01appsstore
- GitHub repository: https://github.com/iplus009/iplus-keboard
