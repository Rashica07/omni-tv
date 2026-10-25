"""Reads just the headers of MP4/MOV/M4V and Matroska/WebM files to learn duration, video and audio codecs, without ffprobe and without reading the media data.
Bounded: at most a few MB are read. Anything it cannot read is reported as unknown, never guessed. verdict() says what this TV is likely to do with the file."""
import os, struct

MAX_MOOV = 24 * 1024 * 1024
MAX_MKV_HEAD = 4 * 1024 * 1024

VIDEO_NAMES = {b"avc1": "h264", b"avc3": "h264", b"hvc1": "hevc", b"hev1": "hevc", b"vp09": "vp9", b"av01": "av1", b"mp4v": "mpeg4", b"vp08": "vp8", b"s263": "h263"}
AUDIO_NAMES = {b"mp4a": "aac", b"ac-3": "ac3", b"ec-3": "eac3", b"dtsc": "dts", b"dtsh": "dts", b"dtsl": "dts", b"dtse": "dts", b"Opus": "opus", b"fLaC": "flac", b".mp3": "mp3", b"alac": "alac"}
MKV_NAMES = {"V_MPEG4/ISO/AVC": "h264", "V_MPEGH/ISO/HEVC": "hevc", "V_VP9": "vp9", "V_VP8": "vp8", "V_AV1": "av1", "V_MPEG4/ISO/ASP": "mpeg4", "V_MPEG4/ISO/SP": "mpeg4", "V_MS/VFW/FOURCC": "mpeg4",
             "A_AAC": "aac", "A_AC3": "ac3", "A_EAC3": "eac3", "A_DTS": "dts", "A_OPUS": "opus", "A_VORBIS": "vorbis", "A_FLAC": "flac", "A_MPEG/L3": "mp3", "A_TRUEHD": "truehd", "A_MPEG/L2": "mp2"}


def probe(path):
    """Returns {'container', 'duration_ms', 'video': [{'codec','w','h'}], 'audio': [codec,...], 'error'?}. Never raises for a bad file."""
    ext = path.rsplit(".", 1)[-1].lower() if "." in path else ""
    try:
        with open(path, "rb") as f:
            head = f.read(12)
            f.seek(0)
            if head[4:8] == b"ftyp" or ext in ("mp4", "m4v", "mov"):
                return _mp4(f, os.path.getsize(path))
            if head[:4] == b"\x1a\x45\xdf\xa3":
                return _mkv(f)
    except (OSError, struct.error, ValueError, IndexError) as e:
        return {"container": ext or "unknown", "duration_ms": 0, "video": [], "audio": [], "error": "unreadable (%s)" % type(e).__name__}
    return {"container": ext or "unknown", "duration_ms": 0, "video": [], "audio": [], "error": "container not inspected"}


# ---------------------------------------------------------------- MP4
def _boxes(buf, start, end):
    pos = start
    while pos + 8 <= end:
        size, typ = struct.unpack(">I4s", buf[pos:pos + 8])
        hdr = 8
        if size == 1:
            size = struct.unpack(">Q", buf[pos + 8:pos + 16])[0]; hdr = 16
        elif size == 0:
            size = end - pos
        if size < hdr or pos + size > end:
            return
        yield typ, pos + hdr, pos + size
        pos += size


def _mp4(f, total):
    info = {"container": "mp4", "duration_ms": 0, "video": [], "audio": []}
    pos = 0
    moov = None
    while pos + 8 <= total:                                   # walk top-level boxes by their sizes: never reads mdat
        f.seek(pos)
        h = f.read(16)
        if len(h) < 8: break
        size, typ = struct.unpack(">I4s", h[:8])
        hdr = 8
        if size == 1 and len(h) >= 16: size = struct.unpack(">Q", h[8:16])[0]; hdr = 16
        elif size == 0: size = total - pos
        if size < hdr: break
        if typ == b"moov":
            if size - hdr > MAX_MOOV: info["error"] = "header too large to inspect"; return info
            f.seek(pos + hdr); moov = f.read(size - hdr); break
        pos += size
    if moov is None:
        info["error"] = "no movie header (moov) found"; return info
    for typ, a, b in _boxes(moov, 0, len(moov)):
        if typ == b"mvhd":
            ver = moov[a]
            if ver == 1: ts, dur = struct.unpack(">IQ", moov[a + 20:a + 32])
            else: ts, dur = struct.unpack(">II", moov[a + 12:a + 20])
            info["duration_ms"] = int(dur * 1000 / ts) if ts else 0
        elif typ == b"trak":
            _trak(moov, a, b, info)
    return info


def _trak(buf, a, b, info):
    kind = None; entry = None
    for typ, x, y in _boxes(buf, a, b):
        if typ != b"mdia": continue
        for t2, x2, y2 in _boxes(buf, x, y):
            if t2 == b"hdlr": kind = buf[x2 + 8:x2 + 12]
            elif t2 == b"minf":
                for t3, x3, y3 in _boxes(buf, x2, y2):
                    if t3 != b"stbl": continue
                    for t4, x4, y4 in _boxes(buf, x3, y3):
                        if t4 == b"stsd" and y4 - x4 >= 16:
                            esz, fourcc = struct.unpack(">I4s", buf[x4 + 8:x4 + 16])
                            entry = (fourcc, x4 + 16, y4)
    if not entry: return
    fourcc, body, end = entry
    if kind == b"vide":
        w = h = 0
        if end - body >= 28: w, h = struct.unpack(">HH", buf[body + 24:body + 28])
        info["video"].append({"codec": VIDEO_NAMES.get(fourcc, fourcc.decode("latin1").strip()), "w": w, "h": h})
    elif kind == b"soun":
        info["audio"].append(AUDIO_NAMES.get(fourcc, fourcc.decode("latin1").strip()))


# ---------------------------------------------------------------- Matroska
def _vint(buf, pos, keep_marker=False):
    first = buf[pos]
    n = 1
    mask = 0x80
    while n <= 8 and not (first & mask):
        n += 1; mask >>= 1
    if n > 8: raise ValueError("bad vint")
    val = first if keep_marker else first & (mask - 1)
    for i in range(1, n): val = (val << 8) | buf[pos + i]
    unknown = (not keep_marker) and val == (1 << (7 * n)) - 1
    return val, n, unknown


def _elements(buf, start, end):
    pos = start
    while pos < end:
        eid, n1, _ = _vint(buf, pos, True)
        size, n2, unknown = _vint(buf, pos + n1)
        body = pos + n1 + n2
        stop = end if unknown else min(end, body + size)
        yield eid, body, stop, unknown
        pos = stop


def _mkv(f):
    buf = f.read(MAX_MKV_HEAD)
    info = {"container": "matroska", "duration_ms": 0, "video": [], "audio": []}
    if b"webm" in buf[:64]: info["container"] = "webm"
    ts_scale = 1000000
    dur_raw = 0.0
    try:
        for eid, a, b, unk in _elements(buf, 0, len(buf)):
            if eid != 0x18538067: continue                      # Segment
            for e2, a2, b2, u2 in _elements(buf, a, b):
                if e2 == 0x1549A966:                            # Info
                    for e3, a3, b3, _ in _elements(buf, a2, b2):
                        if e3 == 0x2AD7B1: ts_scale = int.from_bytes(buf[a3:b3], "big")
                        elif e3 == 0x4489: dur_raw = struct.unpack(">f" if b3 - a3 == 4 else ">d", buf[a3:b3])[0]
                elif e2 == 0x1654AE6B:                          # Tracks
                    for e3, a3, b3, _ in _elements(buf, a2, b2):
                        if e3 == 0xAE: _mkv_track(buf, a3, b3, info)
                elif e2 == 0x1F43B675:                          # first Cluster: media data starts, headers are done
                    break
    except (ValueError, IndexError, struct.error):
        info["error"] = "header only partly readable"
    info["duration_ms"] = int(dur_raw * ts_scale / 1e6)
    return info


def _mkv_track(buf, a, b, info):
    ttype = 0; codec = ""; w = h = 0
    for eid, x, y, _ in _elements(buf, a, b):
        if eid == 0x83: ttype = int.from_bytes(buf[x:y], "big")
        elif eid == 0x86: codec = buf[x:y].decode("latin1")
        elif eid == 0xE0:
            for e2, x2, y2, _ in _elements(buf, x, y):
                if e2 == 0xB0: w = int.from_bytes(buf[x2:y2], "big")
                elif e2 == 0xBA: h = int.from_bytes(buf[x2:y2], "big")
    name = next((v for k, v in MKV_NAMES.items() if codec == k or codec.startswith(k + "/") or codec.startswith(k)), codec or "unknown")
    if ttype == 1: info["video"].append({"codec": name, "w": w, "h": h})
    elif ttype == 2: info["audio"].append(name)


# ---------------------------------------------------------------- what this TV will do with it
PLAYABLE_CONTAINERS = {"mp4", "matroska", "webm"}
UNSUPPORTED_CONTAINERS = {"avi", "wmv", "mpg", "mpeg", "flv", "asf"}      # the TV player (Media3) has no reader for these
OK_VIDEO = {"h264"}
MAYBE_VIDEO = {"hevc", "vp9", "av1", "mpeg4", "vp8"}
OK_AUDIO = {"aac", "mp3", "ac3", "eac3", "opus", "vorbis", "flac", "mp2"}
MAYBE_AUDIO = {"dts", "truehd", "alac"}


def verdict(info, ext=""):
    """('direct' | 'maybe' | 'unsupported' | 'unknown', reason). 'direct' means the container and codecs are ones this TV's player handles; it is still only a prediction until it has played."""
    cont = info.get("container", ext)
    if cont in UNSUPPORTED_CONTAINERS:
        return ("unsupported", "the %s container is not supported by this TV's player" % cont)
    if info.get("error") and not info.get("video") and not info.get("audio"):
        return ("unsupported", "the video index cannot be read: the file may be damaged, or its container is not supported") if "unreadable" in info["error"] or "moov" in info["error"] else ("unknown", info["error"])
    if cont not in PLAYABLE_CONTAINERS:
        return ("unknown", "the %s container was not inspected, so it is not known whether it plays" % (cont or ext))
    if not info.get("video"):
        return ("unsupported", "no video track found")
    notes = []
    for v in info["video"][:1]:
        if v["codec"] in OK_VIDEO: pass
        elif v["codec"] in MAYBE_VIDEO: notes.append("%s video may not play on this TV" % v["codec"])
        else: return ("unsupported", "unknown video codec %s" % v["codec"])
        if v.get("w", 0) > 1920: notes.append("%dp is larger than this TV can show smoothly" % v["h"])
    audio = info.get("audio", [])
    if audio and not any(a in OK_AUDIO for a in audio):
        notes.append("%s sound may be silent on this TV" % "/".join(sorted(set(audio))))
    return ("maybe", "; ".join(notes)) if notes else ("direct", "")
