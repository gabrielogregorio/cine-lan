"""Entry point: python3 -m media_lan"""
import sqlite3
import threading
import time

from .config import Config
from .database import Database
from .http_api import MediaServer
from .indexer import Indexer


def scan_forever(indexer, interval_s):
    while True:
        try:
            indexer.run()
        except (OSError, sqlite3.Error) as error:
            print(f"scan failed: {error}", flush=True)
        time.sleep(interval_s)


def main():
    config = Config()
    database = Database(config.database)
    indexer = Indexer(database, config)
    server = MediaServer(config, database, indexer)
    threading.Thread(target=scan_forever, args=(indexer, config.scan_interval_s), daemon=True).start()
    print(f"media-lan on {config.address}:{config.port}, library {config.root}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
