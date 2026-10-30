"""SQLite access, one connection per thread."""
import sqlite3
import threading

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE IF NOT EXISTS shows(
  id TEXT PRIMARY KEY, folder TEXT, title TEXT, sort_key TEXT, year INTEGER, overview TEXT,
  rating REAL, genres TEXT, extra TEXT, signature TEXT);
CREATE TABLE IF NOT EXISTS items(
  id TEXT PRIMARY KEY, kind TEXT, show_id TEXT, path TEXT, size INTEGER,
  title TEXT, sort_key TEXT, year INTEGER, overview TEXT, rating REAL, genres TEXT,
  duration_s INTEGER, season INTEGER, episode INTEGER, has_nfo INTEGER,
  extra TEXT, signature TEXT, indexed_at REAL);
CREATE INDEX IF NOT EXISTS items_kind ON items(kind, sort_key);
CREATE INDEX IF NOT EXISTS items_show ON items(show_id, season, episode);
CREATE TABLE IF NOT EXISTS images(owner_id TEXT, kind TEXT, path TEXT, PRIMARY KEY(owner_id, kind));
CREATE TABLE IF NOT EXISTS subtitles(id TEXT PRIMARY KEY, item_id TEXT, language TEXT, path TEXT);
CREATE INDEX IF NOT EXISTS subtitles_item ON subtitles(item_id);
"""


class Database:
    def __init__(self, path):
        self.path = path
        self.local = threading.local()
        self.connection.executescript(SCHEMA)

    @property
    def connection(self):
        if not hasattr(self.local, "connection"):
            connection = sqlite3.connect(self.path, timeout=30)
            connection.row_factory = sqlite3.Row
            connection.execute("PRAGMA journal_mode=WAL")
            connection.execute("PRAGMA synchronous=NORMAL")
            self.local.connection = connection
        return self.local.connection

    def rows(self, sql, *params):
        return [dict(row) for row in self.connection.execute(sql, params)]

    def row(self, sql, *params):
        row = self.connection.execute(sql, params).fetchone()
        return dict(row) if row else None
