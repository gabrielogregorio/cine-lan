"""What can be learned from a file name alone."""
import hashlib
import re
import unicodedata


def normalize(text: str | None) -> str:
    without_accents = unicodedata.normalize("NFKD", text or "")

    return "".join(char for char in without_accents if not unicodedata.combining(char)).casefold()


def stable_id(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()[:16]


def to_int(value: str | float | None) -> int | None:
    if value is None:
        return None
    try:
        return int(float(value))
    except ValueError:
        return None


def title_and_year(name: str) -> tuple[str, int | None]:
    match = re.fullmatch(r"(.*?)\s*\((\d{4})\)", name)

    return (match[1], int(match[2])) if match else (name, None)


def season_and_episode(name: str) -> tuple[int, int | None]:
    """Reads S01E02, 01x03, Ep.9, Season 1 or Temporada 1, or a leading number."""
    match = re.search(r"[Ss](\d{1,2})[ ._-]*[Ee](\d{1,4})", name)
    if match:
        return int(match[1]), int(match[2])
    match = re.search(r"(?<!\d)(\d{1,2})x(\d{1,3})(?!\d)", name)
    if match:
        return int(match[1]), int(match[2])
    match = re.search(r"\bEp\.?\s*(\d{1,4})", name, re.IGNORECASE)
    if match:
        season = re.search(r"(?:Season|Temporada)\s*(\d{1,2})", name, re.IGNORECASE)

        return (int(season[1]) if season else 1), int(match[1])
    match = re.match(r"(\d{1,3})(?!\d)", name)

    return (1, int(match[1])) if match else (1, None)
