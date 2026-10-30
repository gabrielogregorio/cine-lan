"""Read-only HTTP API: catalog as JSON, video with Range, images and subtitles."""
import json
import mimetypes
import os
import re
import socket
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from .config import Config
from .database import Database, Row
from .indexer import Indexer

Images = dict[str, dict[str, str]]
JsonObject = dict[str, Any]
ClientRequest = socket.socket | tuple[bytes, socket.socket]
ClientAddress = tuple[str, int]

CHUNK_BYTES = 256 * 1024
PUBLIC_ITEM_FIELDS = ("id", "kind", "title", "year", "overview", "rating", "duration_s", "size")
PUBLIC_SHOW_FIELDS = ("id", "title", "year", "overview", "rating")
EXTRA_MIME_TYPES = {
    ".mkv": "video/x-matroska",
    ".srt": "application/x-subrip",
    ".vtt": "text/vtt",
    ".webp": "image/webp",
}


class MediaServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, config: Config, database: Database, indexer: Indexer) -> None:
        self.config = config
        self.database = database
        self.indexer = indexer
        super().__init__((config.address, config.port), RequestHandler)

    def verify_request(self, request: ClientRequest, client_address: ClientAddress) -> bool:
        return self.config.network_allowed(client_address[0])

    def handle_error(self, request: ClientRequest, client_address: ClientAddress) -> None:
        # A player that seeks or closes drops its connection: that is normal, not a server error.
        if not isinstance(sys.exc_info()[1], ConnectionError | TimeoutError):
            super().handle_error(request, client_address)


def _file_format(path: str) -> str:
    return os.path.splitext(path)[1].lstrip(".").lower()


def _people(extra: str | None) -> JsonObject:
    """Fields the app uses to find similar titles without opening each detail."""
    data = json.loads(extra or "{}")

    return {
        "directors": data.get("directors", []),
        "actors": data.get("actors", []),
        "collection": data.get("collection", ""),
    }


def _public_item(item: Row, images: Images) -> JsonObject:
    """Builds the public JSON of an item: the disk path never leaves this function."""
    output = {field: item[field] for field in PUBLIC_ITEM_FIELDS}
    output["show_id"] = item["show_id"]
    output["season"] = item["season"]
    output["episode"] = item["episode"]
    output["has_nfo"] = bool(item["has_nfo"])
    output["genres"] = json.loads(item["genres"] or "[]")
    output["format"] = _file_format(item["path"])
    output["url"] = f"/media/{item['id']}"
    output["images"] = images.get(item["id"], {})
    output.update(_people(item["extra"]))

    return output


def _public_show(show: Row, images: Images, episodes: int) -> JsonObject:
    output = {field: show[field] for field in PUBLIC_SHOW_FIELDS}
    output["genres"] = json.loads(show["genres"] or "[]")
    output["images"] = images.get(show["id"], {})
    output["episodes"] = episodes
    output.update(_people(show["extra"]))

    return output


def _byte_range(header: str, size: int) -> tuple[int, int, bool] | None:
    """Returns (start, end, partial), or None if the request cannot be satisfied."""
    request = re.fullmatch(r"bytes=(\d*)-(\d*)", header)
    if not request or not (request[1] or request[2]):
        return 0, size - 1, False
    if request[1]:
        start = int(request[1])
        end = min(int(request[2]), size - 1) if request[2] else size - 1
    else:
        start, end = max(size - int(request[2]), 0), size - 1
    return None if start > end else (start, end, True)


class RequestHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "media-lan"
    sys_version = ""
    timeout = 120
    server: MediaServer
    send_body = True

    def log_message(self, format: str, *args: object) -> None:
        pass

    def do_HEAD(self) -> None:
        self._dispatch(False)

    def do_GET(self) -> None:
        self._dispatch(True)

    @property
    def database(self) -> Database:
        return self.server.database

    def _host_allowed(self) -> bool:
        host = (self.headers.get("Host") or "").rsplit(":", 1)[0].lower()

        return host in self.server.config.hosts or self.server.config.network_allowed(host)

    def _dispatch(self, send_body: bool) -> None:
        self.send_body = send_body
        if not self._host_allowed():
            self._error(403, "host not allowed")

            return
        path = urllib.parse.urlsplit(self.path).path
        for pattern, method in ROUTES:
            match = re.fullmatch(pattern, path)
            if match:
                getattr(self, method)(*match.groups())

                return
        self._error(404, "route not found")

    def _send_bytes(self, data: bytes, content_type: str, code: int = 200) -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        if self.send_body:
            self.wfile.write(data)

    def _send_json(self, data: object, code: int = 200) -> None:
        self._send_bytes(json.dumps(data, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8", code)

    def _error(self, code: int, message: str) -> None:
        self._send_json({"error": message}, code)

    def _images_by_owner(self) -> Images:
        images: Images = {}
        for row in self.database.rows("SELECT owner_id, kind FROM images"):
            images.setdefault(row["owner_id"], {})[row["kind"]] = f"/image/{row['owner_id']}/{row['kind']}"
        return images

    def _count(self, sql: str) -> int:
        row = self.database.row(sql)

        return int(row["n"]) if row else 0

    def health(self) -> None:
        self._send_json(
            {
                "status": "ok",
                "movies": self._count("SELECT COUNT(*) n FROM items WHERE kind='movie'"),
                "shows": self._count("SELECT COUNT(*) n FROM shows"),
                "episodes": self._count("SELECT COUNT(*) n FROM items WHERE kind='episode'"),
                "missing_nfo": self._count("SELECT COUNT(*) n FROM items WHERE has_nfo=0"),
                "indexing": self.server.indexer.last_run,
            }
        )

    def list_movies(self) -> None:
        images = self._images_by_owner()
        rows = self.database.rows("SELECT * FROM items WHERE kind='movie' ORDER BY sort_key")
        self._send_json({"total": len(rows), "items": [_public_item(row, images) for row in rows]})

    def list_shows(self) -> None:
        images = self._images_by_owner()
        rows = self.database.rows(
            "SELECT s.*, (SELECT COUNT(*) FROM items i WHERE i.show_id=s.id) n FROM shows s ORDER BY s.sort_key"
        )
        self._send_json({"total": len(rows), "shows": [_public_show(row, images, row["n"]) for row in rows]})

    def list_ids(self) -> None:
        """Ids that exist, so the app can hide progress and favorites of files gone from disk."""
        rows = self.database.rows("SELECT id FROM items") + self.database.rows("SELECT id FROM shows")
        self._send_json({"ids": [row["id"] for row in rows]})

    def list_folders(self) -> None:
        """The library as it is on disk, to find what has no cover or no .nfo."""
        images = self._images_by_owner()
        folders: dict[str, list[JsonObject]] = {}
        rows = self.database.rows("SELECT id, kind, path, title, has_nfo, size, show_id FROM items ORDER BY path")
        for row in rows:
            folder, file_name = os.path.split(row["path"])
            own_images = images.get(row["id"], {})
            cover = own_images.get("poster") or own_images.get("thumb")
            folders.setdefault(folder, []).append(
                {
                    "id": row["id"],
                    "kind": row["kind"],
                    "show_id": row["show_id"],
                    "title": row["title"],
                    "file": file_name,
                    "format": _file_format(file_name),
                    "size": row["size"],
                    "has_nfo": bool(row["has_nfo"]),
                    "has_cover": cover is not None,
                    "url": f"/media/{row['id']}",
                    "images": {"thumb": cover} if cover else {},
                }
            )
        folder_list = [{"folder": folder, "items": entries} for folder, entries in folders.items()]
        self._send_json({"total": len(rows), "folders": folder_list})

    def item_detail(self, item_id: str) -> None:
        item = self.database.row("SELECT * FROM items WHERE id=?", item_id)
        if not item:
            self._error(404, "item not found")

            return
        output = _public_item(item, self._images_by_owner())
        output["extra"] = json.loads(item["extra"] or "{}")
        rows = self.database.rows("SELECT id, language FROM subtitles WHERE item_id=?", item_id)
        output["subtitles"] = [{"language": row["language"], "url": f"/subtitle/{row['id']}"} for row in rows]
        self._send_json(output)

    def show_detail(self, show_id: str) -> None:
        show = self.database.row("SELECT * FROM shows WHERE id=?", show_id)
        if not show:
            self._error(404, "show not found")

            return
        images = self._images_by_owner()
        episodes = self.database.rows(
            "SELECT * FROM items WHERE show_id=? ORDER BY season, episode IS NULL, episode, sort_key", show_id
        )
        output = _public_show(show, images, len(episodes))
        output["extra"] = json.loads(show["extra"] or "{}")
        seasons: dict[int, list[JsonObject]] = {}
        for row in episodes:
            seasons.setdefault(row["season"], []).append(_public_item(row, images))
        output["seasons"] = [
            {"number": number, "poster": output["images"].get(f"season-{number}"), "episodes": items}
            for number, items in seasons.items()
        ]
        self._send_json(output)

    def media(self, item_id: str) -> None:
        row = self.database.row("SELECT path FROM items WHERE id=?", item_id)
        self._send_file(row["path"] if row else None, True)

    def image(self, owner_id: str, kind: str) -> None:
        row = self.database.row("SELECT path FROM images WHERE owner_id=? AND kind=?", owner_id, kind)
        self._send_file(row["path"] if row else None, False)

    def subtitle(self, subtitle_id: str) -> None:
        row = self.database.row("SELECT path FROM subtitles WHERE id=?", subtitle_id)
        self._send_file(row["path"] if row else None, False)

    def _send_file(self, relative: str | None, with_range: bool) -> None:
        """Serves only paths that came from the database: clients never name a disk path."""
        root = self.server.config.root
        path = os.path.realpath(os.path.join(root, relative)) if relative else ""
        if not path.startswith(root + os.sep) or not os.path.isfile(path):
            self._error(404, "file not found")

            return
        size = os.path.getsize(path)
        byte_range = _byte_range(self.headers.get("Range", "") if with_range else "", size)
        if byte_range is None:
            self.send_response(416)
            self.send_header("Content-Range", f"bytes */{size}")
            self.send_header("Content-Length", "0")
            self.end_headers()

            return
        start, end, partial = byte_range
        extension = os.path.splitext(path)[1].lower()
        content_type = EXTRA_MIME_TYPES.get(extension) or mimetypes.guess_type(path)[0] or "application/octet-stream"
        self.send_response(206 if partial else 200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(end - start + 1))
        self.send_header("X-Content-Type-Options", "nosniff")
        if with_range:
            self.send_header("Accept-Ranges", "bytes")
        else:
            self.send_header("Cache-Control", "max-age=86400")
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if self.send_body:
            self._stream(path, start, end)

    def _stream(self, path: str, start: int, end: int) -> None:
        try:
            with open(path, "rb") as file:
                file.seek(start)
                remaining = end - start + 1
                while remaining:
                    chunk = file.read(min(CHUNK_BYTES, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    remaining -= len(chunk)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True


ROUTES = [
    (r"/api/health", "health"),
    (r"/api/movies", "list_movies"),
    (r"/api/ids", "list_ids"),
    (r"/api/folders", "list_folders"),
    (r"/api/shows", "list_shows"),
    (r"/api/shows/([0-9a-f]{16})", "show_detail"),
    (r"/api/items/([0-9a-f]{16})", "item_detail"),
    (r"/media/([0-9a-f]{16})", "media"),
    (r"/image/([0-9a-f]{16})/([a-z0-9-]+)", "image"),
    (r"/subtitle/([0-9a-f]{16})", "subtitle"),
]
