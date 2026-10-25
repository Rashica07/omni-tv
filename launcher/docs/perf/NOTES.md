
## 2026-10-24 Home frame cost investigation (honest record)
Home measured 7.8% janky at one point and 25-48% later, with identical or older code (the commit from the 7.8% run, rebuilt and re-run, gave 37%), and
with the wallpaper, bloom and blur all switched off (28%). A Compose control app measured on the same TV at the same time was clean (0%). Per-stage
frame stats on the slow frames: app CPU recording 0.3 ms, but 16 ms to issue/submit and 12 ms waiting to dequeue a buffer: the app is stalled by the
compositor, which was handling 40 visible layers (other apps' leftover surfaces: YouTube, Netflix, the old Aura and FLauncher, others).
Closing those apps would test it; not done because they are the owner's open apps. Treat the Home numbers as dependent on how many apps are open.
