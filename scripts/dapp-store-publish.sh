#!/usr/bin/env bash
# scripts/dapp-store-publish.sh
#
# Publishes a Flipcash APK to the Solana dApp Store through the publisher portal
# (@solana-mobile/dapp-store-cli, pinned in apps/flipcash/app/dapp_publishing), then records
# what it published under .dappStore in the release manifest.
#
# Usage:
#   bash scripts/dapp-store-publish.sh <apk> <versionCode> [whats-new-file]
#
# Get the APK from scripts/download-play-universal-apk.sh, which also writes the whats-new file
# from Play's production release notes, so both stores ship the same APK and the same notes.
#
# Env:
#   DAPP_STORE_API_KEY       — publisher portal API key
#   DAPP_STORE_KEYPAIR_PATH  — Solana signer keypair JSON
#   DAPP_STORE_PORTAL_URL    — optional portal origin (the CLI defaults to production)
#   MANIFEST_PATH            — manifest location (default: .well-known/release-manifest.json)

set -euo pipefail

if [ -f .env.local ]; then
  set -a; source .env.local; set +a
fi

APK="${1:?usage: dapp-store-publish.sh <apk> <versionCode> [whats-new-file]}"
VERSION_CODE="${2:?usage: dapp-store-publish.sh <apk> <versionCode> [whats-new-file]}"
NOTES_FILE="${3:-}"
MANIFEST_PATH="${MANIFEST_PATH:-.well-known/release-manifest.json}"
PUBLISHING_DIR=apps/flipcash/app/dapp_publishing

: "${DAPP_STORE_API_KEY:?DAPP_STORE_API_KEY not set}"
: "${DAPP_STORE_KEYPAIR_PATH:?DAPP_STORE_KEYPAIR_PATH not set}"
[ -f "$APK" ] || { echo "APK not found at $APK"; exit 1; }
[ -f "$DAPP_STORE_KEYPAIR_PATH" ] || { echo "Keypair not found at $DAPP_STORE_KEYPAIR_PATH"; exit 1; }

# Only publish what Play production already ships.
PROD_CODE=$(jq -r '.tracks.production.versionCode // empty' "$MANIFEST_PATH")
PROD_NAME=$(jq -r '.tracks.production.versionName // empty' "$MANIFEST_PATH")
if [ "$VERSION_CODE" != "$PROD_CODE" ]; then
  echo "versionCode $VERSION_CODE is not Play production ($PROD_CODE); refusing to publish"
  exit 1
fi

WHATS_NEW=""
[ -n "$NOTES_FILE" ] && [ -f "$NOTES_FILE" ] && WHATS_NEW=$(cat "$NOTES_FILE")
[ -n "$WHATS_NEW" ] || WHATS_NEW="Bug fixes and improvements."

APK_ABS=$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")
KEYPAIR_ABS=$(cd "$(dirname "$DAPP_STORE_KEYPAIR_PATH")" && pwd)/$(basename "$DAPP_STORE_KEYPAIR_PATH")

echo "Publishing $PROD_NAME ($VERSION_CODE) to the Solana dApp Store"
(
  cd "$PUBLISHING_DIR"
  pnpm install --frozen-lockfile
  pnpm exec dapp-store \
    --apk-file "$APK_ABS" \
    --whats-new "$WHATS_NEW" \
    --keypair "$KEYPAIR_ABS" \
    --idempotency-key "flipcash-$VERSION_CODE" \
    --verbose
)

# --- record the publish so the manifest drift check and the CI gate see it ---
TMP=$(mktemp)
jq --argjson c "$VERSION_CODE" --arg n "$PROD_NAME" \
  '.dappStore = ({versionCode: $c} + (if $n == "" then {} else {versionName: $n} end))' \
  "$MANIFEST_PATH" > "$TMP"
mv "$TMP" "$MANIFEST_PATH"
echo "Recorded dappStore.versionCode=$VERSION_CODE in $MANIFEST_PATH"
