# What an ordinary launcher can and cannot replace on Android TV

Verified against this TV (Vestel / MediaTek, Android 11) where marked D; documentation otherwise.

## OMNI controls (it is the Home role holder, D: `dumpsys role` HOME = `com.rasha.omni`)
- The Home screen and everything drawn in OMNI's own window: navigation, shelves, categories, Watch, Settings shortcuts, options sheets.
- App discovery and launching (LEANBACK + LAUNCHER activities, scoped `<queries>`), favourites, ordering, hiding (all OMNI-side data).
- Reading the TV's public input list and state, and asking the system to show an input (`TvContract` pass-through URI).
- Reading Watch Next items apps publish, with the user-grantable `READ_TV_LISTINGS` permission (D: protection level "dangerous").

## Public APIs, with limits
- `TvInputManager`: list of inputs, labels, types, connection state. **No** CEC device details (`HdmiDeviceInfo` is hidden), no signal
  information, no way to switch silently: the system TV app does the switching.
- `TvContract` programs: only what publishers expose; reading all channels needs a system-signature permission (`ACCESS_ALL_EPG_DATA`, D).
- Package visibility on Android 11: only packages matching declared `<queries>`.

## Not replaceable without platform privileges
| Surface | Why |
|---|---|
| Volume / mute / source OSD, the input picker, the TV's own menu | drawn by the manufacturer's TV app (here `com.mediatek.wwtv.tvcenter` and Vestel overlays), which owns the hardware inputs |
| System Settings app | a separate privileged app; OMNI can only open its pages |
| HDMI-CEC control (power on/off of devices, one-touch play, device names) | `HdmiControlManager` is a system API (hidden), needs `HDMI_CEC` system permission |
| Live TV tuner UI and EPG | the TV's own TV input service |
| Notifications, permission and install dialogs, the system keyboard | system UI |
| Boot logo, shutdown/power menu, screen saver choice | firmware / system settings |
| Replacing the Home *role* for other flows (e.g. the dedicated source button) | the remote's source/guide keys are handled by the manufacturer's key service (`com.vestel.tv.keycontroller`) |

## Manufacturer-specific (documented, not used)
This TV ships MediaTek/Vestel services (`com.mediatek.tv.service`, `com.mediatek.tvinput`, `com.vestel.tv.*`). Their AIDL/Binder interfaces are
private, undocumented, and signature-protected; using them would make OMNI break on any other TV and on updates. They are not used.
`dumpsys hdmi_control` (shell only, needs the DUMP permission) shows richer CEC data than apps can read, see TV-INPUTS.md.

## Practical consequences for OMNI's design
- Inputs are shown from the public API and labelled honestly; switching hands over to the system.
- Anything that needs the manufacturer's UI (volume, source list) is left to it; OMNI never pretends to replace it.
- Do not disable manufacturer components: nothing in OMNI requires it, and the project rules forbid it.
