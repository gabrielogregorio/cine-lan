#!/usr/bin/env python3
"""E2E on the emulator: remote keys only, with content really added to and removed from a local server.

Usage: python3 android/e2e/e2e.py
Needs the emulator running with the debug APK (android/emulator.sh debug). Wipes the app data on the emulator.
"""
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time

from harness import PACKAGE, SERIAL, LocalServer, activity, adb, alive, api, keys, menu, open_app, screen, status, wait

TEST_NAME = "QX Teste E2E (2099)"
failures = []


def check(name, condition, detail=""):
    suffix = f"  [{detail}]" if detail and not condition else ""
    print(("  ok  " if condition else "FAIL  ") + name + suffix, flush=True)
    if not condition:
        failures.append(name)


def read_progress():
    """Copies the app database. Only works on the debug APK."""
    with tempfile.TemporaryDirectory() as folder:
        target = os.path.join(folder, "u.sqlite")
        for suffix in ("", "-wal", "-shm"):
            data = subprocess.run(
                ["adb", "-s", SERIAL, "exec-out", "run-as", PACKAGE, "cat", f"databases/user.sqlite{suffix}"],
                capture_output=True,
            ).stdout
            if data.startswith(b"SQLite") or suffix:
                with open(target + suffix, "wb") as file:
                    file.write(data)
        connection = sqlite3.connect(target)
        rows = connection.execute("SELECT id, position FROM progress").fetchall()
        connection.close()
        return dict(rows)


def wait_for_index(condition):
    for _ in range(20):
        if condition():
            return
        wait(0.5)


def add_test_movie(server):
    movies = os.path.join(server.root, "movies")
    source = next(n for n in sorted(os.listdir(movies)) if n.endswith(".mp4") and "(2021)" in n)
    poster = next(n for n in sorted(os.listdir(movies)) if n.endswith("-poster.jpg"))
    shutil.copy(os.path.join(movies, source), os.path.join(movies, TEST_NAME + ".mp4"))
    shutil.copy(os.path.join(movies, poster), os.path.join(movies, TEST_NAME + "-poster.jpg"))
    wait_for_index(lambda: any(i["title"].startswith("QX Teste") for i in api("/api/movies")["items"]))


def remove_test_movie(server):
    for suffix in (".mp4", "-poster.jpg"):
        path = os.path.join(server.root, "movies", TEST_NAME + suffix)
        if os.path.exists(path):
            os.remove(path)
    wait_for_index(lambda: not any(i["title"].startswith("QX Teste") for i in api("/api/movies")["items"]))


def search_qx():
    """Types QX on the on-screen keyboard: no other title has that sequence."""
    menu(5)
    typing = ("DPAD_DOWN", "DPAD_DOWN", *["DPAD_RIGHT"] * 4, "DPAD_CENTER", "DPAD_DOWN", "DPAD_RIGHT", "DPAD_CENTER")
    keys(*typing, pause=0.5)
    wait(2)


def test_movie():
    return next(i for i in api("/api/movies")["items"] if i["title"].startswith("QX Teste"))


def walk_the_menu():
    print("Remote: every menu entry opens and returns", flush=True)
    open_app()
    check("app opens on the home screen", activity() == "MainActivity")
    menu(1)
    check("Movies opens", "Filmes" in screen())
    menu(2)
    check("Shows opens", "Séries" in screen())
    menu(3)
    check("empty Favorites does not lock the remote", "Nada nos favoritos" in screen())
    menu(4, go_back=False)
    check("empty Buggy does not lock the remote", "Nada marcado como bugado" in screen())
    menu(6, go_back=False)
    wait(4)
    check("Folders opens", activity() == "FoldersActivity")
    keys("BACK")
    wait(2)
    check("Folders returns", activity() == "MainActivity")
    menu(8, go_back=False)
    wait(1)
    check("Settings opens", activity() == "SettingsActivity")
    keys("DPAD_DOWN", "DPAD_CENTER")
    text = screen()
    check("order changes to hourly", "toda hora" in text, text[:80])
    keys("DPAD_CENTER")
    check("order changes to A to Z", "De A a Z" in screen())
    keys("DPAD_CENTER")
    check("order returns to daily", "todo dia" in screen())
    keys("BACK")
    wait(2)
    check("Settings returns", activity() == "MainActivity")


def exit_for_real():
    print("Leaving closes everything", flush=True)
    for _ in range(4):
        if not alive() or activity() not in ("MainActivity", "DetailActivity"):
            break
        keys("BACK", pause=1.5)
    wait(4)
    check("Back on the menu ends the process", not alive())


def add_and_watch(server):
    print("New content appears, plays and keeps its position", flush=True)
    before = api("/api/health")["movies"]
    add_test_movie(server)
    check("server counted the new movie", api("/api/health")["movies"] == before + 1)
    open_app()
    search_qx()
    check("search finds the new movie", "QX Teste" in screen())
    keys("DPAD_RIGHT", "DPAD_CENTER")
    wait(3)
    check("detail opens", activity() == "DetailActivity")
    keys("DPAD_RIGHT", "DPAD_CENTER", "DPAD_RIGHT", "DPAD_CENTER")
    check("button marks the title as buggy", "✓  Bugado" in screen())
    keys("DPAD_LEFT", "DPAD_LEFT", "DPAD_CENTER")
    movie = test_movie()
    limit = time.time() + 120
    while time.time() < limit and read_progress().get(movie["id"], 0) < 35000:
        wait(5)
    check("player plays", activity() == "PlayerActivity")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    wait(5)
    check("Home ends the process", not alive())
    position = read_progress().get(movie["id"], 0)
    check("position saved on exit", position > 30000, str(position))
    return position


def resume(position):
    print("Comes back on top and resumes", flush=True)
    open_app()
    text = screen()
    check(
        "Continue watching is the first row",
        text.find("Continuar assistindo") >= 0
        and (text.find("Favoritos") < 0 or text.find("Continuar assistindo") < text.find("Favoritos")),
        text[:120],
    )
    check("movie is in the row", "QX Teste" in text)
    keys("DPAD_CENTER")
    wait(12)
    check("one click opens the player", activity() == "PlayerActivity")
    keys("BACK")
    wait(2)
    now = read_progress().get(test_movie()["id"], 0)
    check("resumed from the position, not the start", now >= position, f"{now} >= {position}")
    keys("BACK")
    wait(2)
    check("Favorites shows the movie", "Favoritos" in screen())
    menu(4)
    check("Buggy shows the movie", "QX Teste" in screen())
    menu(0)


def remove(server):
    print("Removed content disappears without breaking anything", flush=True)
    movie = test_movie()
    remove_test_movie(server)
    check("server returns 404 for the video", status(movie["url"]) == 404)
    check("server returns 404 for the detail", status("/api/items/" + movie["id"]) == 404)
    check("id left the list", movie["id"] not in api("/api/ids")["ids"])
    exit_for_real()
    open_app()
    text = screen()
    check("gone from Continue and Favorites", "QX Teste" not in text, text[:120])
    check("app stays up", activity() == "MainActivity")


def remove_with_the_app_open(server):
    print("A file removed with the app open shows a notice", flush=True)
    add_test_movie(server)
    exit_for_real()
    open_app()
    search_qx()
    keys("DPAD_RIGHT", "DPAD_CENTER")
    wait(3)
    remove_test_movie(server)
    keys("DPAD_CENTER")
    wait(16)
    check("player shows a notice instead of hanging", "não está mais no acervo" in screen())
    keys("BACK")
    wait(2)
    check("Back leaves the notice", activity() == "DetailActivity")


def main():
    adb("shell", "pm", "clear", PACKAGE)
    adb("logcat", "-b", "crash", "-c")
    with LocalServer() as server:
        walk_the_menu()
        exit_for_real()
        position = add_and_watch(server)
        resume(position)
        remove(server)
        remove_with_the_app_open(server)
    crashes = adb("logcat", "-b", "crash", "-d")
    check("no app crash", f"Process: {PACKAGE}" not in crashes)
    print(f"\n{'FAILED: ' + ', '.join(failures) if failures else 'ALL GREEN'}", flush=True)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
