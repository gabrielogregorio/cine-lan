"""Shared helpers: drive the emulator with remote keys and run a local server on the fictional library."""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from types import TracebackType
from typing import Any

PACKAGE = "app.filmes.tv"
ACTIVITY = "app.medialan.MainActivity"
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")
PORT = 8097
NETWORK_TIMEOUT_S = 90
BASE = f"http://127.0.0.1:{PORT}"
REPOSITORY = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

sys.path.insert(0, os.path.join(REPOSITORY, "demo"))
import make_library  # noqa: E402


def run(command: list[str]) -> str:
    return subprocess.run(command, capture_output=True, text=True, timeout=120).stdout


def adb(*args: str) -> str:
    return run(["adb", "-s", SERIAL, *args])


def adb_bytes(*args: str) -> bytes:
    return subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, timeout=120).stdout


def wait(seconds: float) -> None:
    time.sleep(seconds)


def keys(*names: str, pause: float = 0.8) -> None:
    for name in names:
        adb("shell", "input", "keyevent", "KEYCODE_" + name)
        wait(pause)


def screen() -> str:
    for _ in range(4):
        output = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        if "<hierarchy" in output:
            return " ".join(re.findall(r'text="([^"]*)"', output))
        wait(2)
    return ""


def activity() -> str:
    output = adb("shell", "dumpsys", "activity", "activities")
    match = re.search(r"mResumedActivity.*?/[\w.]*\.(\w+)", output)

    return match[1] if match else ""


def alive() -> bool:
    return adb("shell", "pidof", PACKAGE).strip() != ""


def ensure_network() -> None:
    """The TV image can boot with Wi-Fi off, and 10.0.2.2 is unreachable until it is on."""
    adb("shell", "svc", "wifi", "enable")
    deadline = time.time() + NETWORK_TIMEOUT_S
    while time.time() < deadline:
        if "1 received" in adb("shell", "ping", "-c1", "-W2", "10.0.2.2"):
            return

        wait(1)

    raise RuntimeError("the emulator has no network")


def open_app() -> None:
    ensure_network()
    adb("shell", "am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
    wait(9)


def menu(position: int, go_back: bool = True) -> None:
    """Anchored at the top.

    Home 0, Movies 1, Shows 2, Favorites 3, Buggy 4, Search 5, Folders 6, Shuffle 7, Settings 8.
    """
    if go_back:
        keys("BACK")
    keys(*["DPAD_UP"] * 8, pause=0.35)
    keys(*["DPAD_DOWN"] * position, pause=0.35)
    keys("DPAD_CENTER")
    wait(1.5)


def api(route: str) -> Any:
    with urllib.request.urlopen(BASE + route, timeout=10) as response:
        return json.loads(response.read())


def status(route: str) -> int:
    try:
        with urllib.request.urlopen(BASE + route, timeout=10) as response:
            return int(response.status)
    except urllib.error.HTTPError as error:
        return error.code


class LocalServer:
    """The real server, rescanning every second, on a throwaway fictional library or on the one given."""

    def __init__(self, library: str | None = None) -> None:
        self.library = library

    def __enter__(self) -> "LocalServer":
        self.folder = tempfile.mkdtemp(prefix="media-lan-e2e-")
        self.root = self.library or os.path.join(self.folder, "library")
        if not self.library:
            make_library.build(self.root)
        environment = dict(
            os.environ,
            MEDIA_LAN_ROOT=self.root,
            MEDIA_LAN_DATABASE=os.path.join(self.folder, "index.sqlite"),
            MEDIA_LAN_ADDRESS="0.0.0.0",
            MEDIA_LAN_PORT=str(PORT),
            MEDIA_LAN_NETWORKS="127.0.0.0/8,10.0.2.0/24",
            MEDIA_LAN_SCAN_INTERVAL_S="1",
        )
        self.process = subprocess.Popen(
            [sys.executable, "-m", "media_lan"], cwd=os.path.join(REPOSITORY, "server"), env=environment
        )
        for _ in range(30):
            wait(0.5)
            try:
                if api("/api/health").get("indexing"):
                    return self
            except (OSError, ValueError):
                pass
        raise RuntimeError("the local server did not start")

    def __exit__(
        self,
        kind: type[BaseException] | None,
        error: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        self.process.terminate()
        self.process.wait(timeout=10)
        shutil.rmtree(self.folder, ignore_errors=True)
