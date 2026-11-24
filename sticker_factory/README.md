# Keybird Custom Sticker Factory

Generate your own keyboard stickers — no AI service or network required. Uses Pillow +
Windows Segoe UI Emoji to draw real color-emoji sticker sheets and wallpapers, then builds
valid offline `.iplustheme` ZIP bundles that the existing Keybird theme engine can install.

## What it produces

For each of 4 themes (Bangla Utsav, Cute Zoo, Anime Heroes, Space Crew):

| Output | Installed as | Where it shows |
|---|---|---|
| `<theme_id>_v1.iplustheme` | `theme_type = art_pack` | Keycap character art + full keyboard backdrop (keyboard theme) |
| `<theme_id>_stickers_v1.iplustheme` | `theme_type = sticker` | **Emoji action ➔ Stickers** tab / **Stickers** action — tap to send into any app |

Plus a `.sha256.txt` per bundle for the store catalog's `bundle_sha256`.

## Run

```powershell
cd D:\Projects\iplus-keboard\sticker_factory
python generate_sticker_art.py   # render art/raw/*.png (emoji sheets + wallpapers)
python build_sticker_packs.py    # build art/out/*.iplustheme + sha256 files
```

Requires Python 3 + Pillow (`pip install pillow`).
Edit the `THEMES` list in each script to change letters, names, emoji, colours, or themes.

## Make it your own (3 ways)

1. **Replace the art**: drop your own `<stem>_sheet.png` (2×3 grid, 512px cells) and
   `<stem>_hero.png` into `art/raw/`, rerun `build_sticker_packs.py`.
2. **Chat stickers from your own photos**: in the app, **Settings ➔ Theme ➔ My Stickers
   (custom)** ➔ Add images. They are copied to `filesDir/custom_stickers/my_pack/` and
   appear as a "My Stickers" pack in the keyboard sticker panel.
3. **Standalone Sticker action**: added to the action registry (`uix/actions/StickerAction.kt`)
   and pinned in Favorites — find it in the keyboard action bar / All Actions.

## MEGA pack (500 themes + 1000 stickers)

```powershell
python generate_mega_pack.py          # full: 500 themes + 1000 stickers
python generate_mega_pack.py 20 40    # quick test
```

**Uniqueness guarantee:** every theme gets a distinct fingerprint computed from its title,
palette scheme + hue, wallpaper pattern (12 painters: rings/waves/dots/grid/aurora/stars/
chevrons/hex/mountains/bubbles/sparkles/beams), unique 6-emoji set, effect mode, corner
radius and font. The generator asserts all fingerprints **and** all titles are distinct —
it will fail rather than emit two similar themes.

Outputs to `art/mega/`:
- `themes/kb_gen_0001_v1.iplustheme` … `kb_gen_0500_v1.iplustheme` — each with its own
  palette, emoji wallpaper, 6 keycap stickers, `effect_mode` and SHA-256.
- `stickers/kb_sticker_mega_01_v1.iplustheme` … `_10` — 10 packs × 100 stickers = 1000.
- `catalog_themes.json` / `catalog_stickers.json` — ready-to-import catalog rows
  (theme_id, title, sha256, effect).

Effect modes cycle through `glass, motion, live, 3d_live_motion, touch, neon, aurora,
holographic, parallax, ripple`. The manifest carries `keyboard.effect_mode` and the engine
parses it into `StoreThemeRenderProfile.effectMode`; `glass` renders the frosted rim/glow,
`touch` / `3d_live_motion` additionally amplify the pressed-state flash. Motion/parallax
are declared presets whose animated playback is not yet implemented in the static
Canvas drawable path.

## Security / integration notes

- Bundles are verified with SHA-256 on download, then stored encrypted (AES-GCM) bound to
  `(app_id, theme_id, device_hash)` — see `ThemeStoreRepository.installBundle()` and
  `ThemeStoreDbHelper.writeArtBundle()`.
- ZIP extraction is ZIP-slip-safe (`ArtBundleCodec.sanitizeAssetPath`, `assets/` only).
- Custom sticker files are served through the FileProvider entry `custom_stickers` in
  `java/res/xml/file_paths.xml` so `InputConnection.commitContent()` works.
