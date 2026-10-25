#!/usr/bin/env bash
# Captures the five reference screens under fixed conditions. Usage: capture.sh <outdir>
# Conditions: force-stop, cold launch via the Home intent, then scripted D-pad steps with long waits
# (adb input is slow on this TV). Netflix/DRM windows cannot be captured; none are visited here.
python3 -I "$(dirname "$0")/tvguard.py" || exit 3
OUT="${1:?outdir}"; TV="${TV:?set TV=<tv-ip>:5555}"
A="adb -s $TV shell"
shot(){ adb -s $TV exec-out screencap -p > "$OUT/.raw"; python3 -I -c "
d=open('$OUT/.raw','rb').read(); i=d.find(b'\x89PNG'); open('$1','wb').write(d[i:]) if i>=0 else print('no png')"; }
k(){ $A input keyevent "$1"; sleep "${2:-3.5}"; }
mkdir -p "$OUT"
$A input keyevent KEYCODE_WAKEUP; sleep 1
$A am force-stop com.rasha.omni; sleep 2
$A am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 10
shot "$OUT/01_home_first.png"
k KEYCODE_DPAD_RIGHT 5; shot "$OUT/02_home_second.png"
k KEYCODE_DPAD_RIGHT 5; k KEYCODE_DPAD_RIGHT 5; shot "$OUT/03_home_fourth.png"
k KEYCODE_DPAD_UP 5; k KEYCODE_DPAD_RIGHT 12; shot "$OUT/04_watch.png"
k KEYCODE_DPAD_RIGHT 8; shot "$OUT/05_apps.png"
k KEYCODE_DPAD_RIGHT 8; shot "$OUT/06_settings.png"
rm -f "$OUT/.raw"
