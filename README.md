# NealLink

**One keyboard. Many screens.**

NealLink is an Ubuntu → Android LAN companion for controlling an Android tablet with pointer events from the Ubuntu machine.

## v0.3.0

- Polished Android UI with persistent server address
- NealLink logo and launcher branding
- LAN WebSocket support (`ws://`)
- Accessibility setup/status flow
- Versioned Android APK (`versionCode 3`, `versionName 0.3.0`)
- Debug and release APK artifacts from GitHub Actions
- Persistent signing support for future in-place Android updates

## Ubuntu

Use an Ubuntu **X11** session for the current input-capture MVP.

```bash
python3 -m pip install --user -r requirements-ubuntu.txt
./start_ubuntu_android.sh
```

Find the LAN IP:

```bash
hostname -I
```

The Android URL is:

```text
ws://<UBUNTU-IP>:24891
```

Check the display session with:

```bash
echo $XDG_SESSION_TYPE
```

For the MVP, prefer:

```text
x11
```

## Android APK

Open **GitHub → Actions → Build Android APK** and download the completed artifact.

Use **`NealLink-release-apk`** for normal releases once persistent signing is configured. The debug artifact is useful for testing but is not suitable for guaranteed in-place upgrades across GitHub-hosted runners.

### Android Accessibility on Xiaomi / HyperOS

When the APK is sideloaded, Android may mark Accessibility as a restricted setting.

Open **NealLink → Open App Settings → ⋮ → Allow restricted settings**, then return to **Accessibility → Downloaded apps → NealLink** and enable it.

## Persistent signing for in-place updates

Android only allows an APK to update an existing installation when the new APK is signed with the same certificate.

The older NealLink builds were GitHub Actions debug builds, so their signing key is not available anymore. That means **one migration install is unavoidable** before stable signing is established.

After that migration, configure these GitHub repository secrets:

```text
NEALLINK_KEYSTORE_BASE64
NEALLINK_STORE_PASSWORD
NEALLINK_KEY_ALIAS
NEALLINK_KEY_PASSWORD
```

Generate a new release keystore locally:

```bash
keytool -genkeypair -v \
  -keystore neallink-release.jks \
  -alias neallink \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Convert it to base64 for the `NEALLINK_KEYSTORE_BASE64` secret:

```bash
base64 -w 0 neallink-release.jks
```

The signing key itself should never be committed to GitHub. The repository `.gitignore` already excludes local keystore files.

## Current Android limitation

Android's public SDK does not provide a third-party app with a universal desktop-style hover cursor or unrestricted global keyboard injection. This MVP therefore uses an Accessibility overlay cursor and Accessibility gestures. A later release can add a stronger second-screen mode using a virtual display + low-latency streaming architecture.
