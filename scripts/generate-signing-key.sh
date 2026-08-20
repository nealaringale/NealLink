#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-neallink-release.jks}"
ALIAS="${2:-neallink}"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool was not found. Install a JDK 17 package first." >&2
  exit 1
fi

if [[ -e "$OUT" ]]; then
  echo "Refusing to overwrite existing keystore: $OUT" >&2
  exit 1
fi

keytool -genkeypair -v \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

echo
echo "Keystore created: $OUT"
echo "Keep this file private. Add its base64 contents and passwords as GitHub Actions secrets."
