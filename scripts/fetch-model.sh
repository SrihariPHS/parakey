#!/usr/bin/env bash
set -euo pipefail

# Fetch the int8 Parakeet TDT-CTC 110M model used by Parakey.
# The model is CC-BY-4.0 and is never committed to this repository.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${PARAKEY_ASSETS:-$ROOT/../parakey-assets}"
BASE="https://huggingface.co/VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8/resolve/main"

MODEL_SHA="9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a"
TOKENS_SHA="450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10"

mkdir -p "$DEST"

fetch() {
  local name="$1" url="$2"
  if [[ -f "$DEST/$name" ]]; then
    echo "Using existing $DEST/$name"
  else
    echo "Downloading $name…"
    curl -L --fail --max-time 900 --retry 3 --progress-bar -o "$DEST/$name.part" "$url"
    mv "$DEST/$name.part" "$DEST/$name"
  fi
}

fetch "model.int8.onnx" "$BASE/model.int8.onnx"
fetch "tokens.txt" "$BASE/tokens.txt"

actual_model="$(shasum -a 256 "$DEST/model.int8.onnx" | awk '{print $1}')"
actual_tokens="$(shasum -a 256 "$DEST/tokens.txt" | awk '{print $1}')"
[[ "$actual_model" == "$MODEL_SHA" ]] || { echo "Model checksum mismatch: $actual_model" >&2; exit 1; }
[[ "$actual_tokens" == "$TOKENS_SHA" ]] || { echo "Tokens checksum mismatch: $actual_tokens" >&2; exit 1; }

echo "Model ready in $DEST"
