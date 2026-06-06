#!/usr/bin/env bash
# Generiert deterministisch einen CI-Keystore und committed ihn.
#
# WARNUNG — bewusste Designentscheidung:
# Dieser Keystore liegt im Klartext im Repo. Das ist HIER ok, weil:
#   1. Die App wird ausschließlich per Sideload (GitHub Release) verteilt.
#   2. Die einzige Anforderung ist Update-Kontinuität (sonst verweigert
#      Android das Drüber-Installieren).
#   3. Keine Production-Daten, keine User-Base am Cert.
# NIEMALS für Play-Store-Releases verwenden.

set -euo pipefail
cd "$(dirname "$0")/.."

KEYSTORE=keystore/ci.keystore
PASS="ci-password-public"
ALIAS="ci"

if [[ -f "$KEYSTORE" ]]; then
  echo "Keystore exists — refusing to overwrite (would break updates)."
  exit 1
fi

mkdir -p keystore
keytool -genkeypair -v \
  -keystore "$KEYSTORE" -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=So-Mi CI Build, OU=Sideload, O=Selfhosted, C=DE"

echo "=== KEYSTORE FINGERPRINT ==="
keytool -list -v -keystore "$KEYSTORE" -storepass "$PASS" | grep -E "SHA-?256:" | head -n1

git add "$KEYSTORE"
echo "Staged $KEYSTORE — commit with: git commit -m 'chore: add CI keystore (sideload-only)'"
