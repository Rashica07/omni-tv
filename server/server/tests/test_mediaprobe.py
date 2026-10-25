import os, struct, sys, tempfile, unittest
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import mediaprobe as probe


def box(typ, payload=b""):
    return struct.pack(">I4s", 8 + len(payload), typ) + payload


def mp4(vcodec=b"avc1", acodec=b"mp4a", w=1280, h=720, secs=3600, moov_first=True, with_audio=True):
    mvhd = box(b"mvhd", struct.pack(">B3xIIII", 0, 0, 0, 1000, secs * 1000) + b"\0" * 80)
    def trak(kind, fourcc, entry_extra):
        hdlr = box(b"hdlr", b"\0" * 8 + kind + b"\0" * 13)
        entry = box(fourcc, b"\0" * 6 + b"\0\1" + entry_extra)
        stsd = box(b"stsd", b"\0\0\0\0" + struct.pack(">I", 1) + entry)
        return box(b"trak", box(b"mdia", hdlr + box(b"minf", box(b"stbl", stsd))))
    v = trak(b"vide", vcodec, b"\0" * 16 + struct.pack(">HH", w, h) + b"\0" * 50)
    a = trak(b"soun", acodec, b"\0" * 20) if with_audio else b""
    moov = box(b"moov", mvhd + v + a)
    ftyp = box(b"ftyp", b"isom\0\0\2\0isomiso2")
    mdat = box(b"mdat", b"\0" * 5000)
    return ftyp + moov + mdat if moov_first else ftyp + mdat + moov


def vint(n):
    for ln in range(1, 9):
        if n < (1 << (7 * ln)) - 1: return ((1 << (7 * ln)) | n).to_bytes(ln, "big")
    raise ValueError


def el(eid, payload): return eid.to_bytes((eid.bit_length() + 7) // 8, "big") + vint(len(payload)) + payload


def mkv(vcodec=b"V_MPEG4/ISO/AVC", acodec=b"A_AAC", w=1920, h=1080, doc=b"matroska", secs=7200):
    ebml = el(0x1A45DFA3, el(0x4282, doc))
    info = el(0x1549A966, el(0x2AD7B1, (1000000).to_bytes(4, "big")) + el(0x4489, struct.pack(">f", secs * 1000.0)))
    vt = el(0xAE, el(0x83, b"\1") + el(0x86, vcodec) + el(0xE0, el(0xB0, w.to_bytes(2, "big")) + el(0xBA, h.to_bytes(2, "big"))))
    at = el(0xAE, el(0x83, b"\2") + el(0x86, acodec))
    tracks = el(0x1654AE6B, vt + at)
    cluster = el(0x1F43B675, b"\0" * 2000)
    return ebml + el(0x18538067, info + tracks + cluster)


class ProbeTests(unittest.TestCase):
    def write(self, data, name="a.bin"):
        d = tempfile.mkdtemp(); p = os.path.join(d, name); open(p, "wb").write(data); return p

    def test_mp4_h264(self):
        i = probe.probe(self.write(mp4(), "x.mp4"))
        self.assertEqual((i["container"], i["duration_ms"], i["video"][0]["codec"], i["video"][0]["w"], i["video"][0]["h"], i["audio"]), ("mp4", 3600000, "h264", 1280, 720, ["aac"]))
        self.assertEqual(probe.verdict(i)[0], "direct")

    def test_mp4_with_moov_at_the_end(self):
        i = probe.probe(self.write(mp4(moov_first=False), "x.mp4"))
        self.assertEqual(i["duration_ms"], 3600000)

    def test_mp4_hevc_dts_is_maybe_with_reasons(self):
        v, why = probe.verdict(probe.probe(self.write(mp4(vcodec=b"hvc1", acodec=b"dtsc"), "x.mp4")))
        self.assertEqual(v, "maybe"); self.assertIn("hevc", why); self.assertIn("dts", why)

    def test_big_picture_noted(self):
        v, why = probe.verdict(probe.probe(self.write(mp4(w=3840, h=2160), "x.mp4")))
        self.assertEqual(v, "maybe"); self.assertIn("2160", why)

    def test_mkv(self):
        i = probe.probe(self.write(mkv(), "x.mkv"))
        self.assertEqual((i["container"], i["duration_ms"], i["video"][0]["codec"], i["video"][0]["h"], i["audio"]), ("matroska", 7200000, "h264", 1080, ["aac"]))
        self.assertEqual(probe.verdict(i)[0], "direct")

    def test_webm_and_codec_names(self):
        i = probe.probe(self.write(mkv(vcodec=b"V_VP9", acodec=b"A_OPUS", doc=b"webm"), "x.webm"))
        self.assertEqual((i["container"], i["video"][0]["codec"], i["audio"]), ("webm", "vp9", ["opus"]))
        i = probe.probe(self.write(mkv(acodec=b"A_DTS/HD"), "x.mkv"))
        self.assertEqual(i["audio"], ["dts"])

    def test_unsupported_container_is_said_plainly(self):
        v, why = probe.verdict(probe.probe(self.write(b"RIFF\0\0\0\0AVI LIST" + b"\0" * 100, "x.avi")), "avi")
        self.assertEqual(v, "unsupported"); self.assertIn("avi", why)

    def test_malformed_files_never_raise(self):
        for data in (b"", b"\0" * 7, mp4()[:20], mkv()[:30], b"\x1a\x45\xdf\xa3\xff\xff\xff\xff", os.urandom(300)):
            for name in ("x.mp4", "x.mkv", "x.avi"):
                i = probe.probe(self.write(data, name))
                self.assertIn("container", i)
                self.assertIn(probe.verdict(i, name.rsplit(".", 1)[1])[0], ("unsupported", "unknown", "maybe", "direct"))

    def test_missing_file(self):
        i = probe.probe("/nonexistent/file.mp4")
        self.assertIn("error", i)


if __name__ == "__main__":
    unittest.main()
