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


def draw_film_strip(width: int, height: int, scale: float, corner_radius: float = 0) -> Image.Image:
    """Original drawing: white film strip with a hollow play sign on blue."""

    def scaled_x(position: float) -> float:
        return width / 2 + (position - 256) * scale

    def scaled_y(position: float) -> float:
        return height / 2 + (position - 256) * scale

    def box(left: float, top: float, right: float, bottom: float) -> tuple[float, float, float, float]:
        return scaled_x(left), scaled_y(top), scaled_x(right), scaled_y(bottom)

    background = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(background)
    for row in range(height):
        ratio = row / height
        mixed = tuple(int(top * (1 - ratio) + end * ratio) for top, end in zip(TOP_COLOR, BOTTOM_COLOR, strict=True))
        draw.line([(0, row), (width, row)], fill=mixed)

    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=int(corner_radius), fill=255)
    image = Image.new("RGBA", (width, height), CLEAR)
    image.paste(background, (0, 0), mask)

    strip = Image.new("RGBA", (width, height), CLEAR)
    draw = ImageDraw.Draw(strip)
    draw.rounded_rectangle(box(64, 120, 448, 392), radius=26 * scale, fill=WHITE)
    for hole in range(6):
        for hole_top in (140, 350):
            draw.rectangle(box(84 + hole * 62, hole_top, 118 + hole * 62, hole_top + 22), fill=CLEAR)

    play_sign = [(scaled_x(216), scaled_y(194)), (scaled_x(216), scaled_y(318)), (scaled_x(322), scaled_y(256))]
    draw.polygon(play_sign, fill=CLEAR)
    image.alpha_composite(strip)

    return image


def main() -> None:
    if len(sys.argv) > 1:
        source = Image.open(sys.argv[1]).convert("RGB")
        icon = ImageOps.fit(source, (96, 96), Image.Resampling.LANCZOS)
        banner = ImageOps.fit(source, (640, 360), Image.Resampling.LANCZOS)
    else:
        icon = draw_film_strip(2048, 2048, 4, 336).resize((96, 96), Image.Resampling.LANCZOS)
        banner = draw_film_strip(2560, 1440, 3.6).resize((640, 360), Image.Resampling.LANCZOS)

    # For sideloaded apps, the Amazon launcher ignores the banner and fits the app icon in a square.
    icon.save(os.path.join(RES, "mipmap-xhdpi/ic_launcher.png"))
    banner.convert("RGB").save(os.path.join(RES, "drawable-xhdpi/banner.png"))


if __name__ == "__main__":
    main()
