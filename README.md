# iPlus Keyboard

A Bangla and English Android keyboard project. The goal is to preserve the upstream typing engine and language behavior while building a distinct iPlus app experience.

## Current development status

- Upstream source: imported from the public [Orbit Board repository](https://github.com/fahitakrim/Orbit-Board), which documents its public source through v1.0.8.
- App branding: initial iPlus app label, settings title and home-screen branding changes are in progress.
- Existing themes: theme IDs and original presets are to be retained.
- Custom photo themes: not implemented yet; the public source currently contains a placeholder button.
- APK: not yet built or tested from this fork.

## Product goals

- **App name:** iPlus Keyboard
- **Languages:** Bangla and English, preserving supported upstream layouts and typing behavior.
- **Offline-first typing:** core typing must not depend on a network connection.
- **Optional online features:** isolated from the typing pipeline and clearly disclosed.
- **Themes:** keep existing themes unchanged and add new themes separately.
- **Custom Theme Studio:** select a personal photo, crop/position it, adjust background and key appearance, preview and save locally.
- **Official channel:** [iPlus Apps Store on Telegram](https://t.me/ismailq01appsstore)

## Source and licensing

This is a derivative project based on the publicly available source of [Orbit Board](https://github.com/fahitakrim/Orbit-Board). The upstream repository states that newer releases after its public v1.0.8 source are maintained privately; this fork uses the public source, not private release code.

The upstream project is distributed under Apache License 2.0. Required copyright notices, the complete license, NOTICE contents and third-party attributions must remain in the repository and in redistributed source/binaries as required. App-facing Orbit Board branding is being replaced, but legal provenance and required attribution are not removed. See [LICENSE.md](LICENSE.md) and [NOTICE](NOTICE).

## Build

The project uses Android Gradle Plugin, Kotlin, Jetpack Compose and native C++ components. Before publishing an APK, verify the build with JDK 17+, Android SDK 34 and the NDK version required by the native build.

The current source build configuration is at the repository root. The upstream README's historical build instructions should not be assumed correct without testing this fork.

## Development notes

- Keep the original namespace and JNI bindings until a complete package/native compatibility audit proves they can safely change.
- Do not delete or rename existing theme keys or preset resources.
- Never commit personal signing keys, keystore passwords or private API credentials.
- Do not describe a feature or APK as complete until it has been implemented and tested.
