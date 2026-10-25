# Install (human version)

You need: an Android TV / Google TV on the same network as your computer, a computer with `adb`, Java 17 and the Android SDK build-tools (35), and about 20 minutes.
Nothing on the TV is deleted. You can go back to your TV's own launcher at any time (see **Undo**).

## 1. Let the computer talk to the TV
1. TV: *Settings > Device Preferences > About*, press **Build** (or *Android TV OS build*) 7 times to unlock developer options.
2. TV: *Settings > Device Preferences > Developer options*, turn on **USB debugging** and **Network debugging / ADB over network** (wording varies).
3. Note the TV's IP address (*Settings > Network*). Computer: `adb connect <tv-ip>:5555`, then accept the prompt on the TV. `adb devices` must list it as `device`.

## 2. Build and install the launcher
```
cd launcher
./build.sh /path/to/jdk-17          # needs ANDROID_SDK_ROOT (build-tools 35, platform android-35); creates your own omni.keystore on first run: keep it
TV=<tv-ip>:5555 ./deploy.sh
```
Optional film posters: put your own TMDB key in `launcher/tmdb.token` before building (https://www.themoviedb.org/settings/api). Never share an APK built with a key inside.

## 3. Make OMNI the Home screen (optional, reversible)
Press Home on the remote and pick OMNI as the default if Android asks. Some TVs force their own launcher; do not disable system packages unless you know the undo.

## 4. Apps (Cinema, Weather, ...)
```
cd android
# one-time: your own signing keys (the launcher's key is made by launcher/build.sh and is shared with Cinema so its signature permission means "an OMNI app")
keytool -genkeypair -keystore omni-os.keystore -storepass omni-os-local -keypass omni-os-local -alias omni -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=OMNI"
export JAVA_HOME=/path/to/jdk-17
echo "<your tmdb key>" > tmdb.token      # optional; Cinema does not need it when it reads a server library
./gradlew :apps:cinema:assembleRelease    # likewise :apps:weather, :apps:server, ...
adb -s <tv-ip>:5555 install -r apps/cinema/build/outputs/apk/release/cinema-release.apk
```
See `android/docs/APPS.md` for what each app does.

## 5. Your own film library (optional)
On the computer that will keep the films (Linux; Python 3.9+):
```
cd server && sudo ./deploy/install.sh        # read the script first; prints an 8-digit PIN
cp -r /path/to/your/films/* /srv/omni-media/Movies/
# posters/overviews (optional): save your TMDB key to /etc/omniptv/tmdb.token (owner root, group omniptv, mode 640),
# add  "tmdb_token_file": "/etc/omniptv/tmdb.token"  to /etc/omniptv/config.json, then: sudo systemctl restart omniptvd
```
In Cinema on the TV: *Sources > Add OMNI server*, enter `<computer-ip>:8765` and the PIN. The server only listens on your LAN; the install script opens the port to your local subnet only.
Films are indexed from file names like `Heat (1995).mkv` or `Heat.1995.1080p.BluRay.mkv`. Playback is the original file (no transcoding), so formats the TV cannot decode are reported as such.

## Undo
```
adb shell pm enable com.google.android.tvlauncher         # your TV's stock launcher, if you disabled it
adb uninstall com.rasha.omni                               # remove OMNI
sudo systemctl disable --now omniptvd                      # remove the server (server/deploy/uninstall.sh removes its files)
```
