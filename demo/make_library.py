#!/usr/bin/env python3
"""Builds a fictional library with generated art and short clips. Usage: python3 demo/make_library.py target-folder"""
import colorsys
import hashlib
import os
import random
import subprocess
import sys
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
CLIP_SECONDS = 60
MOVIES = [
    ("A Última Órbita", 2021, "Ficção científica", 8.4, "Uma engenheira fica sozinha numa estação e descobre um sinal que vem de dentro dela."),
    ("O Farol de Vidro", 2018, "Drama", 7.9, "Duas irmãs voltam à ilha da infância para decidir o destino do velho farol."),
    ("Maré Vermelha", 2020, "Suspense", 7.1, "Um pescador encontra uma carga que ninguém deveria ter visto."),
    ("Noite no Sertão", 2016, "Aventura", 7.6, "Uma caravana atravessa o sertão seguindo um mapa desenhado de memória."),
    ("Cidade de Papel", 2019, "Comédia", 6.8, "Um cartógrafo inventa um bairro e, um dia, recebe uma carta de lá."),
    ("O Relojoeiro", 2015, "Drama", 8.1, "O último relojoeiro da cidade conserta uma hora que ninguém viveu."),
    ("Asas de Ferrugem", 2022, "Aventura", 7.3, "Dois amigos reformam um avião abandonado para cruzar o país."),
    ("Silêncio na Mata", 2017, "Suspense", 6.9, "Uma expedição perde o contato com a cidade e entre si."),
    ("Doce Tempestade", 2014, "Comédia", 6.5, "Uma confeitaria enfrenta o maior temporal da história do bairro."),
    ("Vozes do Porto", 2023, "Drama", 8.0, "Um rádio pirata reúne uma cidade inteira durante um apagão."),
    ("Terra Sem Mapa", 2012, "Ficção científica", 7.7, "Colonos acordam num planeta que muda de forma a cada noite."),
    ("O Último Trem", 2011, "Aventura", 7.2, "Uma ferrovia desativada guarda a única saída de um vale isolado."),
]
SHOWS = [
    ("Patrulha Lunar", 2019, "Ficção científica", 8.2, "Uma equipe pequena cuida da única base da Lua."),
    ("Vila dos Ventos", 2017, "Comédia", 7.4, "A rotina de uma vila onde o vento nunca para."),
    ("Detetives do Bairro", 2021, "Suspense", 7.8, "Dois aposentados resolvem casos que a polícia ignora."),
]
EPISODES = ["O começo", "A visita", "Sem volta"]
NO_COVER = ("Arquivo Sem Dados (2010)", "Rascunho de Montagem (2024)")


def color(seed, offset=0.0, saturation=0.7, value=0.8):
    hue = (int(hashlib.sha1(seed.encode()).hexdigest()[:6], 16) / 0xFFFFFF + offset) % 1
    return tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, saturation, value))


def gradient(size, top, bottom):
    image = Image.new("RGB", size)
    draw = ImageDraw.Draw(image)
    for y in range(size[1]):
        t = y / size[1]
        draw.line([(0, y), (size[0], y)], fill=tuple(int(a * (1 - t) + b * t) for a, b in zip(top, bottom, strict=True)))
    return image


def backdrop(title, size):
    rng = random.Random(title)
    image = gradient(size, color(title, 0, 0.75, 0.35), color(title, 0.08, 0.8, 0.85))
    glow = Image.new("RGB", size)
    draw = ImageDraw.Draw(glow)
    for _ in range(9):
        radius = rng.randint(size[1] // 8, size[1] // 2)
        x, y = rng.randint(0, size[0]), rng.randint(0, size[1])
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=color(title, rng.random() * 0.25, 0.7, 0.9))
    return Image.blend(image, glow.filter(ImageFilter.GaussianBlur(size[1] // 10)), 0.45)


def wrapped(draw, text, font, width):
    lines, line = [], ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if draw.textlength(candidate, font=font) > width and line:
            lines.append(line)
            line = word
        else:
            line = candidate
    return lines + [line]


def make_poster(path, title):
    image = backdrop(title, (500, 750))
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(FONT, 56)
    y = 470
    for line in wrapped(draw, title, font, 440):
        draw.text((30, y), line, font=font, fill="#FFFFFF", stroke_width=2, stroke_fill="#00000060")
        y += 68
    image.save(path, quality=88)


def make_fanart(path, title):
    backdrop(title, (1280, 720)).save(path, quality=85)


def make_logo(path, title):
    font = ImageFont.truetype(FONT, 72)
    image = Image.new("RGBA", (800, 310), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    y = 40
    for line in wrapped(draw, title, font, 760)[:2]:
        draw.text((10, y), line, font=font, fill="#FFFFFF", stroke_width=3, stroke_fill="#000000")
        y += 90
    image.save(path)


def make_nfo(path, tag, title, year, genre, rating, overview, extra=""):
    with open(path, "w", encoding="utf-8") as file:
        file.write(
            f"<{tag}><title>{escape(title)}</title><year>{year}</year><plot>{escape(overview)}</plot>"
            f"<runtime>1</runtime><genre>{genre}</genre>"
            f'<ratings><rating default="true"><value>{rating}</value></rating></ratings>'
            f"<director>Direção Fictícia</director><actor><name>Elenco Fictício</name></actor>{extra}</{tag}>"
        )


def make_clip(path):
    subprocess.run(
        [
            "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
            f"testsrc2=size=1280x720:rate=24:duration={CLIP_SECONDS}", "-f", "lavfi", "-i",
            f"sine=frequency=330:duration={CLIP_SECONDS}", "-c:v", "libx264", "-preset", "veryfast",
            "-pix_fmt", "yuv420p", "-b:v", "400k", "-c:a", "aac", "-b:a", "48k", "-shortest", path,
        ],
        check=True,
    )


def link_clip(clip, path):
    if not os.path.exists(path):
        os.link(clip, path)


def build(root):
    movies = os.path.join(root, "movies")
    shows = os.path.join(root, "shows")
    os.makedirs(movies, exist_ok=True)
    os.makedirs(shows, exist_ok=True)
    clip = os.path.join(root, ".clip.mp4")
    if not os.path.exists(clip):
        make_clip(clip)

    for title, year, genre, rating, overview in MOVIES:
        base = os.path.join(movies, f"{title} ({year})")
        link_clip(clip, base + ".mp4")
        make_nfo(base + ".nfo", "movie", title, year, genre, rating, overview)
        make_poster(base + "-poster.jpg", title)
        make_fanart(base + "-fanart.jpg", title)
        make_logo(base + "-clearlogo.png", title)
    for name in NO_COVER:
        link_clip(clip, os.path.join(movies, name + ".mp4"))

    for title, year, genre, rating, overview in SHOWS:
        folder = os.path.join(shows, f"{title} ({year})")
        os.makedirs(folder, exist_ok=True)
        make_nfo(os.path.join(folder, "tvshow.nfo"), "tvshow", title, year, genre, rating, overview)
        make_poster(os.path.join(folder, "poster.jpg"), title)
        make_fanart(os.path.join(folder, "fanart.jpg"), title)
        make_logo(os.path.join(folder, "clearlogo.png"), title)
        for number, name in enumerate(EPISODES, 1):
            base = os.path.join(folder, f"{title} - S01E{number:02d}")
            link_clip(clip, base + ".mp4")
            make_nfo(base + ".nfo", "episodedetails", name, year, genre, rating, f"Episódio {number} de {title}.",
                     f"<season>1</season><episode>{number}</episode>")
            make_fanart(base + "-thumb.jpg", f"{title} {number}")


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "build/demo-library")
