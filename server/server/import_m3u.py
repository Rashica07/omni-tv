#!/usr/bin/env python3
"""Imports a standard M3U/M3U8 playlist into the OMNIPTV catalogue (schema 2). Playlists are untrusted input.

Identity: every channel gets a deterministic id built from the SOURCE name plus the playlist's own tvg-id when it has one, otherwise from the
normalised channel name. Two entries are the same channel only when they share that id inside one source (their URLs become alternates). Entries from
different sources are never merged by name alone. Imported streams are tier C (third-party host) and NOT enabled: enabling is the owner's decision.

Usage: import_m3u.py PLAYLIST_FILE --source NAME [--catalog catalog.json] [--country AL|XK|..] [--out merged.json]
"""
import argparse, hashlib, json, re, sys, unicodedata, urllib.parse

MAX_BYTES = 8_000_000
MAX_ENTRIES = 20_000
ATTR = re.compile(r'([\w\-]+)="([^"]*)"')

def slug(s):
    n = unicodedata.normalize("NFD", s.lower())
    n = "".join(c for c in n if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", "-", n).strip("-")[:48]

def safe_url(u):
    p = urllib.parse.urlparse(u)
    return (p.scheme in ("http", "https") and bool(p.hostname) and "@" not in p.netloc and len(u) < 600
            and not re.search(r"[\s<>\"'\\]", u))

def clean_text(s, n=80):
    return re.sub(r"[\x00-\x1f\x7f]", " ", s).strip()[:n]

def parse(text, source):
    """Returns (rows, report). rows follow the schema-2 channel layout; report counts what was skipped and why."""
    report = {"entries": 0, "kept": 0, "unsafe_url": 0, "no_name": 0, "duplicates_merged": 0, "malformed": 0}
    src = slug(source) or "import"
    text = text.lstrip("﻿")
    lines = [l.strip() for l in text.replace("\r\n", "\n").replace("\r", "\n").split("\n")]
    if not lines or not lines[0].startswith("#EXTM3U"):
        report["malformed"] += 1
        return [], report
    chans, pending = {}, None
    for line in lines[1:]:
        if len(chans) >= MAX_ENTRIES:
            break
        if not line:
            continue
        if line.startswith("#EXTINF"):
            head, _, name = line.partition(",")
            attrs = dict(ATTR.findall(head))
            pending = {"name": clean_text(name or attrs.get("tvg-name", "")), "tvg": clean_text(attrs.get("tvg-id", ""), 80),
                       "country": clean_text(attrs.get("tvg-country", ""), 3).upper(), "group": clean_text(attrs.get("group-title", ""), 24).lower(),
                       "lang": clean_text(attrs.get("tvg-language", ""), 12).lower()}
            continue
        if line.startswith("#"):
            continue
        report["entries"] += 1
        meta, pending = pending, None
        if meta is None:
            report["malformed"] += 1
            continue
        if not meta["name"]:
            report["no_name"] += 1
            continue
        if not safe_url(line):
            report["unsafe_url"] += 1
            continue
        key = meta["tvg"] or slug(meta["name"])
        cid = "m3u:%s:%s" % (src, slug(key) or hashlib.sha1(line.encode()).hexdigest()[:8])
        row = chans.get(cid)
        if row is None:
            row = {"id": cid, "name": meta["name"], "alt": [], "country": meta["country"] if len(meta["country"]) == 2 else "",
                   "languages": [meta["lang"]] if meta["lang"] else [], "categories": [meta["group"]] if meta["group"] else [], "website": "",
                   "network": None, "streams": []}
            chans[cid] = row
        else:
            report["duplicates_merged"] += 1
        if all(s["url"] != line for s in row["streams"]):
            row["streams"].append({"url": line, "quality": None, "probe": "unchecked", "checked": "", "tier": "C", "needs_referrer": False, "enabled": False})
    report["kept"] = len(chans)
    return list(chans.values()), report

def merge(catalog, rows):
    """Adds imported channels to a schema-2 catalogue document; existing ids are left untouched."""
    have = {c["id"] for c in catalog.get("channels", [])}
    added = 0
    for r in rows:
        if r["id"] not in have:
            catalog.setdefault("channels", []).append(r)
            added += 1
    return added

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("playlist")
    ap.add_argument("--source", required=True)
    ap.add_argument("--catalog")
    ap.add_argument("--out")
    a = ap.parse_args()
    data = open(a.playlist, "rb").read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        sys.exit("playlist larger than %d bytes: refused" % MAX_BYTES)
    rows, report = parse(data.decode("utf-8", "replace"), a.source)
    print(json.dumps(report))
    if a.catalog:
        doc = json.load(open(a.catalog))
        added = merge(doc, rows)
        json.dump(doc, open(a.out or a.catalog, "w"), indent=1, ensure_ascii=False)
        print("added", added, "channels (disabled; edit the catalogue to enable what you are allowed to use)")

if __name__ == "__main__":
    main()
