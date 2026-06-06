#!/usr/bin/env bash
# scripts/install-dev.sh
#
# Pull the latest GitHub Release APK and `adb install -r` it onto every
# connected device. Intended for daily dev-on-phone iteration on the
# HONOR Magic V2 (and anything else plugged in / on the network).
#
# Requires: adb. Prefers `gh` (auth + rate limit). Falls back to curl+jq.
# Env:
#   REPO        owner/name (default: inferred from `gh repo view`)
#   TAG         release tag (default: latest)
#   APK_DIR     download directory (default: ./.dist)
set -euo pipefail

REPO="${REPO:-}"
TAG="${TAG:-}"
APK_DIR="${APK_DIR:-./.dist}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: missing dependency: $1" >&2; exit 2; }; }
need adb

mkdir -p "$APK_DIR"

# --- 1. Resolve repo --------------------------------------------------------
if [[ -z "$REPO" ]]; then
  if command -v gh >/dev/null 2>&1; then
    REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || true)"
  fi
fi
if [[ -z "$REPO" ]]; then
  echo "ERROR: cannot infer REPO. Set REPO=owner/name or run inside a gh-authed clone." >&2
  exit 2
fi

# --- 2. Download APK --------------------------------------------------------
echo "fetching APK from $REPO ${TAG:+(tag=$TAG)}"

if command -v gh >/dev/null 2>&1; then
  if [[ -n "$TAG" ]]; then
    gh release download "$TAG" --repo "$REPO" --pattern '*.apk' --dir "$APK_DIR" --clobber
  else
    gh release download         --repo "$REPO" --pattern '*.apk' --dir "$APK_DIR" --clobber
  fi
else
  need curl
  need jq
  api="https://api.github.com/repos/$REPO/releases/${TAG:-latest}"
  [[ -z "$TAG" ]] && api="https://api.github.com/repos/$REPO/releases/latest"
  echo "  via REST: $api"
  meta="$(curl --fail --silent --show-error -H 'Accept: application/vnd.github+json' "$api")"
  mapfile -t urls < <(echo "$meta" | jq -r '.assets[] | select(.name | endswith(".apk")) | .browser_download_url')
  if [[ ${#urls[@]} -eq 0 ]]; then
    echo "ERROR: no .apk assets in release" >&2
    exit 1
  fi
  for u in "${urls[@]}"; do
    fname="$(basename "$u")"
    echo "  downloading $fname"
    curl --fail --location --silent --show-error -o "$APK_DIR/$fname" "$u"
  done
fi

shopt -s nullglob
apks=( "$APK_DIR"/*.apk )
shopt -u nullglob
if [[ ${#apks[@]} -eq 0 ]]; then
  echo "ERROR: no APK files in $APK_DIR after download" >&2
  exit 1
fi
echo "have ${#apks[@]} apk(s):"
for a in "${apks[@]}"; do echo "  - $a"; done

# --- 3. Enumerate adb devices ----------------------------------------------
adb start-server >/dev/null 2>&1 || true

mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')

if [[ ${#devices[@]} -eq 0 ]]; then
  cat >&2 <<'EOF'
ERROR: no adb devices in state "device".

Checklist:
  1. USB cable connected (or `adb connect <ip>:5555` for wireless).
  2. USB debugging enabled in Developer Options on the phone.
  3. Authorized this host on the phone's RSA prompt.
  4. `adb devices` lists the device as "device" (not "unauthorized" / "offline").
EOF
  exit 1
fi

echo "installing on ${#devices[@]} device(s):"
fail=0
for d in "${devices[@]}"; do
  for a in "${apks[@]}"; do
    echo "  -> $d : $(basename "$a")"
    if ! adb -s "$d" install -r -d "$a"; then
      echo "     FAILED on $d" >&2
      fail=1
    fi
  done
done

exit "$fail"
