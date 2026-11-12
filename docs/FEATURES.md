# iPlus Keyboard — Feature and QA Notes

This document tracks iPlus-specific implementation work on the `work/iplus-keyboard-implementation` branch. “Implemented” means code is present; it does not mean the behavior has passed physical-device testing.

## Keyboard layouts and selection

### Implemented
- Increased the active keyboard selection limit from three to five.
- Updated Home and Languages screens to use the shared limit.
- Updated related labels to reflect the new capacity.

### QA still required
- Confirm Avro, Probhat, Jatiyo and Akkhor are present in the packaged subtype resources.
- Select all four Bangla layouts plus English together.
- Switch between layouts and verify actual input and active-layout indicators.

## Custom photo themes

### Implemented
- Custom photo background support retained and permission handling improved.
- Added background dim, zoom and horizontal/vertical positioning controls.
- Added a blur-style softening preference (0–20).
- Added saved photo-theme presets with save, apply, rename and delete actions.
- Saved presets restore the image URI and its dim, blur, zoom and X/Y settings.
- Reset action includes the blur-style setting.
- Persistent image permission is checked before accepting a selected photo.

### Technical note
The blur-style effect currently uses bitmap downscaling/upscaling as a lightweight softening approximation. It is not a true Gaussian blur.

### QA still required
- Test image selection from different document/gallery providers.
- Test URI persistence after app/device restart.
- Test unavailable or revoked image URIs.
- Test multiple presets, renaming, deletion and reset.
- Confirm all built-in themes remain available and render correctly.

## Voice input

### Implemented
- Added an English-inclusion preference for mixed-language recognition.
- Added a live-transcription / inline partial-result toggle.
- Added a voice-punctuation preference.
- Connected these preferences to the existing voice-input action.
- Adds English to the allowed recognition languages when enabled and the current locale is not English.

### QA still required
- Verify Bangla-only, English-only and mixed Bangla-English dictation.
- Verify partial transcription on/off.
- Verify punctuation behavior with supported models.
- Confirm the required multilingual model is available and installed.
- Test microphone permission denial, offline use and recognition errors.

## Reliability fixes

### Update checker
- Removed the misleading “Already on latest version” fallback when update metadata sources fail.
- The checker now reports failure and clears stale latest-update state instead.

### Custom photo permission
- Checks whether persistent URI permission was granted.
- Stops the save flow and informs the user if permission cannot be retained.
- Adds an accessibility description to the photo-selection control.

### Spell checker
- Android spell-checker service is enabled in the app configuration.
- Actual language coverage and behavior still need device validation.

## App identity and legal attribution

- App-facing name is iPlus Keyboard.
- GitHub and official Telegram links are included in the developer/details area.
- Existing application ID is retained to preserve compatibility with installations using the upstream package identity.
- Required upstream copyright notices, license terms and third-party attributions are retained separately from app-facing branding.

## Build status

- GitHub Actions stable-debug workflow run #65 completed successfully for commit `54c2d3ef3137615cbe49a98b7e2bd0e2693380b1`.
- Artifact: `iplus-keyboard-stable-debug`.
- Workflow: https://github.com/iplus009/iplus-keboard/actions/runs/43300979683
- The Node.js 20 deprecation notice was a warning; this recorded run concluded successfully.
- This is a debug artifact, not a production-signed release.
- No physical-device test result is claimed here.

## Recommended QA checklist

- [ ] Install the debug APK on a clean Android device.
- [ ] Test upgrade from a compatible prior installation, if available.
- [ ] Verify Bangla layouts and English QWERTY.
- [ ] Verify bilingual typing, prediction and autocorrection.
- [ ] Verify personal dictionary and Bangla spell-check behavior.
- [ ] Verify custom photo selection and persistent permissions.
- [ ] Verify dim, softening, zoom and X/Y controls.
- [ ] Save, apply, rename and delete multiple photo presets.
- [ ] Verify every existing built-in theme remains available.
- [ ] Verify Bangla, English and mixed voice input with supported models.
- [ ] Verify live transcription and punctuation preferences.
- [ ] Verify update-check success and failure states.
- [ ] Check startup, responsiveness, memory use and crash logs.

Only mark a QA item complete after testing on a real device or through a documented automated test.
