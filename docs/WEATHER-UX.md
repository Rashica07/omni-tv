# OMNI Weather: how it looks, feels and works

Weather follows the same rules as Cinema and the launcher (see `docs/CINEMA-UX.md`): the shared top bar, 64 dp / 30 dp safe area, glass capsules, "focused = solid ivory with dark content". Where Cinema is calm charcoal, Weather lets the sky supply the colour.

**Data** (unchanged): Open-Meteo (no key, CC BY 4.0), cached on disk for 30 minutes, city list chosen by the person (no location permission). Current conditions, 24 hourly values, 7 daily values, sunrise/sunset, UV, wind, humidity, pressure, rain probability. Open-Meteo's forecast API provides no weather alerts, so there is no alerts section: nothing is invented.

| Page | What it shows |
|---|---|
| **Today** | Place, date and how fresh the numbers are; the temperature as the primary element; condition with its icon; high, low, feels-like; one sentence about the next 12 hours (only when the hourly forecast supports it); a quiet row of wind, humidity, UV, rain chance and sunrise/sunset. OK refreshes, hold OK or Menu changes the city. |
| **Hourly** | A temperature curve with rain-chance bars and sunrise/sunset markers, and 24 focusable hour cards; the focused card moves a marker on the curve and fills the header. |
| **Week** | Seven aligned rows: day, icon, rain chance, low, a range bar against the week, high. Today is marked; the focused row names its weather. |

**The sky** is drawn procedurally from the real sunrise and sunset and the real condition: deep navy at night with stars and the moon in its real phase, blue in daylight with a glowing sun, warm amber near sunrise and sunset, grey-blue with streaks in rain, cool white with flakes in snow, dark with occasional flashes in a storm, drifting cloud layers sized by the reported cloud cover. Light shades keep the text readable on any sky. On low-memory televisions (`isLowRamDevice` or a small memory class) it uses fewer drops and cloud layers and redraws less often; with the system's animations off, nothing moves.

**Honesty.** A forecast older than 45 minutes is marked as such; if it is older than that *and* the last refresh failed, or older than three hours, the screen says "Last known, from X. Not live." in crimson and dims the temperature. Loading and error states say what is happening and offer Refresh. The developer preview (`sim=` extras, only with the debug flag) shows a crimson "SIMULATED PREVIEW" and changes only the look.
