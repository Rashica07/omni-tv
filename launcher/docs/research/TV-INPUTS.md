# This TV's inputs: what is exposed, and to whom

Captured 2026-10-24 with `adb shell` on the target TV. **Shell-level data is richer than what an app can read**, and the two are kept apart below.

## Shell view (needs DUMP; not available to apps)
`dumpsys tv_input`: nine pass-through inputs under `com.mediatek.tvinput`: tuner `HW0`/`HW7`, composite `HW1`, component `HW2`, VGA `HW3`, HDMI ports
`HW4`, `HW5`, `HW6`, plus an input named `HDMI300004` (a hot-plugged CEC device), and two app-provided channel inputs (`com.cltv.fast`, `com.haystack.android`).
Input states at capture time: HDMI ports and `HDMI300004` = 1 (connected, standby), composite/component/VGA = 2 (disconnected), tuner = 0 (connected).
`dumpsys hdmi_control`: three HDMI-in ports (ports 1-3, all CEC-capable, port 2 also ARC), CEC enabled. One CEC device was listed on port 3 with
`display_name: PlayStation 5` (device type 4, power status 1 = standby) at capture time.

## App view (public API only)
`TvInputManager.getTvInputList()`, `getInputState()`, `TvInputInfo.loadLabel()/loadCustomLabel()/getType()/getParentId()/isPassthroughInput()`.
`HdmiDeviceInfo` (the CEC name and device type) is a hidden API: an app learns a device's name only if the TV copied it into a `TvInputInfo` label.
What the app really receives on this TV is logged by OMNI (`log.tag.OmniState DEBUG`, lines starting `input id=`) and recorded in
`docs/research/TV-INPUTS-app-view.md` once measured. Until then, no claim is made that OMNI can display "PlayStation 5".

## OMNI's honest behaviour
- Shows the labels the TV gives; marks them "Name from your TV".
- A device type is either **set by the user** ("set by you"), **guessed from a name the TV supplied** ("guessed from the name your TV reports"),
  or absent. A port called "HDMI 2" is never turned into a console.
- Selecting an input asks the system TV app to show it (documented `TvContract.buildChannelUriForPassthroughInput`). If the TV refuses, OMNI says so.
