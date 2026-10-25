#!/usr/bin/env python3
"""Reproducible OMNI benchmark: drives the TV with scripted keys and reads gfxinfo framestats.

Usage: bench.py <scenario> [label]      scenarios: home | apps | watch | tv | all
Method: Home intent, one unmeasured warm-up pass, `gfxinfo reset`, one measured pass, then parse
`dumpsys gfxinfo com.rasha.omni framestats` (per-frame CSV) and `dumpsys meminfo`.
Frame time = FRAME_COMPLETED - INTENDED_VSYNC. Input latency = HANDLE_INPUT_START - OLDEST_INPUT_EVENT.
Results append to docs/perf/results.jsonl. Needs adb access to the TV; key injection is slow on this TV
(~1 s per `input` call), so waits are long and scenarios are short.
"""
import json, re, subprocess, sys, time, statistics, datetime, os

TV = os.environ["TV"]
PKG = "com.rasha.omni"
R, L, U, D, C = "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_CENTER"

def adb(*a, check=False):
    return subprocess.run(["adb", "-s", TV, *a], capture_output=True, text=True, timeout=120).stdout

def key(k, wait=3.2):
    adb("shell", "input", "keyevent", k); time.sleep(wait)

SCENARIOS = {
    # Home: move along the shelf, switch shelf and back, return to the start
    "home":  [(R,3.2),(R,3.2),(R,3.2),(D,3.8),(R,3.2),(U,3.8),(L,3.2),(L,3.2),(L,3.2)],
    # Apps tab: go there via the header, walk down and across, come back
    "apps":  [(U,3),(R,6),(R,6),(D,4),(D,3.2),(R,3.2),(R,3.2),(D,3.2),(D,4),(U,3.2),(U,3.2),(L,3.2),(L,3.2),(U,4),(U,4),(L,6),(L,6)],
    # Watch tab: header -> Watch, into the shelf/rows, across, back out
    "watch": [(U,3),(R,6),(D,4),(R,3.2),(R,3.2),(D,4),(U,3.2),(U,3),(R,3.2),(R,3.2),(L,3.2),(L,3.2),(L,3.2)],
    # TV tab: header -> TV (three tab switches), toolbar, along Kosovo, down through Albania to the tuner and back, into the rail and out
    # short TV profile for elimination runs
    "tvs":   [(U,3),(R,4),(R,4),(R,5),(D,3.2),(D,3.2),(R,3.2),(R,3.2),(R,3.2),(R,3.2),(D,3.8),(U,3.8),(R,3.2),(R,3.2),(L,3.2),(L,3.2)],
    "homes": [(R,3),(R,3),(R,3),(L,3),(L,3),(L,3),(R,3),(L,3)],
    "tv":    [(U,3),(R,4),(R,4),(R,5),(D,3.2),(D,3.2),(R,3.2),(R,3.2),(R,3.2),(D,3.8),(R,3.2),(R,3.2),(D,3.8),(U,3.8),(U,3.2),(L,3.2),(L,3.2),
              (R,3.2),(R,3.2),(R,3.2),(R,3.2),(R,3.2),(R,3.2),(R,3.2),(R,3.2),(R,3.2),(R,3.2),(R,3.2),(D,3.2),(D,3.2),(L,3.2)],
}

COLS = ["Flags","IntendedVsync","Vsync","OldestInputEvent","NewestInputEvent","HandleInputStart","AnimationStart",
        "PerformTraversalsStart","DrawStart","SyncQueued","SyncStart","IssueDrawCommandsStart","SwapBuffers",
        "FrameCompleted","DequeueBufferDuration","QueueBufferDuration","GpuCompleted"]
NOINPUT = 9223372036854775807   # OldestInputEvent sentinel for frames with no input

def read_frames():
    """framestats only keeps the last 120 frames, so callers poll and de-duplicate by IntendedVsync."""
    txt = adb("shell", "dumpsys", "gfxinfo", PKG, "framestats")
    out, on, idx = {}, False, None
    for line in txt.splitlines():
        if line.startswith("---PROFILEDATA---"):
            on = not on; continue
        if not on: continue
        if line.startswith("Flags"):
            idx = {n: i for i, n in enumerate(line.rstrip(",").split(","))}; continue
        if idx and re.match(r"^\d+,", line):
            f = [int(x) for x in line.rstrip(",").split(",") if x != ""]
            if len(f) > idx["FrameCompleted"]: out[f[idx["IntendedVsync"]]] = (f, idx)
    return out

def run_pass(steps, collect=None):
    for k, w in steps:
        key(k, w)
        if collect is not None: collect.update(read_frames())

def pct(v, p):
    v = sorted(v); return v[min(len(v)-1, int(len(v)*p/100))] if v else 0

def measure(name, label):
    steps = SCENARIOS[name]
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"); time.sleep(8)
    run_pass(steps)                                   # warm-up
    adb("shell", "dumpsys", "gfxinfo", PKG, "reset"); time.sleep(1)
    frames = {}
    run_pass(steps, frames)                           # measured pass, polled after every step
    ft, gpu, lat = [], [], []
    for f, idx in frames.values():
        if f[idx["Flags"]] & 1: continue              # flag bit 0 = window layout changed: not a normal frame
        t0 = f[idx["IntendedVsync"]]
        ft.append((f[idx["FrameCompleted"]] - t0) / 1e6)
        if "GpuCompleted" in idx and len(f) > idx["GpuCompleted"] and f[idx["GpuCompleted"]] > t0:
            gpu.append((f[idx["GpuCompleted"]] - t0) / 1e6)
        oi = f[idx["OldestInputEvent"]]
        if oi != NOINPUT and f[idx["HandleInputStart"]] > oi: lat.append((f[idx["HandleInputStart"]] - oi) / 1e6)
    summary = adb("shell", "dumpsys", "gfxinfo", PKG)
    sj = re.search(r"Janky frames: (\d+) \(([\d.]+)%\)", summary); st = re.search(r"Total frames rendered: (\d+)", summary)
    mem = adb("shell", "dumpsys", "meminfo", PKG)
    m = re.search(r"TOTAL PSS:\s+(\d+)", mem) or re.search(r"TOTAL\s+(\d+)", mem)
    res = {
        "when": datetime.datetime.now().isoformat(timespec="seconds"), "label": label, "scenario": name,
        "frames_sampled": len(ft), "frames_total_gfxinfo": int(st.group(1)) if st else None,
        "p50_ms": round(pct(ft, 50), 1), "p90_ms": round(pct(ft, 90), 1), "p95_ms": round(pct(ft, 95), 1),
        "p99_ms": round(pct(ft, 99), 1), "max_ms": round(max(ft), 1) if ft else 0,
        "over_16_7ms": sum(1 for x in ft if x > 16.7), "over_33ms": sum(1 for x in ft if x > 33.4),
        "jank_pct_sampled": round(100 * sum(1 for x in ft if x > 16.7) / max(1, len(ft)), 2),
        "jank_pct_gfxinfo": float(sj.group(2)) if sj else None,
        "worst5_ms": [round(x, 1) for x in sorted(ft, reverse=True)[:5]],
        "gpu_p95_ms": round(pct(gpu, 95), 1), "gpu_max_ms": round(max(gpu), 1) if gpu else 0,
        "input_latency_p50_ms": round(pct(lat, 50), 1), "input_latency_max_ms": round(max(lat), 1) if lat else 0,
        "pss_kb": int(m.group(1)) if m else None,
    }
    os.makedirs("docs/perf", exist_ok=True)
    with open("docs/perf/results.jsonl", "a") as f: f.write(json.dumps(res) + "\n")
    print(json.dumps(res))

if __name__ == "__main__":
    if subprocess.run(["python3", "-I", os.path.join(os.path.dirname(os.path.abspath(__file__)), "tvguard.py")]).returncode: sys.exit(3)
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    label = sys.argv[2] if len(sys.argv) > 2 else "unlabelled"
    for n in (SCENARIOS if which == "all" else [which]): measure(n, label)
