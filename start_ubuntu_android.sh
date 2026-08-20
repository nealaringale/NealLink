#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if [[ "${XDG_SESSION_TYPE:-}" != "x11" ]]; then
  echo "Warning: NealLink MVP is intended for an Ubuntu X11 session."
  echo "Current session: ${XDG_SESSION_TYPE:-unknown}"
fi

if [[ ! -f "config/ubuntu_android.json" ]]; then
  echo "Error: config/ubuntu_android.json not found."
  exit 1
fi

exec python3 neallink_ubuntu.py --config config/ubuntu_android.json
