#!/usr/bin/env python3
"""Generates the app icon and banner. Usage: python3 logo.py [image.png]
Without an argument it draws the film strip with a play sign; with one it uses that image."""
import os
import sys

from PIL import Image, ImageDraw, ImageOps

DIR = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(DIR, "app/src/main/res")
TOP_COLOR = (0x0F, 0x79, 0xFF)
BOTTOM_COLOR = (0x0A, 0x3F, 0xA8)
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)


def draw_film_strip(width, height, k, radius=0):
    """Original drawing: white film strip with a hollow play sign on blue."""
    px = lambda x: width / 2 + (x - 256) * k
    py = lambda y: height / 2 + (y - 256) * k
    r = lambda x0, y0, x1, y1: [px(x0), py(y0), px(x1), py(y1)]
    background = Image.new("RGB", (width, height))
    d = ImageDraw.Draw(background)
    for y in range(height):
        t = y / height
        mixed = tuple(int(a * (1 - t) + b * t) for a, b in zip(TOP_COLOR, BOTTOM_COLOR, strict=True))
        d.line([(0, y), (width, y)], fill=mixed)
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=int(radius), fill=255)
    img = Image.new("RGBA", (width, height), CLEAR)
    img.paste(background, (0, 0), mask)

    strip = Image.new("RGBA", (width, height), CLEAR)
    d = ImageDraw.Draw(strip)
    d.rounded_rectangle(r(64, 120, 448, 392), radius=26 * k, fill=WHITE)
    for i in range(6):
        for y in (140, 350):
            d.rectangle(r(84 + i * 62, y, 118 + i * 62, y + 22), fill=CLEAR)
    d.polygon([(px(216), py(194)), (px(216), py(318)), (px(322), py(256))], fill=CLEAR)
    img.alpha_composite(strip)
    return img


def main():
    if len(sys.argv) > 1:
        source = Image.open(sys.argv[1]).convert("RGB")
        icon = ImageOps.fit(source, (96, 96), Image.LANCZOS)
        banner = ImageOps.fit(source, (640, 360), Image.LANCZOS)
    else:
        icon = draw_film_strip(2048, 2048, 4, 336).resize((96, 96), Image.LANCZOS)
        banner = draw_film_strip(2560, 1440, 3.6).resize((640, 360), Image.LANCZOS)

    # For sideloaded apps, the Amazon launcher ignores the banner and fits the app icon in a square.
    icon.save(os.path.join(RES, "mipmap-xhdpi/ic_launcher.png"))
    banner.convert("RGB").save(os.path.join(RES, "drawable-xhdpi/banner.png"))


if __name__ == "__main__":
    main()
