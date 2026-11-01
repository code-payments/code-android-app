#!/usr/bin/env bash
# scripts/download-play-universal-apk.sh
#
# Downloads the universal APK that Google Play generated, and signed with the app signing key,
# for one versionCode, plus that release's production release notes. The dApp Store publish
# uses this APK so both stores ship the same bytes under the same certificate.
#
# Env:
#   SERVICE_ACCOUNT_KEY_JSON  — path to the Google service account JSON file
#   PKG                       — package name (default: com.flipcash.app.android)
#   VERSION_CODE              — versionCode to fetch (default: production in the release manifest)
#   MANIFEST_PATH             — manifest location (default: .well-known/release-manifest.json)
#   OUT_DIR                   — output directory (default: build/dapp-store)
#   NOTES_LANGUAGE            — release notes language (default: en-US)
#   CERT_SHA256               — optional signing certificate SHA-256 to select among generated APK sets
#
# Writes $OUT_DIR/flipcash-<versionCode>.apk and $OUT_DIR/whats-new.txt (empty when Play has no
# notes for that release). Outputs apk_path, version_code, notes_path to $GITHUB_OUTPUT in CI.

set -euo pipefail

if [ -f .env.local ]; then
  set -a; source .env.local; set +a
fi

: "${SERVICE_ACCOUNT_KEY_JSON:?SERVICE_ACCOUNT_KEY_JSON not set}"
[ -f "$SERVICE_ACCOUNT_KEY_JSON" ] || { echo "SA file not found at $SERVICE_ACCOUNT_KEY_JSON"; exit 1; }

PKG="${PKG:-com.flipcash.app.android}"
MANIFEST_PATH="${MANIFEST_PATH:-.well-known/release-manifest.json}"
OUT_DIR="${OUT_DIR:-build/dapp-store}"
NOTES_LANGUAGE="${NOTES_LANGUAGE:-en-US}"
VERSION_CODE="${VERSION_CODE:-$(jq -r '.tracks.production.versionCode // empty' "$MANIFEST_PATH")}"
: "${VERSION_CODE:?VERSION_CODE not set and no production versionCode in $MANIFEST_PATH}"

emit() {
  echo "$1=$2"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "$1=$2" >> "$GITHUB_OUTPUT"; fi
}

source "$(dirname "$0")/lib/play-api.sh"
ACCESS=$(play_access_token "$SERVICE_ACCOUNT_KEY_JSON") || exit 1
AUTH="Authorization: Bearer $ACCESS"
BASE="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG"

mkdir -p "$OUT_DIR"
APK_PATH="$OUT_DIR/flipcash-$VERSION_CODE.apk"
NOTES_PATH="$OUT_DIR/whats-new.txt"

# --- find the universal APK Play generated for this versionCode ---
GENERATED=$(curl -sf "$BASE/generatedApks/$VERSION_CODE" -H "$AUTH") \
  || { echo "Failed to list generated APKs for $VERSION_CODE"; exit 1; }
# Play returns one entry per signing certificate; after a key upgrade there is more than one.
# Set CERT_SHA256 to pick one, otherwise the first entry wins.
CERT_SHA256="${CERT_SHA256:-}"
ENTRY=$(echo "$GENERATED" | jq -c --arg cert "$CERT_SHA256" '
  [ .generatedApks[]? | select(.generatedUniversalApk.downloadId != null)
    | select($cert == "" or (.certificateSha256Hash | ascii_downcase) == ($cert | ascii_downcase)) ][0] // empty')
[ -n "$ENTRY" ] || { echo "Play has no universal APK for $VERSION_CODE"; echo "$GENERATED"; exit 1; }
echo "Generated APK sets: $(echo "$GENERATED" | jq '[.generatedApks[]?] | length'), using certificate $(echo "$ENTRY" | jq -r .certificateSha256Hash)"
DOWNLOAD_ID=$(echo "$ENTRY" | jq -r .generatedUniversalApk.downloadId)
[ -n "$DOWNLOAD_ID" ] || { echo "Play has no universal APK for $VERSION_CODE"; echo "$GENERATED"; exit 1; }

echo "Downloading universal APK for $VERSION_CODE"
curl -sfL "$BASE/generatedApks/$VERSION_CODE/downloads/$DOWNLOAD_ID:download?alt=media" \
  -H "$AUTH" -o "$APK_PATH" \
  || { echo "APK download failed"; exit 1; }

# --- production release notes for this versionCode ---
EDIT_ID=$(curl -s -X POST "$BASE/edits" -H "$AUTH" | jq -r .id)
curl -s "$BASE/edits/$EDIT_ID/tracks/production" -H "$AUTH" \
  | jq -r --arg code "$VERSION_CODE" --arg lang "$NOTES_LANGUAGE" '
      [ .releases[]? | select((.versionCodes // []) | index($code)) ]
      | .[0].releaseNotes // []
      | map(select(.language == $lang)) | .[0].text // empty' \
  > "$NOTES_PATH"
curl -s -X DELETE "$BASE/edits/$EDIT_ID" -H "$AUTH" >/dev/null || true

echo "Wrote $APK_PATH ($(wc -c < "$APK_PATH" | tr -d ' ') bytes)"
emit "apk_path" "$APK_PATH"
emit "version_code" "$VERSION_CODE"
emit "notes_path" "$NOTES_PATH"
