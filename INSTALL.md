# Install (human version)

You need: an Android TV / Google TV on the same network as your computer, a computer with `adb`, Java 17 and the Android SDK build-tools (35), and about 20 minutes.
Nothing on the TV is deleted. You can go back to your TV's own launcher at any time (see **Undo**).

## 1. Let the computer talk to the TV
1. TV: *Settings > Device Preferences > About*, press **Build** (or *Android TV OS build*) 7 times to unlock developer options.
2. TV: *Settings > Device Preferences > Developer options*, turn on **USB debugging** and **Network debugging / ADB over network** (wording varies).
3. Note the TV's IP address (*Settings > Network*). Computer: `adb connect <tv-ip>:5555`, then accept the prompt on the TV. `adb devices` must list it as `device`.

## 2. Install OMNI
**Easiest:** download `omni.apk` from the Releases page and run `adb install -r omni.apk`.

**Build it yourself** (JDK 17, Android SDK with build-tools 35 and platform android-35):
```
cd android
echo "<your tmdb key>" > tmdb.token                      # optional: film posters for the Watch tab (https://www.themoviedb.org/settings/api); never share an APK built with a key inside
keytool -genkeypair -keystore omni.keystore -storepass android -keypass android -alias aura -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=OMNI"   # once: your own signing key, keep it (a new key cannot update an old install)
export JAVA_HOME=/path/to/jdk-17 ANDROID_SDK_ROOT=/path/to/Sdk
./gradlew :app:assembleRelease
TV=<tv-ip>:5555 ../launcher/deploy.sh                    # installs app/build/outputs/apk/release/app-release.apk and AOT-compiles it
```
That one APK contains the launcher, Cinema (with its player), Weather, the Server dashboard and the screensaver.

## 3. Make OMNI the Home screen (optional, reversible)
Press Home on the remote and pick OMNI as the default if Android asks. Some TVs force their own launcher; do not disable system packages unless you know the undo.

## 4. The screensaver
OMNI > Settings > Screensaver downloads landscape photos. To use it as the TV's idle screen: TV Settings > Device Preferences > Screen saver > *OMNI Gallery* (or with adb: `settings put secure screensaver_components com.rasha.omni/com.rasha.omni.gallery.OmniDream`).

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
adb uninstall com.rasha.omni                               # remove OMNI (Cinema, Weather, Server and the screensaver go with it)
adb shell settings put secure screensaver_components com.google.android.backdrop/.Backdrop   # if you had chosen the OMNI screensaver
sudo systemctl disable --now omniptvd                      # remove the server (server/deploy/uninstall.sh removes its files)
```
