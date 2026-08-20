# NealLink

**One mouse. Many screens.**

NealLink is an Ubuntu → Android LAN pointer bridge. The intended interaction is simple: keep the tablet connected, use the mouse normally on Ubuntu, and when the mouse reaches the **right edge of the Ubuntu screen**, NealLink transfers pointer ownership to the tablet.

## v0.4.0

- Right-edge mouse handoff from Ubuntu to Android
- Relative mouse capture while the tablet owns the pointer
- Mouse re-centering on Ubuntu so you can keep moving without hitting the physical edge
- Move the tablet cursor to the **left edge** to hand ownership back to Ubuntu
- Remote click and scroll routing while tablet ownership is active
- Cleaner Android status flow and pointer overlay handling
- Versioned Android APK (`versionCode 4`, `versionName 0.4.0`)
- Pull requests now run the Android build before merge

## How the pointer handoff works

```text
Ubuntu screen
┌─────────────────────────────────────────────┐
│                                             │
│                              mouse →→→→→→→→ │ RIGHT EDGE
└─────────────────────────────────────────────┘
                                             ↓
                                      OWNERSHIP SWITCH
                                             ↓
Android tablet
┌─────────────────────────────────────────────┐
│  ●                                          │
│  NealLink cursor                            │
│                                             │
│                         move/click/scroll   │
│                                             │
└─────────────────────────────────────────────┘
                                             ↑
                                  LEFT EDGE = return
```

When Android owns the pointer, Ubuntu uses `xdotool` to keep the real desktop pointer near the center. Mouse movement is converted to relative deltas and sent over the WebSocket. This is what makes continuous movement possible instead of getting stuck at the Ubuntu screen edge.

## Ubuntu

Use an Ubuntu **X11** session for the current input-capture MVP.

```bash
python3 -m pip install --user -r requirements-ubuntu.txt
sudo apt install xdotool
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

Check the display session:

```bash
echo $XDG_SESSION_TYPE
```

For this version, it should say:

```text
x11
```

## Android

Install the latest `NealLink-release-apk` from GitHub Actions.

The tablet only needs:

1. NealLink open and connected to the Ubuntu WebSocket URL.
2. NealLink Accessibility enabled once.
3. Ubuntu and the tablet on the same LAN/hotspot.

No username, password, or account is required.

## Persistent Android updates

Android only allows an APK to update an existing installation when the new APK uses the same signing certificate. Stable releases therefore use a persistent release keystore configured through these GitHub repository secrets:

```text
NEALLINK_KEYSTORE_BASE64
NEALLINK_STORE_PASSWORD
NEALLINK_KEY_ALIAS
NEALLINK_KEY_PASSWORD
```

The private keystore must never be committed to GitHub.

## Android limitation

The current implementation transfers the **mouse/pointer**, clicks, and scroll. Android's public SDK does not provide unrestricted third-party global keyboard injection, so keyboard forwarding is a separate subsystem rather than something this MVP pretends to support universally. A future full second-screen version can add virtual-display rendering and a dedicated input channel.
