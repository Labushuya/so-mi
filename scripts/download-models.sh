#!/usr/bin/env bash
# scripts/download-models.sh
#
# Idempotent, manifest-driven cache primer for LLM / embedding models.
#
# Phase 1: cache layout + idempotency only. The "model" here is a tiny
#          synthesized placeholder so the cache has something real to
#          checksum and the GitHub Actions cache key has a legitimate
#          target — but no large download blocks CI.
# Phase 2: real GGUF artifacts (smoke-test embedding model + a Tiny-tier LLM
#          for CI smoke-tests) get appended to MANIFEST_*. Larger
#          per-tier models (§7) are downloaded by the *app itself* at
#          runtime, never here.
#
# Contract:
#   - Always writes to $CACHE_DIR (default ~/.cache/llm-models).
#   - Each entry has a name, URL, and sha256.
#   - File is downloaded only if missing or sha256 mismatches.
#   - Re-running is a no-op when the cache is intact.
#   - Exits non-zero on verification failure (so CI catches drift).
set -euo pipefail

CACHE_DIR="${LLM_MODELS_CACHE:-$HOME/.cache/llm-models}"
mkdir -p "$CACHE_DIR"

# ----------------------------------------------------------------------------
# Manifest — three parallel arrays for portability across bash 3.2 (macOS)
# and bash 5+ (Linux CI).
#
# TODO(phase-2): append real artifacts, e.g.
#   MANIFEST_NAMES+=("bge-small-en-v1.5-q8_0.gguf")
#   MANIFEST_URLS+=("https://huggingface.co/.../resolve/main/...")
#   MANIFEST_SHAS+=("<sha256>")
# ----------------------------------------------------------------------------

PLACEHOLDER_NAME="phase1-placeholder.txt"
PLACEHOLDER_CONTENT="so-mi llm-models cache placeholder, replaced in phase 2"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "ERROR: need sha256sum or shasum on PATH" >&2
    exit 2
  fi
}

# Compute the placeholder's sha256 from a temp file (works without openssl).
__tmp="$(mktemp)"
trap 'rm -f "$__tmp"' EXIT
printf '%s' "$PLACEHOLDER_CONTENT" > "$__tmp"
PLACEHOLDER_SHA256="$(sha256_of "$__tmp")"

MANIFEST_NAMES=("$PLACEHOLDER_NAME")
MANIFEST_URLS=("placeholder://phase1")     # sentinel, never fetched
MANIFEST_SHAS=("$PLACEHOLDER_SHA256")

verify() {
  local path="$1" want="$2"
  [[ -f "$path" ]] || return 1
  local got
  got="$(sha256_of "$path")"
  [[ "$got" == "$want" ]]
}

fetch() {
  local url="$1" dest="$2"
  if [[ "$url" == placeholder://* ]]; then
    # Phase 1: synthesize the placeholder rather than hitting the network.
    printf '%s' "$PLACEHOLDER_CONTENT" > "$dest.part"
  elif command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 --retry-delay 2 \
         --show-error --silent --output "$dest.part" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget --tries=3 --quiet -O "$dest.part" "$url"
  else
    echo "ERROR: need curl or wget on PATH" >&2
    exit 2
  fi
  mv -f "$dest.part" "$dest"
}

ensure_one() {
  local name="$1" url="$2" want="$3"
  local dest="$CACHE_DIR/$name"

  if verify "$dest" "$want"; then
    echo "ok    $name (cached)"
    return 0
  fi

  if [[ -f "$dest" ]]; then
    echo "stale $name (sha mismatch, re-fetching)"
    rm -f "$dest"
  else
    echo "miss  $name (fetching)"
  fi

  fetch "$url" "$dest"

  if ! verify "$dest" "$want"; then
    local got
    got="$(sha256_of "$dest")"
    echo "ERROR: sha256 mismatch for $name" >&2
    echo "  expected: $want" >&2
    echo "  got:      $got" >&2
    rm -f "$dest"
    exit 1
  fi
  echo "done  $name"
}

main() {
  echo "llm-models cache: $CACHE_DIR"
  local i
  for i in "${!MANIFEST_NAMES[@]}"; do
    ensure_one "${MANIFEST_NAMES[$i]}" "${MANIFEST_URLS[$i]}" "${MANIFEST_SHAS[$i]}"
  done
  echo "all entries verified (${#MANIFEST_NAMES[@]})"
}

main "$@"
