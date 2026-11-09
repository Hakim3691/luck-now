#!/usr/bin/env bash
# Vendors the pinned llama.cpp revision used by the JNI bridge.
# Sources are build inputs only and are git-ignored.
#
#   scripts/fetch-llama-cpp.sh           # fetch the pinned revision
#   scripts/fetch-llama-cpp.sh --clean   # drop the checkout
set -euo pipefail

REVISION="7d7f0287530bc8981690f361d8c1627dbae1c99a"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/third_party/llama.cpp"

if [ "${1:-}" = "--clean" ]; then
  rm -rf "$DEST"
  echo "removed $DEST"
  exit 0
fi

if [ -d "$DEST/.git" ] && [ "$(git -C "$DEST" rev-parse HEAD 2>/dev/null || true)" = "$REVISION" ]; then
  echo "llama.cpp already at ${REVISION:0:7}"
  exit 0
fi

rm -rf "$DEST"
mkdir -p "$DEST"
git init -q "$DEST"
git -C "$DEST" remote add origin https://github.com/ggml-org/llama.cpp
# Shallow fetch of the exact commit keeps CI fast (~39 MB instead of the full history).
git -C "$DEST" fetch --depth 1 origin "$REVISION"
git -C "$DEST" checkout -q FETCH_HEAD
echo "llama.cpp vendored at ${REVISION:0:7}"
