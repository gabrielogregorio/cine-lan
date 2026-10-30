#!/usr/bin/env python3
"""Server tests against a fake library. Usage: python3 -m unittest discover -s tests"""
import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from typing import Any, ClassVar

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "server"))
from media_lan.config import Config  # noqa: E402
from media_lan.database import Database  # noqa: E402
from media_lan.http_api import MediaServer, _byte_range  # noqa: E402
from media_lan.indexer import Indexer  # noqa: E402
from media_lan.naming import season_and_episode  # noqa: E402
from media_lan.nfo import MAX_ACTORS, NfoData, read_nfo  # noqa: E402

MOVIE_NFO = """<?xml version="1.0" encoding="UTF-8"?><movie><title>Ação Total</title>
<originaltitle>Total Action</originaltitle><year>2001</year><plot>Overview.</plot><runtime>90</runtime>
<genre>Action</genre><ratings><rating default="true"><value>7.5</value></rating></ratings>
<director>Jane Doe</director><actor><name>John Roe</name></actor></movie>"""
EPISODE_NFO = "<episodedetails><title>Pilot</title><season>2</season><episode>5</episode></episodedetails>"
SHOW_NFO = "<tvshow><title>Good Show</title><year>1999</year><genre>Comedy</genre></tvshow>"


def write_file(root: str, relative: str, content: bytes | str = b"x") -> None:
    path = os.path.join(root, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as file:
        file.write(content.encode() if isinstance(content, str) else content)


class EpisodeNumbers(unittest.TestCase):
    def test_file_name_formats(self) -> None:
        cases = {
            "Show - S01E08": (1, 8),
            "Show S02E01": (2, 1),
            "Space Duck (2003) - 01x03 - Name": (1, 3),
            "Godparents - Part 3 - Ep.55": (1, 55),
            "Wild Zoo - Ep. 36 Temporada 2": (2, 36),
            "Wild Zoo - Ep. 36 Season 3": (3, 36),
            "04 The Moving House": (1, 4),
            "Show without number": (1, None),
        }
        for name, expected in cases.items():
            with self.subTest(name):
                self.assertEqual(season_and_episode(name), expected)


class ByteRange(unittest.TestCase):
    def test_no_header_is_the_whole_file(self) -> None:
        self.assertEqual(_byte_range("", 100), (0, 99, False))

    def test_open_end_stops_at_the_last_byte(self) -> None:
        self.assertEqual(_byte_range("bytes=90-", 100), (90, 99, True))

    def test_end_past_the_file_is_clamped(self) -> None:
        self.assertEqual(_byte_range("bytes=90-500", 100), (90, 99, True))

    def test_last_valid_start_is_accepted(self) -> None:
        self.assertEqual(_byte_range("bytes=99-", 100), (99, 99, True))

    def test_start_at_the_size_cannot_be_satisfied(self) -> None:
        self.assertIsNone(_byte_range("bytes=100-", 100))

    def test_suffix_larger_than_the_file_starts_at_zero(self) -> None:
        self.assertEqual(_byte_range("bytes=-500", 100), (0, 99, True))

    def test_malformed_header_is_ignored(self) -> None:
        self.assertEqual(_byte_range("items=0-5", 100), (0, 99, False))


class ReadNfo(unittest.TestCase):
    def read(self, content: str) -> NfoData:
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "movie.nfo")
            with open(path, "w", encoding="utf-8") as file:
                file.write(content)

            return read_nfo(path)

    def test_default_rating_wins_over_the_first(self) -> None:
        ratings = (
            '<rating><value>1.0</value></rating><rating default="true"><value>8.5</value></rating>'
        )
        self.assertEqual(self.read(f"<movie><ratings>{ratings}</ratings></movie>").get("rating"), 8.5)

    def test_zero_rating_means_no_rating(self) -> None:
        self.assertIsNone(self.read("<movie><rating>0</rating></movie>").get("rating"))

    def test_runtime_in_minutes_becomes_seconds(self) -> None:
        self.assertEqual(self.read("<movie><runtime>90</runtime></movie>").get("duration_s"), 5400)

    def test_cast_stops_at_the_limit(self) -> None:
        actors = "<actor><name>Someone</name></actor>" * (MAX_ACTORS + 1)
        self.assertEqual(len(self.read(f"<movie>{actors}</movie>")["extra"]["actors"]), MAX_ACTORS)

    def test_unreadable_xml_returns_nothing(self) -> None:
        self.assertEqual(self.read("<movie><title>"), {})


class Http(unittest.TestCase):
    temporary: ClassVar[tempfile.TemporaryDirectory[str]]
    root: ClassVar[str]
    database: ClassVar[Database]
    indexer: ClassVar[Indexer]
    server: ClassVar[MediaServer]
    port: ClassVar[int]

    @classmethod
    def setUpClass(cls) -> None:
        cls.temporary = tempfile.TemporaryDirectory()
        base = cls.temporary.name
        cls.root = os.path.realpath(os.path.join(base, "data"))
        write_file(cls.root, "movies/Total Action (2001).mp4", bytes(range(256)) * 4)
        write_file(cls.root, "movies/Total Action (2001).nfo", MOVIE_NFO)
        write_file(cls.root, "movies/Total Action (2001)-poster.jpg", b"jpg")
        write_file(cls.root, "movies/Total Action (2001).pt.srt", "1\n")
        write_file(cls.root, "movies/Raw No Info (1990).mkv")
        write_file(cls.root, "movies/broken.mp4")
        write_file(cls.root, "movies/broken.nfo", "<movie><title>")
        write_file(cls.root, "shows/Good Show (1999)/tvshow.nfo", SHOW_NFO)
        write_file(cls.root, "shows/Good Show (1999)/poster.jpg", b"jpg")
        write_file(cls.root, "shows/Good Show (1999)/season02-poster.jpg", b"jpg")
        write_file(cls.root, "shows/Good Show (1999)/theme.mp3", b"mp3")
        write_file(cls.root, "shows/Good Show (1999)/Ep a.mp4")
        write_file(cls.root, "shows/Good Show (1999)/Ep a.nfo", EPISODE_NFO)
        write_file(cls.root, "shows/Good Show (1999)/03 No Nfo.mp4")
        write_file(cls.root, "shows/Good Show (1999)/extras/bonus.mp4")
        os.environ.update(
            MEDIA_LAN_ROOT=cls.root,
            MEDIA_LAN_DATABASE=os.path.join(base, "t.sqlite"),
            MEDIA_LAN_ADDRESS="127.0.0.1",
            MEDIA_LAN_PORT="0",
            MEDIA_LAN_NETWORKS="127.0.0.0/8",
        )
        config = Config()
        cls.database = Database(config.database)
        cls.indexer = Indexer(cls.database, config)
        cls.indexer.run()
        cls.server = MediaServer(config, cls.database, cls.indexer)
        cls.port = cls.server.server_address[1]
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.temporary.cleanup()

    def fetch(
        self, route: str, headers: dict[str, str] | None = None, data: bytes | None = None
    ) -> tuple[int, bytes, Any]:
        url = f"http://127.0.0.1:{self.port}{route}"
        request = urllib.request.Request(url, data=data, headers=headers or {})
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                return response.status, response.read(), response.headers
        except urllib.error.HTTPError as error:
            return error.code, error.read(), error.headers

    def json(self, route: str) -> Any:
        return json.loads(self.fetch(route)[1])

    def movie(self, snippet: str) -> dict[str, Any]:
        return next(item for item in self.json("/api/movies")["items"] if snippet in item["title"].lower())

    def test_counts(self) -> None:
        data = self.json("/api/health")
        self.assertEqual((data["movies"], data["shows"], data["episodes"]), (3, 1, 3))
        self.assertEqual(data["missing_nfo"], 4)

    def test_movie_with_nfo_and_image(self) -> None:
        movie = self.movie("total")
        self.assertEqual(
            (movie["title"], movie["year"], movie["rating"], movie["duration_s"]), ("Ação Total", 2001, 7.5, 5400)
        )
        self.assertIn("poster", movie["images"])
        self.assertNotIn("path", movie)
        detail = self.json("/api/items/" + movie["id"])
        self.assertEqual(detail["extra"]["directors"], ["Jane Doe"])
        self.assertEqual(detail["subtitles"][0]["language"], "pt")

    def test_list_has_data_to_find_similar_titles(self) -> None:
        movie = self.movie("total")
        self.assertEqual((movie["directors"], movie["actors"]), (["Jane Doe"], ["John Roe"]))

    def test_folders_show_what_is_missing(self) -> None:
        data = self.json("/api/folders")
        self.assertEqual(data["total"], 6)
        folders = {folder["folder"]: folder["items"] for folder in data["folders"]}
        self.assertEqual(set(folders), {"movies", "shows/Good Show (1999)", "shows/Good Show (1999)/extras"})
        by_name = {entry["file"]: entry for entry in folders["movies"]}
        self.assertTrue(by_name["Total Action (2001).mp4"]["has_cover"])
        self.assertFalse(by_name["Raw No Info (1990).mkv"]["has_cover"])
        self.assertFalse(by_name["Raw No Info (1990).mkv"]["has_nfo"])
        self.assertEqual(self.fetch(by_name["Raw No Info (1990).mkv"]["url"])[0], 200)

    def test_raw_file_without_nfo(self) -> None:
        movie = self.movie("raw")
        self.assertEqual(
            (movie["title"], movie["year"], movie["has_nfo"], movie["images"]), ("Raw No Info", 1990, False, {})
        )
        self.assertEqual(self.fetch(movie["url"])[0], 200)

    def test_broken_nfo_does_not_stop_indexing(self) -> None:
        self.assertFalse(self.movie("broken")["has_nfo"])

    def test_shows_and_seasons(self) -> None:
        show = self.json("/api/shows")["shows"][0]
        self.assertEqual((show["title"], show["episodes"]), ("Good Show", 3))
        detail = self.json("/api/shows/" + show["id"])
        self.assertEqual([season["number"] for season in detail["seasons"]], [0, 1, 2])
        self.assertEqual(detail["seasons"][2]["episodes"][0]["title"], "Pilot")
        self.assertEqual(detail["seasons"][1]["episodes"][0]["episode"], 3)
        self.assertIsNotNone(detail["seasons"][2]["poster"])
        self.assertEqual(self.fetch(detail["images"]["theme"])[1], b"mp3")

    def test_range(self) -> None:
        url = self.movie("total")["url"]
        code, body, headers = self.fetch(url, {"Range": "bytes=10-19"})
        self.assertEqual((code, len(body), headers["Content-Range"]), (206, 10, "bytes 10-19/1024"))
        self.assertEqual(self.fetch(url, {"Range": "bytes=-4"})[1], bytes([252, 253, 254, 255]))
        self.assertEqual(self.fetch(url, {"Range": "bytes=9999-"})[0], 416)

    def test_unknown_host_is_refused(self) -> None:
        self.assertEqual(self.fetch("/api/health", {"Host": "evil.example"})[0], 403)
        self.assertEqual(self.fetch("/api/health", {"Host": "8.8.8.8"})[0], 403)

    def test_disk_paths_are_not_accepted(self) -> None:
        for route in ("/media/../../etc/passwd", "/media/0000000000000000", "/image/x/y", "/movies/a.mp4"):
            with self.subTest(route):
                self.assertEqual(self.fetch(route)[0], 404)

    def test_server_refuses_writes(self) -> None:
        self.assertEqual(self.fetch("/api/movies", data=b"{}")[0], 501)

    def test_list_is_whole_and_sorted(self) -> None:
        data = self.json("/api/movies")
        self.assertEqual(data["total"], len(data["items"]))
        self.assertEqual([movie["title"] for movie in data["items"]], ["Ação Total", "broken", "Raw No Info"])

    def test_ip_outside_the_allowed_networks(self) -> None:
        config = self.server.config
        self.assertFalse(config.network_allowed("8.8.8.8"))
        self.assertFalse(config.network_allowed("192.168.1.5"))
        self.assertTrue(config.network_allowed("127.0.0.1"))

    def test_content_added_and_removed(self) -> None:
        relative = "movies/Came And Went (2021).mp4"
        poster = "movies/Came And Went (2021)-poster.jpg"
        write_file(self.root, relative, b"video")
        write_file(self.root, poster, b"jpg")
        self.indexer.run()
        movie = self.movie("came")
        self.assertEqual(self.fetch(movie["url"])[1], b"video")
        self.assertEqual(self.fetch(movie["images"]["poster"])[0], 200)
        self.assertIn(movie["id"], self.json("/api/ids")["ids"])

        os.remove(os.path.join(self.root, relative))
        os.remove(os.path.join(self.root, poster))
        self.indexer.run()
        self.assertNotIn(movie["id"], self.json("/api/ids")["ids"])
        self.assertEqual(self.fetch(movie["url"])[0], 404)
        self.assertEqual(self.fetch(movie["images"]["poster"])[0], 404)
        self.assertEqual(self.fetch("/api/items/" + movie["id"])[0], 404)

    def test_file_deleted_before_the_scan_returns_404(self) -> None:
        relative = "movies/Gone Early (2022).mp4"
        write_file(self.root, relative)
        self.indexer.run()
        movie = self.movie("gone")
        os.remove(os.path.join(self.root, relative))
        self.assertEqual(self.fetch(movie["url"])[0], 404)
        self.indexer.run()

    def test_ids_include_movies_episodes_and_shows(self) -> None:
        ids = self.json("/api/ids")["ids"]
        data = self.json("/api/health")
        self.assertEqual(len(ids), data["movies"] + data["episodes"] + data["shows"])

    def test_reindex_keeps_ids_and_writes_nothing_without_changes(self) -> None:
        before = self.movie("total")["id"]
        self.assertFalse(self.indexer.run()["changed"])
        write_file(self.root, "movies/New (2020).mp4")
        self.assertTrue(self.indexer.run()["changed"])
        self.assertEqual(self.movie("total")["id"], before)
        self.assertEqual(self.movie("new")["year"], 2020)
        os.remove(os.path.join(self.root, "movies/New (2020).mp4"))
        self.indexer.run()


if __name__ == "__main__":
    unittest.main(verbosity=1)
