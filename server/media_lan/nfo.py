"""Reads the .nfo files written by tinyMediaManager."""
import xml.etree.ElementTree as ET

from .naming import to_int


def _rating(root):
    ratings = root.findall("ratings/rating")
    default = next((r for r in ratings if r.get("default") == "true"), ratings[0] if ratings else None)
    value = default.findtext("value") if default is not None else root.findtext("rating")
    try:
        return float(value) or None
    except (TypeError, ValueError):
        return None


def read_nfo(path):
    """Returns the fields of a .nfo, or {} if the file cannot be read."""
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError):
        return {}

    def text(tag):
        return (root.findtext(tag) or "").strip()

    def texts(tag):
        return [e.text.strip() for e in root.findall(tag) if e.text]

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
            "actors": [a.findtext("name", "").strip() for a in root.findall("actor")][:10],
            "studios": texts("studio"),
            "collection": (root.findtext("set/name") or "").strip(),
            "certification": text("certification") or text("mpaa"),
        },
    }
