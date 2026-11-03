#!/usr/bin/env python3
"""Generate HAZ-TAK's original icons.

    python3 tools/icons/make_icons.py

- app/src/main/res/drawable/ic_launcher.png             toolbar icon
- app/src/main/res/drawable-nodpi/ic_haztak_radial.png  radial menu glyph (white)
- docs/user_manual/plugin_icon.png

The design (0.3): an orange hazard diamond with a black border and the word
HAZ, so a first-time user can tell what the button is. The letters are
drawn from polygons here (no font file, no external artwork), so the icons
come out the same on any computer.
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ORANGE = (255, 140, 0, 255)
BLACK = (20, 20, 20, 255)
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)
SS = 8  # supersampling

# Letters in a unit box (x right, y down), as (outline polygons, hole polygons).
T = 0.27  # stroke
LETTERS = {
    "H": (0.80, [[(0, 0), (T, 0), (T, 1), (0, 1)],
                 [(0.80 - T, 0), (0.80, 0), (0.80, 1), (0.80 - T, 1)],
                 [(T, 0.5 - T / 2), (0.80 - T, 0.5 - T / 2),
                  (0.80 - T, 0.5 + T / 2), (T, 0.5 + T / 2)]], []),
    "A": (0.92, [[(0, 1), (0.33, 0), (0.59, 0), (0.92, 1), (0.64, 1),
                  (0.58, 0.80), (0.34, 0.80), (0.28, 1)]],
          [[(0.39, 0.58), (0.46, 0.32), (0.53, 0.58)]]),
    "Z": (0.78, [[(0.03, 0), (0.78, 0), (0.78, 0.23), (0.33, 0.77), (0.78, 0.77),
                  (0.78, 1), (0, 1), (0, 0.77), (0.45, 0.23), (0.03, 0.23)]], []),
}
GAP = 0.10


def diamond(c, r):
    return [(c, c - r), (c + r, c), (c, c + r), (c - r, c)]


def word(d, text, cx, cy, h, ink, hole):
    width = sum(LETTERS[ch][0] for ch in text) + GAP * (len(text) - 1)
    x = cx - width * h / 2
    y = cy - h / 2
    for ch in text:
        w, outer, holes = LETTERS[ch]
        for poly in outer:
            d.polygon([(x + px * h, y + py * h) for px, py in poly], fill=ink)
        for poly in holes:
            d.polygon([(x + px * h, y + py * h) for px, py in poly], fill=hole)
        x += (w + GAP) * h


def draw(size, glyph=False):
    s = size * SS
    im = Image.new("RGBA", (s, s), CLEAR)
    d = ImageDraw.Draw(im)
    c = s / 2
    if glyph:
        d.polygon(diamond(c, 0.49 * s), fill=WHITE)
        d.polygon(diamond(c, 0.41 * s), fill=CLEAR)
        word(d, "HAZ", c, c, 0.19 * s, WHITE, CLEAR)
    else:
        d.polygon(diamond(c, 0.49 * s), fill=BLACK)
        d.polygon(diamond(c, 0.445 * s), fill=ORANGE)
        word(d, "HAZ", c, c, 0.21 * s, BLACK, ORANGE)
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
