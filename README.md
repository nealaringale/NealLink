# NealLink

Ubuntu → Android LAN mouse/keyboard bridge and Android companion.

## Current MVP
- Ubuntu/X11 captures mouse input with `pynput`.
- WebSocket server on TCP `24891`.
- Android companion connects over the same LAN.
- Android shows a movable NealLink cursor overlay.
- Click and scroll are executed through Android Accessibility gestures.
- GitHub Actions builds a debug APK automatically.

## Ubuntu

```bash
python3 -m pip install -r requirements-ubuntu.txt
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

Use an X11 session for the MVP:

```bash
echo $XDG_SESSION_TYPE
```

## Android APK

Open the repository in GitHub → **Actions** → **Build Android APK**. Download the `NealLink-debug-apk` artifact from the completed run.

Install the APK, open NealLink, enable the Accessibility Service, enter the Ubuntu WebSocket URL, and connect.

## Android limitation

Android's public SDK does not provide a third-party app with a universal desktop-style hover cursor or arbitrary global key injection. This MVP therefore uses an accessibility overlay cursor and accessibility gestures. A later release can add a stronger second-screen mode using a virtual display/streaming architecture.
