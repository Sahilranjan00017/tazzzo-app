# Tazzzo brand assets

| File | What |
|---|---|
| `tazzzo_wordmark.svg` | Master wordmark: `tazzzo`, outlined paths, one fill (`#143528`). Runtime copy: `composeApp/src/commonMain/composeResources/drawable/tazzzo_wordmark.xml` (identical path data). |
| `tazzzo_monogram.svg` | Compact `tz` mark for the launcher icon, Android system splash and iOS launch screen (same outlines and corner softening as the wordmark). Runtime copies: `androidMain/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `ic_splash_mark.xml`, the legacy `mipmap-*/ic_launcher*.png`, `iosApp/.../AppIcon.appiconset/appicon1024.png`, `LaunchMark.imageset`. |
| `tools/build_wordmark.py`, `tools/build_monogram.py` | Deterministic generators. They emit a JSON `{viewW, viewH, d}`; the SVG/XML/PNG files are written from it. |

Both marks are **outlined geometry**: nothing at runtime depends on a font. Only the construction base is a font.

## Construction base and licence

- Family: **Poppins ExtraBold** (Indian Type Foundry), used only as the outline source.
- Licence: SIL Open Font License 1.1 — `docs/licenses/Poppins-OFL.txt`. The OFL permits deriving outlined artwork; the
  logo files embed no font program and carry no reserved font name.
- Pinned source: `https://github.com/google/fonts` — `ofl/poppins/Poppins-ExtraBold.ttf` at commit
  `8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5` (sha256 of the file is recorded below). No local or proprietary font
  path is required; the file is fetched into a temporary directory.

## Regenerate (any machine)

```bash
python3 -m venv /tmp/tazzzo-brand && /tmp/tazzzo-brand/bin/pip install fonttools skia-pathops resvg-py pillow
curl -L -o /tmp/Poppins-ExtraBold.ttf \
  https://raw.githubusercontent.com/google/fonts/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/poppins/Poppins-ExtraBold.ttf
shasum -a 256 /tmp/Poppins-ExtraBold.ttf    # must print the sha256 below
/tmp/tazzzo-brand/bin/python design/brand/tools/build_wordmark.py /tmp/Poppins-ExtraBold.ttf /tmp/wm.json
/tmp/tazzzo-brand/bin/python design/brand/tools/build_monogram.py /tmp/Poppins-ExtraBold.ttf /tmp/tz.json
```

The JSON `d` is the `<path d>` of the SVG and the `android:pathData` of the vector XML; `viewW`/`viewH` are the viewBox.
Changing the tuning constants at the top of a script (tracking, z-rhythm, corner radius, leaf notch) is the only way the
marks change — never hand-edit the emitted paths.


POPPINS_SHA256: f2ab17c1a63a0ecc12c2461848fc8a469395e3cd2d641803e889c643d9f958e1
