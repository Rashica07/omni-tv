"""File and folder names -> (title, year). A port of OMNI Cinema's MediaName (Kotlin) so the server and the TV read names the same way.
Only names are used here; the metadata provider decides the rest. Pure functions, no I/O."""
import re

VIDEO_EXT = {"mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "mpg", "mpeg", "wmv"}
SUB_EXT = {"srt", "vtt", "ass", "ssa"}

_JUNK = re.compile(r"^(2160p|1080[pi]|720p|576p|480p|360p|4k|uhd|hdr\d*|bluray|blu-ray|bdrip|brrip|dvdrip|dvd|webrip|web-dl|web|hdtv|hdrip|x264|x265|h264|h265|hevc|xvid|divx|aac\d*|ac3|dts|ddp?\d*|atmos|remux|proper|repack|extended|unrated|remastered|internal|multi|subbed|dubbed|10bit|8bit|amzn|nf|yify|yts|rarbg)$", re.I)
_YEAR = re.compile(r"^(19\d{2}|20\d{2})$")
_PAREN_YEAR = re.compile(r"[(\[](19\d{2}|20\d{2})[)\]]")
_GENERIC = re.compile(r"^(movie|video|film|main|feature|title\d*|\d+)$", re.I)


def extension(name):
    return name.rsplit(".", 1)[1].lower() if "." in name else ""


def is_video(name):
    return extension(name) in VIDEO_EXT and not name.startswith(".")


def strip_ext(name):
    return name.rsplit(".", 1)[0] if "." in name and 2 <= len(extension(name)) <= 4 else name


def _clean(s):
    s = re.sub(r"[._]+", " ", s)
    s = re.sub(r"\[[^\]]*\]", " ", s)
    return re.sub(r"\s+", " ", s).strip(" -._")


def parse_movie(path):
    """path is relative to the library root, '/' separated. Returns {'title', 'year'} (year may be None)."""
    parts = [p for p in path.split("/") if p]
    fname = strip_ext(parts[-1])
    folders = parts[:-1]
    src = folders[-1] if _GENERIC.match(fname.strip()) and folders else fname
    words = _clean(src)
    m = None
    for m in _PAREN_YEAR.finditer(src):
        pass
    if m:
        title = _clean(src[:m.start()]) or words
        return {"title": title or words, "year": int(m.group(1))}
    toks = words.split(" ")
    junk_at = next((i for i, t in enumerate(toks) if i > 0 and _JUNK.match(t)), len(toks))
    year, cut = None, junk_at
    # the film's year is a bare year that ends the title (just before the quality junk); a number inside the title ("Blade Runner 2049 Final Cut") stays in it
    if junk_at - 1 >= 1 and _YEAR.match(toks[junk_at - 1]):
        year, cut = int(toks[junk_at - 1]), junk_at - 1
    title = " ".join(toks[:cut]) or words
    return {"title": title, "year": year}


def fold(s):
    import unicodedata
    n = unicodedata.normalize("NFD", (s or "").lower())
    n = "".join(c for c in n if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", n)).strip()
