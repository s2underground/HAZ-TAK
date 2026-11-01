#!/usr/bin/env python3
"""Generate HAZ-TAK's original icons.

    python3 tools/icons/make_icons.py

- app/src/main/res/drawable/ic_launcher.png          toolbar icon
- app/src/main/res/drawable-nodpi/ic_haztak_radial.png  radial menu glyph (white)
- docs/user_manual/plugin_icon.png

The design: a hazard diamond with a spill point and a plume drifting
downwind inside a square, echoing the guidebook's protective action zone.
All shapes are drawn here from primitives; no external artwork.
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ORANGE = (255, 109, 0, 255)
RED = (213, 0, 0, 255)
WHITE = (255, 255, 255, 255)
DARK = (25, 25, 25, 255)
CLEAR = (0, 0, 0, 0)
SS = 8


def diamond(c, r):
    return [(c, c - r), (c + r, c), (c, c + r), (c - r, c)]


def draw(size, glyph=False):
    s = size * SS
    im = Image.new("RGBA", (s, s), CLEAR)
    d = ImageDraw.Draw(im)
    c = s / 2
    fg = WHITE if glyph else DARK
    if glyph:
        d.polygon(diamond(c, 0.48 * s), fill=WHITE)
        d.polygon(diamond(c, 0.40 * s), fill=CLEAR)
    else:
        d.polygon(diamond(c, 0.49 * s), fill=DARK)
        d.polygon(diamond(c, 0.44 * s), fill=ORANGE)
    # spill point (left) and the downwind square (right)
    sx, sy = c - 0.17 * s, c
    side = 0.30 * s
    w = max(2, int(0.035 * s))
    d.rectangle((sx, sy - side / 2, sx + side, sy + side / 2), outline=fg, width=w)
    r = 0.075 * s
    d.ellipse((sx - r, sy - r, sx + r, sy + r), fill=WHITE if glyph else RED,
              outline=fg, width=w)
    # plume: three arcs drifting downwind
    for k, rr in enumerate((0.10, 0.17, 0.24)):
        R = rr * s
        d.arc((sx - R, sy - R, sx + R, sy + R), start=-35, end=35, fill=fg,
              width=max(2, int(0.03 * s)))
    return im.resize((size, size), Image.LANCZOS)


def save(im, rel):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)
    print("wrote", rel)


if __name__ == "__main__":
    save(draw(128), "app/src/main/res/drawable/ic_launcher.png")
    save(draw(64, glyph=True), "app/src/main/res/drawable-nodpi/ic_haztak_radial.png")
    save(draw(256), "docs/user_manual/plugin_icon.png")
