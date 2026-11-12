# iPlus Keyboard — Technical Architecture

## Product contract

- Product identity: iPlus Keyboard.
- Preserve the upstream typing engine, native integration and language layouts.
- Keep all existing theme IDs, assets and defaults; add new themes alongside them.
- Core typing should remain usable offline. Optional network features must be separate and explicitly enabled.
- User-selected photo backgrounds remain local to the device.

## Layers

1. App/settings UI: onboarding, language configuration, theme studio, privacy, About.
2. Keyboard presentation: key rendering, background, toolbar, popups and visual theme.
3. Input integration: InputMethodService, InputConnection, composing text and key events.
4. Existing typing engine: LatinIME/AOSP-derived processing, dictionaries, language layouts and JNI/native code.
5. Local settings: preferences, custom image URI and user-created theme definitions.
6. Optional online services: isolated from the input pipeline and disabled unless explicitly enabled.

## Theme compatibility

- Do not delete or rename existing theme IDs.
- Preserve saved preference values.
- Keep the existing custom photo background path as a baseline.
- Build the new editor as a presentation/settings layer; do not modify typing logic to support visual effects.
- Handle missing/unreadable image URIs gracefully and fall back to a normal theme background.

## Custom Theme Studio target

1. Choose an image through Android's system photo/document picker.
2. Persist only the URI permission needed to display the selected image.
3. Preview with the actual keyboard layout.
4. Add crop, zoom and position.
5. Add brightness/dim and blur controls.
6. Add key surface opacity/color, border/radius and label contrast controls.
7. Save multiple named custom themes locally; support rename, duplicate, apply and delete.
8. Keep custom images in app-private storage if URI permission cannot be retained.
9. Downsample images to a suitable size and cache decoded data to avoid keyboard memory/performance problems.

## Branding and licensing

- App-facing labels, launcher assets, onboarding, settings, project README and promotional copy should use iPlus identity.
- Preserve required upstream copyright notices, Apache-2.0 terms, NOTICE content and third-party attribution.
- Keep upstream provenance visible in Credits / Open Source Notices; do not present upstream code as newly authored.

## Quality gates

- Clean release build.
- Install and enable the IME.
- Regression-test English, Avro, Probhat, Jatiyo and other shipped layouts.
- Verify key events, composition, cursor movement, deletion and language switching.
- Verify all pre-existing themes remain selectable and visually unchanged.
- Test image selection, saved URI, editor adjustments, theme save/delete and missing-image fallback.
- Review final manifest permissions and verify offline typing.
