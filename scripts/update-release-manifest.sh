#!/usr/bin/env bash
# scripts/update-release-manifest.sh
#
# Fetches current track versionCodes from the Play Developer API,
# updates .well-known/release-manifest.json in place, and bumps
# Flipcash.patchVersion in Packaging.kt when production changes.
#
# Env:
#   SERVICE_ACCOUNT_KEY_JSON  — path to the Google service account JSON file
#   PKG                       — package name (default: com.flipcash.app.android)
#   MANIFEST_PATH             — manifest location (default: .well-known/release-manifest.json)
#
# Outputs (written to $GITHUB_OUTPUT when running in CI):
#   old_prod / new_prod       — previous and current production versionCode
#   prod_changed              — "true" | "false"
#   version                   — new versionName (only when prod_changed=true)

set -euo pipefail

# Source .env.local for local development if present
if [ -f .env.local ]; then
  set -a; source .env.local; set +a
fi

: "${SERVICE_ACCOUNT_KEY_JSON:?SERVICE_ACCOUNT_KEY_JSON not set}"
[ -f "$SERVICE_ACCOUNT_KEY_JSON" ] || { echo "SA file not found at $SERVICE_ACCOUNT_KEY_JSON"; exit 1; }

SA_PATH="$SERVICE_ACCOUNT_KEY_JSON"
PKG="${PKG:-com.flipcash.app.android}"
MANIFEST_PATH="${MANIFEST_PATH:-.well-known/release-manifest.json}"

# --- helper: write to $GITHUB_OUTPUT when in CI, otherwise just print ---
emit() {
  echo "$1=$2"
  [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1=$2" >> "$GITHUB_OUTPUT"
}

# --- mint access token ---
NOW=$(date +%s); EXP=$((NOW + 3600))
CLIENT_EMAIL=$(jq -r .client_email "$SA_PATH")
PRIVATE_KEY=$(jq -r .private_key "$SA_PATH")

HEADER=$(echo -n '{"alg":"RS256","typ":"JWT"}' | base64 | tr '+/' '-_' | tr -d '=\n')
PAYLOAD=$(jq -nc \
  --arg iss "$CLIENT_EMAIL" \
  --arg scope "https://www.googleapis.com/auth/androidpublisher" \
  --argjson iat $NOW --argjson exp $EXP \
  '{iss:$iss, scope:$scope, aud:"https://oauth2.googleapis.com/token", iat:$iat, exp:$exp}' \
  | base64 | tr '+/' '-_' | tr -d '=\n')
SIG=$(printf '%s.%s' "$HEADER" "$PAYLOAD" \
  | openssl dgst -sha256 -sign <(echo "$PRIVATE_KEY") \
  | base64 | tr '+/' '-_' | tr -d '=\n')
ACCESS=$(curl -s -X POST https://oauth2.googleapis.com/token \
  -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  -d "assertion=$HEADER.$PAYLOAD.$SIG" | jq -r .access_token)

[ "$ACCESS" = "null" ] && { echo "Token request failed"; exit 1; }
echo "Got access token"

# --- fetch tracks ---
AUTH="Authorization: Bearer $ACCESS"
BASE="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG"
EDIT_ID=$(curl -s -X POST "$BASE/edits" -H "$AUTH" | jq -r .id)
echo "Created edit $EDIT_ID"

fetch_track() {
  curl -s "$BASE/edits/$EDIT_ID/tracks/$1" -H "$AUTH" \
    | jq -c '[.releases[]? | select(.status == "completed") | .versionCodes[]?] | map(tonumber) | max // null'
}

PROD=$(fetch_track production)
BETA=$(fetch_track beta)
ALPHA=$(fetch_track alpha)
INTERNAL=$(fetch_track internal)
echo "Tracks: prod=$PROD beta=$BETA alpha=$ALPHA internal=$INTERNAL"

curl -s -X DELETE "$BASE/edits/$EDIT_ID" -H "$AUTH" >/dev/null || true

# --- read previous prod ---
OLD_PROD=$(jq -r '.tracks.production // empty' "$MANIFEST_PATH" 2>/dev/null || echo "")
echo "Previous prod: ${OLD_PROD:-<none>} | New prod: $PROD"

# --- write manifest ---
mkdir -p "$(dirname "$MANIFEST_PATH")"
jq -n \
  --argjson production "$PROD" \
  --argjson beta "$BETA" \
  --argjson alpha "$ALPHA" \
  --argjson internal "$INTERNAL" \
  --arg updated "$(date -u +%FT%TZ)" \
  '{updated: $updated, tracks: {production:$production, beta:$beta, alpha:$alpha, internal:$internal}}' \
  > "$MANIFEST_PATH"

echo "Manifest written:"
cat "$MANIFEST_PATH"

emit "old_prod" "${OLD_PROD:-null}"
emit "new_prod" "$PROD"

# --- decide whether prod changed ---
if [ "$OLD_PROD" = "$PROD" ]; then
  echo "Production unchanged, skipping patch bump"
  emit "prod_changed" "false"
  exit 0
fi

emit "prod_changed" "true"
echo "Production changed ($OLD_PROD -> $PROD), bumping patch version"

# --- bump patch version ---
KOTLIN_FILE=buildSrc/src/main/java/Packaging.kt
[ -f "$KOTLIN_FILE" ] || { echo "Skipping patch bump — not in repo root"; exit 0; }

CURRENT=$(sed -n '/object Flipcash : Packaging(/,/)/ s/.*patchVersion = \([0-9][0-9]*\).*/\1/p' "$KOTLIN_FILE")
MAJOR=$(sed -n '/object Flipcash : Packaging(/,/)/ s/.*majorVersion = \([0-9][0-9]*\).*/\1/p' "$KOTLIN_FILE")
MINOR=$(sed -n '/object Flipcash : Packaging(/,/)/ s/.*minorVersion = \([0-9][0-9]*\).*/\1/p' "$KOTLIN_FILE")

[ -z "$CURRENT" ] && { echo "Failed to parse current patchVersion"; exit 1; }
NEXT=$((CURRENT + 1))

if sed --version >/dev/null 2>&1; then
  sed -i "/object Flipcash : Packaging(/,/)/ s/patchVersion = [0-9][0-9]*/patchVersion = $NEXT/" "$KOTLIN_FILE"
else
  sed -i '' "/object Flipcash : Packaging(/,/)/ s/patchVersion = [0-9][0-9]*/patchVersion = $NEXT/" "$KOTLIN_FILE"
fi

emit "version" "$MAJOR.$MINOR.$NEXT"
echo "Patch bump: $CURRENT -> $NEXT ($MAJOR.$MINOR.$NEXT)"
