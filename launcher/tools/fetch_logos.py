#!/usr/bin/env python3
"""Fetch channel logos for OMNI TV's cards from the iptv-org database and store small local copies in assets/logos/.

  python3 -I tools/fetch_logos.py

What it does, and its limits:
  * channels = every id in assets/ptv-catalog.json, plus Italian channels in iptv-org (so the TV's own antenna channels, which only have a name, can be matched by name);
  * each logo is downloaded over https from the URL iptv-org lists, must decode as a real image, is trimmed, fitted into 200x100 and saved as PNG;
  * assets/logos/index.json maps channel id -> file, and normalised name -> file (for antenna channels); assets/logos/SOURCES.json keeps each logo's origin URL.
Logos are the channels' own trademarks, shown only to identify the channel inside a personal build of this launcher. They are not licensed to be redistributed:
do not publish the APK. Nothing is invented: a channel without a downloadable logo keeps its generated card.
"""
import concurrent.futures as cf, io, json, os, re, sys, unicodedata, urllib.request
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "android", "app", "src", "main")
OUT = os.path.join(ROOT, "assets", "logos")
API = "https://iptv-org.github.io/api/"
UA = "Mozilla/5.0 (OMNI-TV logo fetch; personal use)"
MAX_BYTES = 2_000_000


def get(url, limit=MAX_BYTES, timeout=20):
    if not url.startswith("https://"):
        raise ValueError("https only")
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=timeout) as r:
        b = r.read(limit + 1)
    if len(b) > limit:
        raise ValueError("too large")
    return b


def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    return re.sub(r"[^a-z0-9]+", "", "".join(c for c in s if not unicodedata.combining(c)))


def pick(cands):
    ok = [l for l in cands if l.get("format") in ("PNG", "JPEG", "WebP") and (l.get("width") or 0) >= 60]
    ok.sort(key=lambda l: (not l.get("in_use", False), not (not l.get("feed")), -min(l.get("width") or 0, 600)))
    return ok[0] if ok else None


def make(png_bytes):
    im = Image.open(io.BytesIO(png_bytes)); im.load()
    im = im.convert("RGBA")
    if im.width * im.height > 12_000_000:
        raise ValueError("huge")
    bbox = im.getchannel("A").getbbox()
    if bbox: im = im.crop(bbox)
    if im.width < 24 or im.height < 12:
        raise ValueError("tiny")
    im.thumbnail((200, 100), Image.LANCZOS)
    out = io.BytesIO(); im.save(out, "PNG", optimize=True)
    return out.getvalue()


def main():
    os.makedirs(OUT, exist_ok=True)
    logos = json.loads(get(API + "logos.json", 30_000_000, 60)); chans = json.loads(get(API + "channels.json", 30_000_000, 60))
    cat = {c["id"] for c in json.load(open(os.path.join(ROOT, "assets", "ptv-catalog.json")))["channels"]}
    by = {}
    for l in logos: by.setdefault(l["channel"], []).append(l)
    info = {c["id"]: c for c in chans}
    want = set(cat) | {c["id"] for c in chans if c.get("country") == "IT" and not c.get("closed") and not c.get("is_nsfw")}
    todo = [i for i in sorted(want) if i in by and pick(by[i])]
    print("channels wanted", len(want), "with a usable logo entry", len(todo), file=sys.stderr)
    index = {"ids": {}, "names": {}}; sources = {}

    def one(cid):
        l = pick(by[cid]); fn = re.sub(r"[^A-Za-z0-9._-]", "_", cid) + ".png"
        try:
            data = make(get(l["url"]))
        except Exception as e:
            return cid, None, l["url"], type(e).__name__
        open(os.path.join(OUT, fn), "wb").write(data)
        return cid, fn, l["url"], None

    failed = 0
    with cf.ThreadPoolExecutor(16) as ex:
        for cid, fn, url, err in ex.map(one, todo):
            if fn is None: failed += 1; continue
            index["ids"][cid] = fn; sources[cid] = url
            c = info.get(cid, {})
            if c.get("country") == "IT":
                for n in [c.get("name", "")] + list(c.get("alt_names") or []):
                    if norm(n): index["names"].setdefault(norm(n), fn)
    json.dump(index, open(os.path.join(OUT, "index.json"), "w"), separators=(",", ":"), sort_keys=True)
    json.dump(sources, open(os.path.join(OUT, "SOURCES.json"), "w"), indent=0, sort_keys=True)
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print("saved", len(index["ids"]), "logos,", failed, "failed,", size // 1024, "KB total; catalogue covered:", sum(1 for c in cat if c in index["ids"]), "of", len(cat), file=sys.stderr)


if __name__ == "__main__":
    main()
