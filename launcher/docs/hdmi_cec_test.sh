#!/bin/bash
# Step 3 of the HDMI plan: does turning HDMI-CEC off stop the TV maker's app from taking over when OMNI tunes an input?
# Changes ONE global setting for one run and ALWAYS puts the original value back (trap), even if the run fails or you press Ctrl-C.
# Needs: TV idle (tvguard), the PlayStation switched ON, log.tag.OmniDebugOpen=DEBUG. Usage: docs/hdmi_cec_test.sh [host:port]
set -u
HOST="${1:-${TV:?usage: hdmi_cec_test.sh <tv-ip>:5555}}"; A="adb -s $HOST"
ORIG="$($A shell settings get global hdmi_control_enabled | tr -d '\r')"
restore() { if [ "$ORIG" = "null" ] || [ -z "$ORIG" ]; then $A shell settings delete global hdmi_control_enabled >/dev/null; else $A shell settings put global hdmi_control_enabled "$ORIG" >/dev/null; fi; echo "restored hdmi_control_enabled=$ORIG"; }
trap restore EXIT INT TERM
python3 -I "$(dirname "$0")/tvguard.py" || { echo "TV in use"; exit 1; }
$A shell setprop log.tag.OmniDebugOpen DEBUG; $A shell setprop log.tag.OmniState DEBUG
run() {
  $A shell input keyevent KEYCODE_HOME; sleep 3; $A logcat -c
  $A shell am start -n com.rasha.omni/.HdmiProbeActivity --es mode handoff --ei after_ms 5000 >/dev/null; sleep 15
  echo "  foreground: $($A shell dumpsys activity activities | grep -m1 ResumedActivity | grep -o 'u0 [^ ]*')"
  echo "  tvcenter launches: $($A logcat -d | grep -c 'START u0.*tvcenter')"
  $A logcat -d -s OmniState | grep -E "hdmi-overlay" | grep -v "panel state=" | cut -c34-120 | sed 's/^/  /'
  $A shell input keyevent KEYCODE_BACK; sleep 3
}
echo "== CEC ON (original value: $ORIG)"; run
$A shell settings put global hdmi_control_enabled 0; sleep 4
echo "== CEC OFF"; run
