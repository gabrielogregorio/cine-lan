#!/usr/bin/env python3
"""Records the README walkthrough with remote keys. Usage: python3 android/e2e/record_gif.py [library-folder]

Goes through the menu, returns to the home screen, opens a title and starts playback.
Needs the emulator running with the debug APK, plus ffmpeg on the PC.
"""
import os
import subprocess
import sys
import tempfile

from harness import PACKAGE, REPOSITORY, SERIAL, LocalServer, adb, keys, open_app, wait

TARGET = os.path.join(REPOSITORY, "docs", "demo.gif")
REMOTE_VIDEO = "/sdcard/media-lan-demo.mp4"
RECORD_SECONDS = 31
FPS = 6
WIDTH = 560


def walkthrough():
    keys("BACK", pause=1.1)
    keys("DPAD_DOWN", pause=0.8)
    keys("DPAD_CENTER", pause=2.4)
    keys("DPAD_DOWN", pause=1.1)
    keys("BACK", pause=1)
    keys("DPAD_DOWN", pause=0.8)
    keys("DPAD_CENTER", pause=2.4)
    keys("DPAD_RIGHT", pause=1)
    keys("BACK", pause=1)
    keys("DPAD_UP", pause=0.6)
    keys("DPAD_UP", pause=0.6)
    keys("DPAD_CENTER", pause=2.2)
    keys("DPAD_RIGHT", pause=1.2)
    keys("DPAD_RIGHT", pause=1.2)
    keys("DPAD_RIGHT", pause=1.6)
    keys("DPAD_CENTER", pause=2.6)
    keys("DPAD_CENTER", pause=6)


def to_gif(video, target):
    palette = os.path.join(os.path.dirname(video), "palette.png")
    scale = f"fps={FPS},scale={WIDTH}:-1:flags=lanczos"
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", video, "-vf", f"{scale},palettegen=max_colors=64:stats_mode=diff",
         palette],
        check=True,
    )
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", video, "-i", palette, "-lavfi",
         f"{scale}[x];[x][1:v]paletteuse=dither=none:diff_mode=rectangle", target],
        check=True,
    )


def main():
    adb("shell", "pm", "clear", PACKAGE)
    with LocalServer(sys.argv[1] if len(sys.argv) > 1 else None):
        open_app()
        wait(5)
        # The time limit ends the recording cleanly: killing screenrecord leaves an unreadable file.
        recorder = subprocess.Popen(
            ["adb", "-s", SERIAL, "shell", "screenrecord", "--size", "1280x720", "--bit-rate", "6000000",
             "--time-limit", str(RECORD_SECONDS), REMOTE_VIDEO]
        )
        wait(1.5)
        walkthrough()
        recorder.wait(timeout=60)
        wait(1)
    with tempfile.TemporaryDirectory() as folder:
        video = os.path.join(folder, "demo.mp4")
        adb("pull", REMOTE_VIDEO, video)
        to_gif(video, TARGET)
    adb("shell", "rm", REMOTE_VIDEO)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    print(f"{TARGET}: {os.path.getsize(TARGET) // 1024} KB", flush=True)


if __name__ == "__main__":
    main()
