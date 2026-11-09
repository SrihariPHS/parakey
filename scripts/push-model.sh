#!/usr/bin/env bash
set -euo pipefail

# Push the Parakeet model into the debug app's private storage.
# Usage: scripts/push-model.sh [adb-serial]

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="${PARAKEY_ASSETS:-$ROOT/../parakey-assets}"
SERIAL="${1:-}"
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB+=( -s "$SERIAL" )
PKG="com.parakey.keyboard"
REMOTE_DIR="files/models/parakeet-110m"

[[ -f "$ASSETS/model.int8.onnx" ]] || { echo "Missing $ASSETS/model.int8.onnx (run scripts/fetch-model.sh)" >&2; exit 1; }
[[ -f "$ASSETS/tokens.txt" ]] || { echo "Missing $ASSETS/tokens.txt (run scripts/fetch-model.sh)" >&2; exit 1; }

"${ADB[@]}" shell pm path "$PKG" >/dev/null 2>&1 || {
  echo "Install the debug APK first: adb install -r app/build/outputs/apk/debug/app-debug.apk" >&2
  exit 1
}

"${ADB[@]}" push "$ASSETS/model.int8.onnx" /data/local/tmp/parakeet-model.int8.onnx
"${ADB[@]}" push "$ASSETS/tokens.txt" /data/local/tmp/parakeet-tokens.txt
"${ADB[@]}" shell run-as "$PKG" mkdir -p "$REMOTE_DIR"
"${ADB[@]}" shell run-as "$PKG" cp /data/local/tmp/parakeet-model.int8.onnx "$REMOTE_DIR/model.int8.onnx"
"${ADB[@]}" shell run-as "$PKG" cp /data/local/tmp/parakeet-tokens.txt "$REMOTE_DIR/tokens.txt"

host_sha="$(shasum -a 256 "$ASSETS/model.int8.onnx" | awk '{print $1}')"
device_sha="$("${ADB[@]}" shell run-as "$PKG" sha256sum "$REMOTE_DIR/model.int8.onnx" | awk '{print $1}')"
if [[ "$host_sha" != "$device_sha" ]]; then
  echo "Device model checksum mismatch: $device_sha" >&2
  exit 1
fi

"${ADB[@]}" shell rm -f /data/local/tmp/parakeet-model.int8.onnx /data/local/tmp/parakeet-tokens.txt

echo "Model pushed to $PKG/$REMOTE_DIR (sha256 verified)"
