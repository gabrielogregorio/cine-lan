#!/usr/bin/env python3
"""Regenerates the README screenshots. Usage: python3 android/e2e/screenshots.py [library-folder]"""
import io
import os
import sys

from harness import PACKAGE, REPOSITORY, LocalServer, adb, adb_bytes, keys, menu, open_app, wait
from PIL import Image

TARGET = os.path.join(REPOSITORY, "docs", "screenshots")


def capture(name: str) -> None:
    image = Image.open(io.BytesIO(adb_bytes("exec-out", "screencap", "-p"))).convert("RGB")
    image = image.resize((1280, 720), Image.Resampling.LANCZOS)
    image.save(os.path.join(TARGET, name + ".jpg"), quality=82, optimize=True)
    print(name, flush=True)


def main() -> None:
    os.makedirs(TARGET, exist_ok=True)
    adb("shell", "pm", "clear", PACKAGE)
    with LocalServer(sys.argv[1] if len(sys.argv) > 1 else None):
        open_app()
        wait(4)
        capture("home")
        keys("DPAD_CENTER")
        wait(4)
        capture("detail")
        keys("DPAD_RIGHT", "DPAD_CENTER", "DPAD_RIGHT", "DPAD_CENTER", "DPAD_LEFT", "DPAD_LEFT", "DPAD_CENTER")
        wait(10)
        keys("DPAD_CENTER")
        wait(2)
        capture("player")
        keys("DPAD_CENTER", "BACK")
        wait(2)
        keys("BACK")
        wait(2)
        menu(1, go_back=True)
        wait(3)
        capture("movies")
        menu(4)
        wait(2)
        capture("buggy")
        menu(6)
        wait(4)
        capture("folders")
        keys("BACK")
        wait(2)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")


if __name__ == "__main__":
    main()
