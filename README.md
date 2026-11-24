# Keybird

<p align="center">
  <img src="docs/assets/keybird-logo.png" alt="Keybird logo" width="180" />
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

> **Download:** Get the latest universal Android APK (`Keybird-v4.1.2.apk` via CI release) from **[Releases (Latest APK)](https://github.com/iplus009/iplus-keboard/releases/latest)** or browse all builds on the **[Releases page](https://github.com/iplus009/iplus-keboard/releases)**.
>
> **Development status:** Universal APK builds are generated via GitHub Actions. Physical-device regression testing is ongoing.

## Project at a glance

| Item | Details |
|---|---|
| Platform | Android (API 24–35) |
| App name | Keybird |
| Version | `v4.1.2` (`versionCode 40102`) |
| Main input focus | Bangla and English |
| Source branch | `main` |
| Application ID | `com.iplus.keyboard` (retained for compatibility) |
| Build channel | GitHub Actions — stable debug universal APK |
| Official Telegram | [@ismailq01appsstore](https://t.me/ismailq01appsstore) |

## v4.1.2 Demo Showcase

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/v412-home-bento.png" alt="Keybird v4.1.2 Settings Home Bento UI with Theme Store, Themes, My Stickers, and Bengali Layout cards" width="210" />
      <br />
      <b>Keybird v4.1.2 Studio</b><br />
      <sub>Bento quick grid, My Stickers &amp; Bangla toggles</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/v412-aurora-glass-ime.png" alt="Keybird v4.1.2 Aurora Glass live theme with frosted keycaps and crisp key hints" width="210" />
      <br />
      <b>Aurora Glass Live IME</b><br />
      <sub>Frosted glass capsules, motion &amp; crisp hints</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/v412-theme-store.png" alt="Keybird Theme Store with 660+ themes, fonts, and sticker packs" width="210" />
      <br />
      <b>Theme, Font &amp; Sticker Store</b><br />
      <sub>660+ live items, #5-digit search &amp; cloud sync</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/v412-themes-picker.png" alt="Installed and built-in Theme Picker with mini keycap previews and My Stickers shortcut" width="210" />
      <br />
      <b>Installed Themes &amp; Stickers</b><br />
      <sub>Mini keycap previews, custom photos &amp; presets</sub>
    </td>
  </tr>
</table>

## What makes Keybird different

| Experience area | Typical stock / generic keyboards | Keybird |
|---|---|---|
| **Signature visual style** | Uniform monochrome rectangles | **Keybird Matte** (Bauhaus color-block palette + dot-matrix key legends) alongside **Nothing / AMOLED**, **Emerald**, **Cotton Candy**, and **Material You** presets across `20` theme modes |
| **Action strip footprint** | Stacked double toolbars eating screen height | **Single `40dp` inline ActionBar** — tap `>` / `<` to swap inline between suggestions and customizable shortcuts, plus **`...` All Actions** drawer (`Translate`, `Voice Input`, `Text Editor`, `Clipboard`, `Undo/Redo`, `Cut/Copy/Paste`) |
| **Inline Theme Store & Sync** | Hardcoded static themes only | **Live Cloud Theme Store (`88+` themes)** — Category pills, admin live pricing & discounts, Supabase **Email + Google** accounts, lifetime `OWNED` library across reinstalls, and on-demand optional downloads |
| **Voice typing** | Fixed system-only dictation popup | **Mixed Bangla + English Voice Input** with **Live transcription**, **Voice punctuation**, Bluetooth mic preference, offline Whisper support, and `Use Device Voice` fallback |
| **Inline translator** | Requires leaving app or switching keyboards | **Built-in Translator** right inside the keyboard with **Auto Detect**, one-tap **`Bn ⇄ En`** toggle, language swap (`⇄`), and direct insert |
| **Precision editing & clipboard** | Basic cursor drag on spacebar | **Full Text Editor** pad (4-way directional arrows, Shift-Select, Select Word/All, Copy/Paste, Undo/Redo) + **Clipboard Manager** (`50` items / `7` days, password/sensitive-text exclusion) |
| **Portrait comfort height** | Frequently clamped too short (`~243dp`) on tall phones | **Option B ergonomic height** (`292.0dp` default key grid, `55%p` cap) tuned for ~5.2–5.5 cm two-thumb reach |
| **Bangla typing depth** | Usually phonetic-only or 1 fixed layout | **4 built-in Bangla layouts** (`Avro (অভ্র)`, `Probhat (প্রভাত)`, `Jatiyo (জাতীয়)`, and alphabetical `Bangla (বাংলা)`) + `English` — up to **5 active subtypes** switchable right from the spacebar |
| **One-Handed ergonomics** | Basic shrink-to-side box | Dedicated opposite-side **control rail** (`18%` width) with one-tap **Swap Left/Right** and **Expand Full Width**, synced with Split Keyboard |
| **Custom photo personalization** | Simple image overlay that washes out keys | Live **Dim (`0–85%`)**, **Zoom (`1.0x–2.5x`)**, **X/Y pan**, **softening blur (`0–20`)**, **light/dark contrast switch**, and **named reusable presets** |
| **Settings & privacy UX** | Cluttered flat preferences + cloud trackers | **Nothing-inspired dot-matrix UI** with an instant **"Try typing here..."** playground, Banglish auto-correct guardrails, and no third-party ad SDKs |

## Theme showcase

Captured directly from `Keybird` (`com.iplus.keyboard`) on Android emulator (`avro26`, API 26):

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/theme-keybird-matte.png" alt="Keybird Matte Bauhaus theme with collapsed single-bar ActionBar" width="210" />
      <br />
      <b>Keybird Matte</b><br />
      <sub>Bauhaus color-block & dot-matrix legends</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/theme-nothing-amoled.png" alt="AMOLED Nothing theme with Nothing Red accents" width="210" />
      <br />
      <b>AMOLED Nothing</b><br />
      <sub>True black surface & crimson accents</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/theme-emerald.png" alt="Emerald dark theme on Bangla Probhat layout" width="210" />
      <br />
      <b>Emerald</b><br />
      <sub>Deep forest palette & neon green keys</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/theme-cotton-candy.png" alt="Cotton Candy light pastel theme on Bangla Probhat layout" width="210" />
      <br />
      <b>Cotton Candy</b><br />
      <sub>Crisp light pastel contrast & rose accents</sub>
    </td>
  </tr>
</table>

<details>
<summary><b>View Theme Picker Gallery &amp; all 20 built-in / custom theme modes</b></summary>
<br />
<p align="center">
  <img src="docs/assets/showcase/theme-gallery.png" alt="Keybird in-app Theme Picker showing My Photo, Keybird Matte, Nothing, Material, and AMOLED presets" width="240" />
</p>

- **Signature & Custom:** `My Photo` (`CustomPhotoTheme`), `Keybird Matte` (`IPlusMatteTheme`), `VoiceInputTheme`
- **Adaptive / Material You:** `DynamicNothing`, `DynamicSystemTheme`, `DynamicDarkTheme`, `DynamicLightTheme`
- **Dark & AMOLED:** `AMOLEDNothing`, `AMOLEDDarkPurple`, `NothingDark`, `ClassicMaterialDark`, `DeepSeaDark`, `Emerald`
- **Light & Pastel:** `NothingLight`, `ClassicMaterialLight`, `Sunflower`, `Snowfall`, `SteelGray`, `CottonCandy`, `DeepSeaLight`

</details>

## Feature samples in action

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-inline-actionbar.png" alt="Expanded single-bar inline ActionBar with Undo, Redo, Text Editor, Clipboard, One-Handed, and Theme shortcuts" width="210" />
      <br />
      <b>Inline ActionBar</b><br />
      <sub>Expands tools inside the same 40dp bar</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-avro-bangla.png" alt="Avro phonetic Bangla layout active on Keybird spacebar switcher" width="210" />
      <br />
      <b>Avro Phonetic (অভ্র)</b><br />
      <sub>Spacebar swipe/arrows switch up to 5 subtypes</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-jatiyo-bangla.png" alt="Jatiyo fixed Bangla layout active on Keybird" width="210" />
      <br />
      <b>4 Bangla Layouts</b><br />
      <sub>Avro, Probhat, Jatiyo &amp; Bangla Alphabetical</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-one-handed.png" alt="One-Handed Mode with opposite-side Swap and Full-Width rail buttons" width="210" />
      <br />
      <b>One-Handed + Side Rail</b><br />
      <sub>1-tap Left/Right swap &amp; expand-full controls</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-translate.png" alt="Built-in Keybird Translator with Auto Detect, Bengali target, and Bn <-> En quick toggle" width="210" />
      <br />
      <b>Inline Translator (Bn ⇄ En)</b><br />
      <sub>Auto Detect, quick Bn ⇄ En &amp; direct swap</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-voice-keyboard.png" alt="In-keyboard Voice Input panel with offline Whisper and Device Voice options" width="210" />
      <br />
      <b>Voice Input Panel</b><br />
      <sub>Offline Whisper or quick Device Voice</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-voice-settings.png" alt="Keybird Voice Input settings showing Bangla + English mixed typing, Live transcription, and Voice punctuation" width="210" />
      <br />
      <b>Mixed Bangla + English Voice</b><br />
      <sub>Live transcription, punctuation &amp; BT mic</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-more-actions.png" alt="Keybird All Actions grid with Translate, Voice Input, Emojis, Clipboard, Settings, and Edit actions button" width="210" />
      <br />
      <b>All Actions (&hellip; Drawer)</b><br />
      <sub>Quick launch &amp; customizable toolbar order</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-text-editor.png" alt="Keybird Text Editor action window with 4-way cursor arrows, Shift Select, Copy, Paste, and Undo/Redo" width="210" />
      <br />
      <b>Precision Text Editor</b><br />
      <sub>Directional cursor arrows, select &amp; copy/paste</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-clipboard.png" alt="Privacy-first Keybird Clipboard Manager excluding passwords and storing up to 50 items for 7 days" width="210" />
      <br />
      <b>Privacy-First Clipboard</b><br />
      <sub>50 items / 7 days, skips passwords</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-settings-ui.png" alt="Keybird v2.2.1 Settings home screen with updated bird logo, Bangla toggles and Try typing here playground" width="210" />
      <br />
      <b>Keybird v2.2.1 Home</b><br />
      <sub>Updated bird logo, Bangla switches &amp; test box</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/assets/showcase/feature-theme-store.png" alt="Keybird Theme Store showing live categories, free and paid themes, and Supabase account sync bar" width="210" />
      <br />
      <b>Live Theme Store (88+)</b><br />
      <sub>Categories, discounts &amp; account sync</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="25%" colspan="4">
      <img src="docs/assets/showcase/feature-store-login.png" alt="Keybird Store Supabase login dialog with Email Password and Continue with Google" width="210" />
      <br />
      <b>Lifetime Account Login</b><br />
      <sub>Email + Google login keeps paid themes forever</sub>
    </td>
  </tr>
</table>

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

Generated APKs are placed under `build/outputs/apk/stable/debug/` (with universal builds named `keybird-v2.2.1-stable-universal-debug.apk`). CI packages and publishes `Keybird-v<version>.apk` to GitHub Releases on push to `main`.

## Upstream source and legal notices

This project is based on the publicly available source of [Orbit Board](https://github.com/fahitakrim/Orbit-Board) and AOSP `LatinIME`. Copyright notices, Apache-2.0 license terms, the [NOTICE](NOTICE) file, and [LICENSE.md](LICENSE.md) remain intact.

## Official links

- Telegram channel: https://t.me/ismailq01appsstore
- GitHub repository: https://github.com/iplus009/iplus-keboard
