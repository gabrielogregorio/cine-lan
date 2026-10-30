"""Keeps the SQLite index in step with the files on disk."""
import hashlib
import json
import os
import re
import threading
import time

from .naming import normalize, season_and_episode, stable_id, title_and_year
from .nfo import read_nfo

VIDEO_EXTENSIONS = {".mp4", ".mkv", ".m4v", ".webm", ".avi", ".mov"}
SUBTITLE_EXTENSIONS = {".srt", ".vtt"}
IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".png", ".webp")
IMAGE_KINDS = ("poster", "fanart", "clearlogo", "banner", "thumb", "landscape")


def scan(root):
    present = {}
    for folder, subfolders, names in os.walk(root):
        subfolders[:] = [d for d in subfolders if not d.startswith((".", "$"))]
        for name in names:
            path = os.path.join(folder, name)
            try:
                info = os.stat(path)
            except OSError:
                continue
            present[os.path.relpath(path, root).replace(os.sep, "/")] = (info.st_size, int(info.st_mtime))
    return present


def _dump(value):
    return json.dumps(value, ensure_ascii=False)


class Indexer:
    def __init__(self, database, config):
        self.database = database
        self.root = config.root
        self.movies_dir = config.movies_dir
        self.shows_dir = config.shows_dir
        self.lock = threading.Lock()
        self.last_run = None

    def run(self):
        """Scans the disk. Writes nothing if nothing changed, and rereads only changed .nfo files."""
        with self.lock:
            started = time.time()
            present = scan(self.root)
            fingerprint = hashlib.sha1(repr(sorted(present.items())).encode("utf-8")).hexdigest()
            stored = self.database.row("SELECT value FROM meta WHERE key='fingerprint'")
            changed = not stored or stored["value"] != fingerprint
            if changed:
                self._rebuild(present, fingerprint)
            self.last_run = {"changed": changed, "seconds": round(time.time() - started, 1), "when": time.time()}
            return self.last_run

    def _subtitles_by_base(self, present):
        found = {}
        for relative in present:
            base, extension = os.path.splitext(relative)
            if extension.lower() in SUBTITLE_EXTENSIONS:
                found.setdefault(base, []).append((relative, ""))
                stem, language = os.path.splitext(base)
                if language:
                    found.setdefault(stem, []).append((relative, language[1:]))
        return found

    def _classify(self, relative):
        """Returns (kind, show folder) of a video path, or None if it is outside the library layout."""
        parts = relative.split("/")
        if len(parts) >= 2 and parts[0] == self.movies_dir:
            return "movie", None
        if len(parts) >= 3 and parts[0] == self.shows_dir:
            return "episode", parts[1]
        return None

    def _rebuild(self, present, fingerprint):
        old_items = {row["id"]: row for row in self.database.rows("SELECT * FROM items")}
        old_shows = {row["id"]: row for row in self.database.rows("SELECT * FROM shows")}
        subtitles_by_base = self._subtitles_by_base(present)
        items, shows, images, subtitles = [], {}, [], []

        for relative, (size, mtime) in sorted(present.items()):
            base, extension = os.path.splitext(relative)
            found = self._classify(relative) if extension.lower() in VIDEO_EXTENSIONS else None
            if not found:
                continue
            kind, show_folder = found
            show_id = None
            if show_folder:
                show_id = stable_id("show:" + show_folder)
                if show_id not in shows:
                    shows[show_id] = self._build_show(show_folder, present, old_shows, images)

            item_id = stable_id(relative)
            nfo = base + ".nfo"
            signature = f"{size}:{mtime}:{present.get(nfo, (0, 0))[1]}"
            previous = old_items.get(item_id)
            if previous and previous["signature"] == signature:
                items.append(previous)
            else:
                items.append(self._build_item(item_id, kind, show_id, relative, size, nfo, present, signature))
            for image_kind, path in self._sidecar_images(base, present).items():
                images.append((item_id, image_kind, path))
            for path, language in subtitles_by_base.get(base, []):
                subtitles.append((stable_id(path), item_id, language, path))

        self._store(items, shows, images, subtitles, fingerprint)

    def _store(self, items, shows, images, subtitles, fingerprint):
        connection = self.database.connection
        with connection:
            for table in ("items", "shows", "images", "subtitles"):
                connection.execute(f"DELETE FROM {table}")
            connection.executemany(
                "INSERT INTO items VALUES(:id,:kind,:show_id,:path,:size,:title,:sort_key,:year,:overview,"
                ":rating,:genres,:duration_s,:season,:episode,:has_nfo,:extra,:signature,:indexed_at)",
                items,
            )
            connection.executemany(
                "INSERT INTO shows VALUES("
                ":id,:folder,:title,:sort_key,:year,:overview,:rating,:genres,:extra,:signature)",
                list(shows.values()),
            )
            connection.executemany("INSERT OR REPLACE INTO images VALUES(?,?,?)", images)
            connection.executemany("INSERT OR REPLACE INTO subtitles VALUES(?,?,?,?)", subtitles)
            connection.execute("INSERT OR REPLACE INTO meta VALUES('fingerprint',?)", (fingerprint,))

    def _sidecar_images(self, base, present):
        found = {}
        for kind in IMAGE_KINDS:
            for extension in IMAGE_EXTENSIONS:
                if f"{base}-{kind}{extension}" in present:
                    found[kind] = f"{base}-{kind}{extension}"
                    break
        return found

    def _show_images(self, folder, present):
        found = {}
        prefix = f"{self.shows_dir}/{folder}/"
        for kind in IMAGE_KINDS:
            for extension in IMAGE_EXTENSIONS:
                if prefix + kind + extension in present:
                    found[kind] = prefix + kind + extension
                    break
        for relative in present:
            if relative.startswith(prefix):
                match = re.fullmatch(r"season(\d+)-poster\.\w+", relative[len(prefix):])
                if match:
                    found[f"season-{int(match[1])}"] = relative
        if prefix + "theme.mp3" in present:
            found["theme"] = prefix + "theme.mp3"
        return found

    def _read_nfo_if_present(self, nfo, present):
        return read_nfo(os.path.join(self.root, nfo)) if nfo in present else {}

    def _build_show(self, folder, present, old_shows, images):
        show_id = stable_id("show:" + folder)
        for kind, path in self._show_images(folder, present).items():
            images.append((show_id, kind, path))
        nfo = f"{self.shows_dir}/{folder}/tvshow.nfo"
        signature = str(present.get(nfo, (0, 0))[1])
        previous = old_shows.get(show_id)
        if previous and previous["signature"] == signature:
            return previous
        data = self._read_nfo_if_present(nfo, present)
        folder_title, folder_year = title_and_year(folder)
        title = data.get("title") or folder_title
        return {
            "id": show_id,
            "folder": folder,
            "title": title,
            "sort_key": normalize(data.get("sort_title") or title),
            "year": data.get("year") or folder_year,
            "overview": data.get("overview", ""),
            "rating": data.get("rating"),
            "genres": _dump(data.get("genres", [])),
            "extra": _dump(data.get("extra", {})),
            "signature": signature,
        }

    def _episode_numbers(self, relative, name, data):
        season, episode = season_and_episode(name)
        if relative.split("/")[2].lower() == "extras":
            season = 0
        if (data.get("season") or -1) >= 0 and (data.get("episode") or -1) >= 0:
            season, episode = data["season"], data["episode"]
        return season, episode

    def _build_item(self, item_id, kind, show_id, relative, size, nfo, present, signature):
        data = self._read_nfo_if_present(nfo, present)
        name = os.path.splitext(os.path.basename(relative))[0]
        file_title, file_year = title_and_year(name)
        title = data.get("title") or file_title
        season = episode = None
        if kind == "episode":
            season, episode = self._episode_numbers(relative, name, data)
        original_title = data.get("original_title", "")
        return {
            "id": item_id,
            "kind": kind,
            "show_id": show_id,
            "path": relative,
            "size": size,
            "title": title,
            "sort_key": normalize(f"{data.get('sort_title') or title} {original_title}".strip()),
            "year": data.get("year") or file_year,
            "overview": data.get("overview", ""),
            "rating": data.get("rating"),
            "genres": _dump(data.get("genres", [])),
            "duration_s": data.get("duration_s"),
            "season": season,
            "episode": episode,
            "has_nfo": 1 if data else 0,
            "extra": _dump(data.get("extra", {})),
            "signature": signature,
            "indexed_at": time.time(),
        }
