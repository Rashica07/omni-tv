#!/usr/bin/env python3
"""On-device regression suite for OMNI. Drives the TV with scripted keys and asserts on the app's own
OmniState log events (silent unless enabled by this script). Real navigation paths, injected key events:
this is NOT validation with a physical remote.

Usage: regress.py [test-name ...]     (no args = all)      Exit code 1 if any check fails.
Slow by design: `adb input` takes ~1 s per key on this TV.
"""
import os, re, subprocess, sys, time

TV = os.environ["TV"]
PKG = "com.rasha.omni"
DUMMY_APK = os.environ.get("DUMMY_APK", "")      # optional: apk with a LEANBACK launcher activity for the uninstall test
R, L, U, D, C, B = ("KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN",
                    "KEYCODE_DPAD_CENTER", "KEYCODE_BACK")
results, failures = [], []

def adb(*a, timeout=120):
    return subprocess.run(["adb", "-s", TV, *a], capture_output=True, text=True, timeout=timeout).stdout

def shell(*a): return adb("shell", *a)
def key(k, wait=3.6): shell("input", "keyevent", k); time.sleep(wait)
def hold_ok(wait=4.5): shell("input", "keyevent", "--longpress", C); time.sleep(wait)
def log(): return adb("logcat", "-d", "-s", "OmniState:D")
def clear(): adb("logcat", "-c")
def crashes(): return adb("logcat", "-d", "-b", "crash").strip()
def prop(name, val): shell("setprop", "log.tag." + name, val)

def fresh_home(wait=10):
    shell("am", "force-stop", PKG); time.sleep(2)
    shell("input", "keyevent", "KEYCODE_WAKEUP")
    shell("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"); time.sleep(wait)

def events(pattern, text=None):
    return re.findall(pattern, text if text is not None else log())

def last_focus(text=None, page="Home"):
    """Last focus event of one page (hidden pages also emit events when their data loads)."""
    m = events(r"focus page=" + page + r" shelf=(\S+) item=(\S+) idx=(\d+)", text)
    return m[-1] if m else None

def order(shelf="fav", text=None, page="Home"):
    m = re.findall(r"shelf page=" + page + " " + shelf + r"=(\S+)", text if text is not None else log())
    return m[-1].split(",") if m else []

def wait_for(pattern, timeout=25, text_fn=None):
    """Polls the OmniState log until the regex appears; returns the log text (even on timeout)."""
    end = time.time() + timeout
    while True:
        t = (text_fn or log)()
        if re.search(pattern, t) or time.time() > end: return t
        time.sleep(1.5)

def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    if not cond: failures.append(name)
    print(("PASS " if cond else "FAIL ") + name + (("  [" + detail + "]") if detail and not cond else ""))

# ---------------------------------------------------------------- tests
def t_start():
    clear(); fresh_home(); t = log()
    check("start: Home tab selected", "tab=Home" in t, t[-200:])
    f = last_focus(t)
    check("start: first Favorites item focused", f is not None and f[0] == "fav" and f[2] == "0", str(f))

def t_shelf_memory():
    clear(); fresh_home(); key(R); key(R)
    a = last_focus(); check("shelf memory: reached index 2", a and a[2] == "2", str(a))
    key(D); b = last_focus(); check("shelf memory: Down moves to the next shelf", b and b[0] != "fav", str(b))
    key(U); c = last_focus()
    check("shelf memory: Up restores the same item by identity", c and a and c[0] == "fav" and c[1] == a[1] and c[2] == "2", f"{a} -> {c}")

def t_up_goes_to_selected_tab():
    clear(); fresh_home(); key(U); key(R)
    t = log()
    check("Up from the first shelf lands on the header and Right moves to Watch (not a nearest-tab jump)",
          "tab=Watch" in t, t[-300:])
    key(L, 5); check("Left returns to Home tab", log().count("tab=Home") >= 2)

def t_back_hierarchy():
    clear(); fresh_home(); key(B)
    check("Back from content goes to the header", "back to=header" in log())
    key(B); check("Back on the Home header does nothing (launcher root)", "back to=none" in log())
    key(R, 5); key(B)
    check("Back from another tab goes to Home", "back to=home" in log())
    check("OMNI is still in front after repeated Back", PKG in shell("dumpsys", "activity", "activities").split("ResumedActivity")[1][:200])

def t_sheet_focus_restoration():
    clear(); fresh_home(); key(R); before = last_focus()
    hold_ok(); check("hold OK opens the options sheet", "sheet=open" in log())
    key(B, 5); check("Back closes the sheet", "sheet=closed" in log())
    key(R); after = last_focus()
    check("focus is restored after the sheet (next Right moves one item on)",
          before and after and int(after[2]) == int(before[2]) + 1, f"{before} -> {after}")

def t_launch_and_return():
    clear(); fresh_home(); key(R); f = last_focus(); key(C, 10)
    l = events(r"launch pkg=(\S+)")
    check("OK launches the focused app", l and f and l[-1] == f[1], f"{f} {l}")
    pid0 = shell("pidof", PKG).strip()
    shell("input", "keyevent", "KEYCODE_HOME"); time.sleep(6)
    t = wait_for(r"focus page=Home shelf=\S+ item=" + re.escape(f[1]) + r" idx=" + f[2] + r"[\s\S]*$", 20)
    g = last_focus(t)
    pid1 = shell("pidof", PKG).strip()
    if pid0 != pid1: print("INFO the system recycled OMNI's process while the app was in front (restore path used)")
    check("returning Home restores the same item", g and f and g[1] == f[1], f"{f} -> {g}")
    check("OMNI is the foreground home again", PKG in shell("dumpsys", "activity", "activities").split("ResumedActivity")[1][:200])

def t_watchnext_shelf():
    clear(); fresh_home(); t = log()
    if not re.findall(r"shelf page=Home wn=(\S+)", t):
        print("SKIP watch next (no Watch Next data published on this TV right now)"); return
    key(D); f = last_focus()
    check("watch next: Down from Favorites reaches Continue watching", f and f[0] == "wn", str(f))
    hold_ok(); check("watch next: no options sheet for publisher-owned items", "sheet=open" not in log())
    key(C, 14); l = events(r"launch pkg=(\S+) via=watchnext")
    check("watch next: OK launches through a sanitised intent", bool(l), str(l))
    fg = shell("dumpsys", "activity", "activities").split("ResumedActivity")[1][:200]
    check("watch next: the publisher's own app is in front", bool(l) and l[-1] in fg, fg)
    shell("input", "keyevent", "KEYCODE_HOME"); time.sleep(10)
    check("watch next: Home returns to OMNI", PKG in shell("dumpsys", "activity", "activities").split("ResumedActivity")[1][:200])

def t_move_persists():
    clear(); fresh_home(); orig = order(); check("move: favorites order readable", len(orig) >= 3, str(orig))
    if len(orig) < 3: return
    hold_ok(); key(C, 7)                      # first sheet option is Move
    key(R, 4)
    check("move: Right swaps the item", "moved shelf=fav" in log() and "delta=1" in log())
    key(C, 6); check("move: OK drops it", "move-end" in log())
    shell("am", "force-stop", PKG); time.sleep(2); clear(); fresh_home(); moved = order()
    check("move: new order survives a restart", moved[:3] == [orig[1], orig[0], orig[2]], f"{orig[:3]} -> {moved[:3]}")
    # after the restart focus is on the first item (the one that moved up); moving it Right swaps the pair back
    hold_ok(); key(C, 7); key(R, 4); key(C, 6)          # undo
    shell("am", "force-stop", PKG); time.sleep(2); clear(); fresh_home()
    check("move: undo restores the original order", order()[:3] == orig[:3], f"{orig[:3]} -> {order()[:3]}")

def t_reset_confirmation():
    clear(); fresh_home(); key(U); key(R, 4); key(R, 4); key(R, 5)   # Settings tab
    check("settings: tab selected", "tab=Settings" in log())
    key(D, 4)
    for _ in range(8): key(D, 3.3)
    key(C, 2); t = wait_for(r"sheet=open title=Reset layout\?")
    check("reset asks for confirmation instead of acting", "sheet=open title=Reset layout?" in t, t[-250:])
    key(B, 5); check("Back cancels the confirmation", "sheet=closed" in log())

def t_watch_offline_recovery():
    prop("OmniOffline", "DEBUG"); clear(); fresh_home(); key(U); key(R, 12)
    check("watch: offline state is reported", "watch state=offline" in log(), log()[-200:])
    prop("OmniOffline", "INFO"); key(D, 4); key(C, 14)
    check("watch: Try again recovers once the network is back", "watch state=loaded" in log(), log()[-200:])

def t_empty_state():
    prop("OmniEmpty", "DEBUG"); clear(); fresh_home(); t = log()
    check("home: empty state is shown when there are no apps", "home state=empty" in t, t[-200:])
    prop("OmniEmpty", "INFO")
    check("home: no crash in the empty state", crashes() == "", crashes()[:200])

def t_uninstall_recovery():
    if not DUMMY_APK or not os.path.exists(DUMMY_APK): print("SKIP uninstall recovery (set DUMMY_APK)"); return
    adb("install", "-r", DUMMY_APK); time.sleep(8)
    clear(); fresh_home(); key(U); key(R, 4); key(R, 8)        # Apps tab
    key(D, 4)
    check("uninstall: the new app is listed", "com.rasha.omnitest" in " ".join(re.findall(r"shelf page=Apps az\d+=(\S+)", log())), log()[-200:])
    key(D, 4)                                                    # onto the Recently installed shelf if present
    adb("uninstall", "com.rasha.omnitest"); time.sleep(9)
    t = log()
    check("uninstall: no crash and the list drops the app", crashes() == "" and "com.rasha.omnitest" not in " ".join(re.findall(r"shelf page=Apps az\d+=(\S+)", t)[-3:]), crashes()[:200])
    check("uninstall: focus lands on a remaining item", last_focus(t, "Apps") is not None and last_focus(t, "Apps")[1] != "com.rasha.omnitest", str(last_focus(t, "Apps")))

def t_latency():
    prop("OmniLat", "DEBUG"); clear(); fresh_home(12)
    shell("input", "keyevent", *([R] * 6)); time.sleep(5)
    v = [int(x) for x in re.findall(r"key=\S+ ms=(\d+)", adb("logcat", "-d", "-s", "OmniLat:D"))]
    prop("OmniLat", "INFO")
    if not v: check("latency: samples collected", False, "no OmniLat lines"); return
    v.sort(); p50, mx = v[len(v) // 2], v[-1]
    print(f"INFO app-side input->first draw (ms): n={len(v)} p50={p50} max={mx}")
    check("latency: input to first draw p50 under 60 ms", p50 < 60, str(v))

TESTS = [t_start, t_shelf_memory, t_up_goes_to_selected_tab, t_back_hierarchy, t_sheet_focus_restoration,
         t_launch_and_return, t_watchnext_shelf, t_move_persists, t_reset_confirmation, t_watch_offline_recovery, t_empty_state,
         t_uninstall_recovery, t_latency]

if __name__ == "__main__":
    if subprocess.run(["python3", "-I", os.path.join(os.path.dirname(os.path.abspath(__file__)), "tvguard.py")]).returncode: sys.exit(3)
    prop("OmniState", "DEBUG")
    want = set(sys.argv[1:])
    for t in TESTS:
        if want and t.__name__ not in want and t.__name__[2:] not in want: continue
        print("---", t.__name__)
        try: t()
        except Exception as e: check(t.__name__ + " (exception)", False, repr(e))
    prop("OmniState", "INFO")
    print(f"\n{len(results) - len(failures)}/{len(results)} checks passed" + (", FAILED: " + ", ".join(failures) if failures else ""))
    sys.exit(1 if failures else 0)
