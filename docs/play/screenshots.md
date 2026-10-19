# Store listing assets

Where they are: `~/dev_pers/screenshots/play/` — `phone/` (1080x1920),
`tablet-7in/` (1264x1680, the Boox Go 7 panel) and `graphics/` (icon and
feature graphic). The originals stay in `~/dev_pers/screenshots/go7/` and
`exported book/` untouched.

## The icon and the feature graphic

Both are built from `docs/site/icon.svg`, rendered through headless Chrome so
they use the site's own type (Literata, IBM Plex Mono) and read as one thing
with zhuzha.app. Sources in the session scratchpad; the recipe is below.

**Scale the artwork up — it carries the launcher's safe-zone padding.** The
foreground is sized within radius 35 of 108 so a CIRCULAR launcher mask cannot
clip her ear tips. Neither the store icon nor the feature graphic is masked
that way, so at 1.0 she sits small in a sea of white: the icon is rendered at
1.35x and the feature graphic crops at 1.5x.

**The alpha rule inverts between them, which is the trap.** The app icon is
"32-bit PNG (with alpha)"; the feature graphic and every screenshot are "JPEG
or 24-bit PNG (no alpha)". Same listing, opposite requirements.

The feature graphic says `Zhuzha` / "Ask for a book, not an answer." — the
site's own h1, not a new line invented for the store — over the app's dot-grid
texture at its own ~5mm rhythm. The lockup spans x 230-830, so it survives a
4:3 centre crop (which keeps 179-845). A centred alternate is saved beside it.

## What Play requires

- **JPEG or 24-bit PNG with NO ALPHA.** This is the one that bites, see below.
- Min 320px, max 3840px per side, and the long side at most **twice** the short
  side.
- Minimum 2 across all device types, maximum 8 **per device type**.
- For the large-format promotion slots: at least 4 shots, min 1080px, in 16:9
  landscape or 9:16 portrait.

## The alpha trap

`adb exec-out screencap -p` and the device's own power+volume capture both write
**RGBA**, and Play rejects a PNG with an alpha channel. The alpha is fully opaque
(255 everywhere), so flattening onto white is lossless — only the channel goes.

```python
from PIL import Image
im = Image.open(src)
flat = Image.new("RGB", im.size, (255, 255, 255))
flat.paste(im, mask=im.getchannel("A"))
flat.save(dst, "PNG", optimize=True)
```

Check what you have before uploading — PNG colour type 2 is RGB, 6 is RGBA:

```python
d = open(f, 'rb').read(33); print(d[25])   # 2 = OK, 6 = has alpha
```

## Which slot the Boox shots go in

1264x1680 is a 1.33 ratio — valid, and comfortably inside every limit, but it is
not 9:16, so it does **not** satisfy the promo-slot criterion. Put them in the
7-inch tablet slot: the Go 7 is a 7" device and that is what they show. The phone
set is 1080x1920 exactly, so that one does qualify.

## Regenerating the phone set

The emulator normally runs at 320x640 for small-screen testing, which is far too
small to ship. Resize, shoot, restore:

```
adb shell wm size 1080x1920 && adb shell wm density 420
# ... capture ...
adb shell wm size reset && adb shell wm density reset
```

A clean status bar (fixed clock, full battery, no notification icons) comes from
SystemUI demo mode:

```
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# ... capture ...
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

The reader hides the status bar anyway, so this only matters for Library,
Settings, New course, Contents and the Ask panel.

Two things to set up before shooting, or the screens look broken rather than
empty: a Gemini key in Settings (otherwise New course leads with "No API key
set"), and more than one book on the shelf. The emulator's `app.zhuzha` install
was given three by copying course directories out of the older `com.inkfirst`
install under `/sdcard/Android/data/<pkg>/files/courses/`.

**A framebuffer capture does not see live pen-driver ink** — the same property
that makes `screencap` a differential test on the Boox. Any shot meant to show
handwriting must use strokes already saved and re-rendered by the app.
