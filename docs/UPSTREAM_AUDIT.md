# iPlus Keyboard — Source and Branding Audit

Audit date: 2026-11-13  
Repository: https://github.com/iplus009/iplus-keboard  
Upstream: https://github.com/fahitakrim/Orbit-Board

## Verified source facts

- The public source tree inspected for this fork contains approximately 3,600 tracked tree entries.
- Public upstream README documents source availability through v1.0.8; newer v1.2.3+ releases are described as private-development releases distributed as binaries.
- Build configuration uses Android Gradle Plugin 8.2.0 / Kotlin 1.9.20, namespace `org.futo.inputmethod.latin`, application ID `com.arunk140.inputmethod.latin`, compile SDK 34 and min SDK 24.
- Native build uses ndk-build and `native/jni/Android.mk`.
- The source has `stable` and `playstore` flavors.
- Manifest is `java/AndroidManifest.xml`; app/IME label resource is `java/res/values/strings-appname.xml`.

## Existing theme behavior

- Theme model and registry: `java/src/org/futo/inputmethod/latin/uix/theme/ThemeOptions.kt`.
- Theme selector: `java/src/org/futo/inputmethod/latin/uix/theme/selector/ThemePicker.kt`.
- Theme rendering provider: `java/src/org/futo/inputmethod/latin/uix/BasicThemeProvider.kt`.
- Existing preset identifiers remain in the theme registry. Preserve these IDs and serialized preference values.
- The original Orbit preset uses the internal key `OrbitTheme`; the user-facing preset label is already `Matte Bauhaus`. Keep the internal key unless a tested migration is designed.
- The Custom Photo picker is already wired using Android's document picker. It persists the image URI in `CustomKeyboardBackgroundUriSetting`, selects `CustomPhotoTheme`, and the theme provider renders the image with a fixed dark overlay.
- The iPlus working branch adds photo-background dim (0–85%), zoom (1.0x–2.5x), and horizontal/vertical positioning (-100% to +100%), stored in DataStore and consumed by BasicThemeProvider. Blur, custom key styling, multiple saved user themes and theme management are not yet implemented.

## iPlus changes made on this branch

- App label and settings title already read `iPlus Keyboard`.
- Home screen heading already reads iPlus Keyboard.
- Replaced the Home update-status toast text that named Orbit Board with iPlus Keyboard.
- Changed the Home section heading from About Developer to About iPlus.
- Replaced the previous developer-profile card with iPlus Apps Store links to the official Telegram channel and this GitHub repository.
- Added Custom Photo background dim, zoom and horizontal/vertical position sliders; their persisted values are applied to the rendered keyboard background.
- Credits screen keeps a distinct upstream-source attribution section and third-party credits. This is intentional: removing legally required attribution would be incorrect.

## Permission / privacy audit

- The checked-in manifest declares INTERNET and RECORD_AUDIO, among other permissions.
- The upstream README's claim that no Internet permission is present conflicts with the manifest. Treat the final merged manifest as authoritative.
- Before release, trace every permission to a feature and remove only permissions proven unnecessary. Do not promise offline-only networking until the final build is verified. Core typing should not require network access.

## Not yet verified

- No clean Android Gradle build has been run in this environment.
- No APK has been produced or installed for regression testing.
- No device-level test has verified English/Bangla typing, all existing themes or photo URI persistence across reboot.
- The private source for later upstream releases was not available for this audit.
