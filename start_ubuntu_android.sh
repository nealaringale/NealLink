#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if [[ "${XDG_SESSION_TYPE:-}" != "x11" ]]; then
  echo "NealLink needs an Ubuntu X11 session for mouse capture."
  echo "Current session: ${XDG_SESSION_TYPE:-unknown}"
  echo "Log out and choose 'Ubuntu on Xorg' at the login screen."
  exit 1
fi

if ! command -v xdotool >/dev/null 2>&1; then
  echo "NealLink needs xdotool for seamless edge handoff."
  echo "Install it with: sudo apt install xdotool"
  exit 1
fi

if [[ ! -f "config/ubuntu_android.json" ]]; then
  echo "Error: config/ubuntu_android.json not found."
  exit 1
fi

echo "NealLink ready: move the mouse to the RIGHT EDGE to enter the tablet."
echo "Move the tablet cursor to its LEFT EDGE to return to Ubuntu."

exec python3 neallink_ubuntu.py --config config/ubuntu_android.json
