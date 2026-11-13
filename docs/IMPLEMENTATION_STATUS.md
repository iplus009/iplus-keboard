# iPlus Keyboard — Implementation Status

Updated: 2026-11-13

## Completed in the working branch

- [x] Imported public Orbit Board source by creating a GitHub fork. The fork parent is `fahitakrim/Orbit-Board`.
- [x] Created an isolated implementation branch: `work/iplus-keyboard-implementation`.
- [x] Changed the app and IME labels to **iPlus Keyboard**.
- [x] Changed the settings title and home-screen header to iPlus branding.
- [x] Replaced the home-screen subtitle with a neutral Bangla & English keyboard description.
- [x] Reworked the Credits screen for iPlus while retaining upstream and third-party attribution.
- [x] Renamed the visible Orbit preset label to **Matte Bauhaus** without changing its internal key or saved preference compatibility.
- [x] Replaced the launcher foreground vector and background color with an iPlus keyboard mark.
- [x] Redirected release-check endpoints away from the upstream author's artifact repository to the iPlus repository.
- [x] Removed automatic update and announcement checks from initial Home screen startup. The update check remains user-initiated.
- [x] Added a Custom Photo theme option.
- [x] Added Android Open Document image selection with persisted URI permission.
- [x] Added local bitmap loading and a dim overlay for the custom keyboard background.
- [x] Added a GitHub Actions stable debug APK workflow.

## Not yet completed

- [ ] Wait for the current GitHub Actions build and resolve any compile/build failures.
- [ ] Test the keyboard on a physical Android device, including image URI persistence after app restart.
- [ ] Add crop, zoom, reposition, blur, brightness, opacity and key-style controls to a full Theme Studio.
- [ ] Show the selected photo in the theme preview card.
- [ ] Audit and replace any remaining user-visible upstream product branding in all screens and resources.
- [ ] Review all manifest permissions and optional network data flows.
- [ ] Decide whether to change the Android application ID after checking hard-coded package references.
- [ ] Replace remaining legacy density-specific launcher assets if they are selected on older Android versions.
- [ ] Build a signed release APK using a private signing key outside Git.
- [ ] Create a pull request after CI passes; do not merge until review and device tests pass.

## Important boundaries

- The original theme registry keys and original typing engine have not been intentionally changed.
- The public upstream source is the v1.0.8-era source. Do not copy private newer-release binaries or claim to have their source.
- Preserve LICENSE.md, NOTICE, copyright headers and third-party attributions.
- No release APK has been built or verified yet.
