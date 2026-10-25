#!/usr/bin/env bash
# Captures the interaction and failure states under fixed conditions. Usage: capture_states.sh <outdir>
# Uses the test switches (log tags OmniOffline / OmniEmpty), which fake a dead network / an empty app list.
python3 -I "$(dirname "$0")/tvguard.py" || exit 3
OUT="${1:?outdir}"; TV="${TV:?set TV=<tv-ip>:5555}"; A="adb -s $TV shell"; mkdir -p "$OUT"
shot(){ for i in 1 2 3; do adb -s $TV exec-out screencap -p > "$OUT/.raw"; python3 -I -c "
d=open('$OUT/.raw','rb').read(); i=d.find(b'\x89PNG'); open('$1','wb').write(d[i:]) if i>=0 else print('retry')" | grep -q retry || break; sleep 2; done; }
k(){ $A input keyevent "$1"; sleep "${2:-4}"; }
fresh(){ $A am force-stop com.rasha.omni; sleep 3; $A input keyevent KEYCODE_WAKEUP; $A am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep "${1:-12}"; }
fresh; k KEYCODE_DPAD_RIGHT 5
$A input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 6; shot "$OUT/10_options_sheet.png"
k KEYCODE_DPAD_CENTER 6; k KEYCODE_DPAD_RIGHT 6; shot "$OUT/11_move_mode.png"
k KEYCODE_DPAD_LEFT 5; k KEYCODE_DPAD_CENTER 6; shot "$OUT/12_move_dropped.png"
fresh; k KEYCODE_DPAD_UP 4; k KEYCODE_DPAD_RIGHT 14; k KEYCODE_DPAD_DOWN 6; k KEYCODE_DPAD_RIGHT 5; k KEYCODE_DPAD_CENTER 7; shot "$OUT/13_watch_details.png"
$A setprop log.tag.OmniOffline DEBUG; fresh; k KEYCODE_DPAD_UP 4; k KEYCODE_DPAD_RIGHT 14; shot "$OUT/14_watch_offline.png"
$A setprop log.tag.OmniOffline INFO
$A setprop log.tag.OmniEmpty DEBUG; fresh; shot "$OUT/15_home_empty.png"; $A setprop log.tag.OmniEmpty INFO
fresh 3; shot "$OUT/16_cold_start_early.png"      # three seconds after launch: what a person sees while it loads
fresh; rm -f "$OUT/.raw"
