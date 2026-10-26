# Compact launcher and Now Playing

**Icons.** Home tiles are smaller and calmer: apps 164x92 dp (banner) or 92 dp (square icon), posters 104x156 dp, collapsed strips 48x42 dp; the focus lift is 3 dp, the scale 4% and the ring 2 dp. Apps keep their real artwork and colours; the focused app still lights the wallpaper in its own colour (the launcher's existing bloom), nothing glows permanently. Focus targets stay comfortably large for the sofa.

**Now Playing.** A small glass pill at the top right of Home (artwork, title, artist, thin progress line in the artwork's colour, a quiet playing/paused mark). It reads the active Android media session (Spotify, a cast receiver, any player that publishes one) through platform callbacks only: no polling, one bitmap held (at most 320 px), and a 1 Hz redraw of the progress line only while something plays and the pill is visible. It never takes focus: press Right at the end of a shelf to reach it, OK opens the larger view (soft artwork wash, time, previous / play-pause / next, only the controls the player supports), Up/Down/Back closes it. Fields the player does not publish are left out.

**Access.** Reading sessions needs OMNI's notification-listener access (it reads no notifications; same switch as the "casting" notices). Without it the pill simply never appears and the launcher is unchanged. Enable in Settings, or: `adb shell cmd notification allow_listener com.rasha.omni/com.rasha.omni.MediaListener`.

**Ambient.** The screensaver (OMNI Gallery) shows a small artwork-and-title capsule at the bottom right while a session is playing. It does not touch playback and ends on any key, like any screensaver. OMNI does not replace the Android lock screen or the Home role.

Developer check (debug-flagged builds only): `adb shell setprop log.tag.OmniDebugOpen DEBUG; adb shell am start -n com.rasha.omni/.MainActivity --es open nowplaying --es title T --es artist A --ei state 3`. It publishes a real media session, so the real pipeline is what you see.
