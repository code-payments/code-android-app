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
#   TRACKS                    — space-separated list of tracks to fetch and update
#                               (default: production beta alpha internal)
#                               Tracks not listed are preserved from the existing manifest.
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
TRACKS="${TRACKS:-production beta alpha internal}"
FORCE_BUMP="${FORCE_BUMP:-false}"

ALL_TRACKS="production beta alpha internal"

# --- helper: check if a value is in a space-separated list ---
in_list() { [[ " $2 " == *" $1 "* ]]; }

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
    | jq -c '
        [ .releases[]? | select(.status == "completed") ]
        | map({ name, code: ([ .versionCodes[]? | tonumber ] | max) })
        | max_by(.code) // { code: null, name: null }'
}

# --- helper: build a track object or null ---
track_obj() {
  local code="$1" name="$2"
  if [ "$code" = "null" ]; then
    echo "null"
  else
    jq -nc --argjson c "$code" --arg n "$name" \
      '{versionCode: $c} + (if $n == "" then {} else {versionName: $n} end)'
  fi
}

echo "Updating tracks: $TRACKS"

# --- read existing manifest as base ---
mkdir -p "$(dirname "$MANIFEST_PATH")"
if [ -f "$MANIFEST_PATH" ]; then
  EXISTING=$(cat "$MANIFEST_PATH")
else
  EXISTING='{"tracks":{}}'
fi

# --- for each track, either fetch fresh or preserve existing ---
declare -A TRACK_OBJS
for t in $ALL_TRACKS; do
  if in_list "$t" "$TRACKS"; then
    JSON=$(fetch_track "$t")
    CODE=$(echo "$JSON" | jq '.code')
    NAME=$(echo "$JSON" | jq -r '.name // empty')
    TRACK_OBJS[$t]=$(track_obj "$CODE" "$NAME")
    echo "  $t: fetched code=$CODE name=$NAME"
  else
    TRACK_OBJS[$t]=$(echo "$EXISTING" | jq -c ".tracks.$t // null")
    echo "  $t: preserved from manifest"
  fi
done

curl -s -X DELETE "$BASE/edits/$EDIT_ID" -H "$AUTH" >/dev/null || true

# --- read previous prod ---
OLD_PROD=$(echo "$EXISTING" | jq -r '.tracks.production.versionCode // .tracks.production // empty' 2>/dev/null || echo "")
NEW_PROD=$(echo "${TRACK_OBJS[production]}" | jq -r '.versionCode // empty' 2>/dev/null || echo "")
NEW_PROD_NAME=$(echo "${TRACK_OBJS[production]}" | jq -r '.versionName // empty' 2>/dev/null || echo "")
echo "Previous prod: ${OLD_PROD:-<none>} | Current prod: ${NEW_PROD:-<none>} (${NEW_PROD_NAME:-<none>})"

# --- write manifest ---
jq -n \
  --argjson production "${TRACK_OBJS[production]}" \
  --argjson beta "${TRACK_OBJS[beta]}" \
  --argjson alpha "${TRACK_OBJS[alpha]}" \
  --argjson internal "${TRACK_OBJS[internal]}" \
  --arg updated "$(date -u +%FT%TZ)" \
  '{updated: $updated, tracks: {production:$production, beta:$beta, alpha:$alpha, internal:$internal}}' \
  > "$MANIFEST_PATH"

echo "Manifest written:"
cat "$MANIFEST_PATH"

emit "old_prod" "${OLD_PROD:-null}"
emit "new_prod" "${NEW_PROD:-null}"
emit "new_prod_name" "${NEW_PROD_NAME:-null}"

# --- detect whether any track changed ---
if diff -q <(jq -S .tracks "$MANIFEST_PATH") <(echo "$EXISTING" | jq -S '.tracks // {}') >/dev/null 2>&1; then
  emit "manifest_changed" "false"
else
  emit "manifest_changed" "true"
fi

# --- decide whether to bump patch ---
PROD_CHANGED=false
if in_list "production" "$TRACKS" && [ "${OLD_PROD:-null}" != "${NEW_PROD:-null}" ]; then
  PROD_CHANGED=true
fi

if [ "$FORCE_BUMP" != "true" ] && [ "$PROD_CHANGED" != "true" ]; then
  echo "Production unchanged and force_bump not set, skipping patch bump"
  emit "prod_changed" "false"
  exit 0
fi

emit "prod_changed" "$PROD_CHANGED"
emit "forced" "$FORCE_BUMP"
echo "Bumping patch version (prod_changed=$PROD_CHANGED, forced=$FORCE_BUMP)"

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
