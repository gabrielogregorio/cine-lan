"""Reads the .nfo files written by tinyMediaManager."""
import xml.etree.ElementTree as ET
from typing import TypedDict

from .naming import to_int

MAX_ACTORS = 10


class NfoExtra(TypedDict):
    original_title: str
    release_date: str
    directors: list[str]
    actors: list[str]
    studios: list[str]
    collection: str
    certification: str


class NfoData(TypedDict, total=False):
    title: str
    original_title: str
    sort_title: str
    overview: str
    year: int | None
    rating: float | None
    genres: list[str]
    duration_s: int | None
    season: int | None
    episode: int | None
    extra: NfoExtra


def _rating(root: ET.Element) -> float | None:
    ratings = root.findall("ratings/rating")
    default = next((rating for rating in ratings if rating.get("default") == "true"), ratings[0] if ratings else None)
    value = default.findtext("value") if default is not None else root.findtext("rating")
    try:
        return float(value or 0) or None
    except ValueError:
        return None


def read_nfo(path: str) -> NfoData:
    """Returns the fields of a .nfo, or {} if the file cannot be read."""
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError):
        return {}

    def text(tag: str) -> str:
        return (root.findtext(tag) or "").strip()

    def texts(tag: str) -> list[str]:
        return [element.text.strip() for element in root.findall(tag) if element.text]

    return {
        "title": text("title"),
        "original_title": text("originaltitle"),
        "sort_title": text("sorttitle"),
        "overview": text("plot") or text("outline"),
        "year": to_int(text("year") or text("premiered")[:4]),
        "rating": _rating(root),
        "genres": texts("genre"),
        "duration_s": (to_int(text("runtime")) or 0) * 60 or None,
        "season": to_int(text("season")),
        "episode": to_int(text("episode")),
        "extra": {
            "original_title": text("originaltitle"),
            "release_date": text("premiered") or text("aired"),
            "directors": texts("director"),
            "actors": [actor.findtext("name", "").strip() for actor in root.findall("actor")][:MAX_ACTORS],
            "studios": texts("studio"),
            "collection": (root.findtext("set/name") or "").strip(),
            "certification": text("certification") or text("mpaa"),
        },
    }
