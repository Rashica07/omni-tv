#!/usr/bin/env python3
"""Exit 0 if the TV is free to drive with key events, 3 if someone is using it.

"In use" = an app other than OMNI is in front AND a media session is PLAYING, or the TV maker's own TV app (live TV / HDMI input) is in front. Scripts that inject keys, force-stop OMNI or bring it to the
front must call this first (docs/capture*.sh, regress.py, bench.py, deploy.sh). A shared TV is not a test fixture.
Override only when you know nobody is watching: TVGUARD=off.
"""
import os, re, subprocess, sys

TV = os.environ.get("TV", "")
if not TV and os.environ.get("TVGUARD") != "off": sys.exit("set TV=<tv-ip>:5555 first")

def sh(*a):
    return subprocess.run(["adb", "-s", TV, "shell", *a], capture_output=True, text=True, timeout=60).stdout

def state():
    act = sh("dumpsys", "activity", "activities")
    m = re.search(r"ResumedActivity.*? u0 ([\w.]+)/", act)
    fg = m.group(1) if m else "?"
    sess = sh("dumpsys", "media_session")
    playing = []
    for blk in re.split(r"\n\s{4}(?=\S)", sess):
        pkg = re.search(r"package=([\w.]+)", blk)
        st = re.search(r"PlaybackState \{state=(\d+)", blk)
        if pkg and st and st.group(1) == "3": playing.append(pkg.group(1))
    return fg, playing

if __name__ == "__main__":
    if os.environ.get("TVGUARD") == "off": sys.exit(0)
    fg, playing = state()
    # live TV, the antenna and HDMI inputs are watched through the TV maker's own app and have no media session: that app in front means someone is watching
    TV_APPS = ("com.mediatek.wwtv.tvcenter", "com.android.tv", "com.google.android.tvlauncher")
    if fg in TV_APPS[:2]:
        print(f"TV in use: the TV's own app ({fg}) is in front (live TV or an HDMI input). Not touching it.", file=sys.stderr)
        sys.exit(3)
    if fg != "com.rasha.omni" and playing:
        print(f"TV in use: {fg} is in front and {', '.join(playing)} is playing. Not touching it.", file=sys.stderr)
        sys.exit(3)
    sys.exit(0)
