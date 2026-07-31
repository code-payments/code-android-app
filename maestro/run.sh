#!/usr/bin/env bash
# Convenience runner for the Maestro E2E suite (local and CI).
#
# Usage:
#   maestro/run.sh <flow.yaml> [more flows...]     # run specific flows (local dev)
#   maestro/run.sh --tags smoke                    # run by tag, JUnit output (CI)
#
# Handles the fiddly setup a fresh emulator/install needs:
#   - loads creds from maestro/.env when present; existing env vars win (CI supplies them)
#   - approves App Links so https deeplinks route to the app instead of the browser
#   - seeds the send-to-contact recipient into the emulator's contacts (idempotent)
#   - targets a specific device when several are attached (DEVICE env, default emulator-5554)
#
# Prereqs (see maestro/README.md): emulator booted, debug app installed
# (./gradlew :apps:flipcash:app:installDebug), maestro CLI on PATH.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ID="com.flipcash.app.android"
DEVICE="${DEVICE:-emulator-5554}"
ENV_FILE="$SCRIPT_DIR/.env"

# Value from the current environment (CI secrets) if set, else from maestro/.env.
cred() {
  local name="$1" current="${!1:-}"
  if [[ -n "$current" ]]; then printf '%s' "$current"; return; fi
  [[ -f "$ENV_FILE" ]] && grep "^${name}=" "$ENV_FILE" | cut -d= -f2- || true
}
SEED_PHRASE="$(cred SEED_PHRASE)"
LOGIN_DEEPLINK="$(cred LOGIN_DEEPLINK)"
TIPCARD_DEEPLINK="$(cred TIPCARD_DEEPLINK)"
# Dedicated USDF-only (reserves-only) account for gate tests.
USDF_ONLY_DEEPLINK="$(cred USDF_ONLY_DEEPLINK)"
# On-Flipcash contact for send-to-contact tests (seeded into the emulator's contacts).
CONTACT_NAME="$(cred CONTACT_NAME)"
CONTACT_PHONE="$(cred CONTACT_PHONE)"

# App Links verification does not survive a fresh install; approve so
# https://app.flipcash.com/... deeplinks open the app, not Chrome.
adb -s "$DEVICE" shell pm set-app-links --package "$APP_ID" 2 all >/dev/null 2>&1 || true

# Seed the send-to-contact recipient into the emulator's contacts (idempotent). No-op
# unless CONTACT_NAME + CONTACT_PHONE are set. The device-side single quotes preserve
# spaces in the name; the new raw contact is the highest auto-increment _id.
seed_contact() {
  [[ -z "$CONTACT_NAME" || -z "$CONTACT_PHONE" ]] && return 0
  local data_uri="content://com.android.contacts/data"
  local raw_uri="content://com.android.contacts/raw_contacts"
  if adb -s "$DEVICE" shell content query --uri "$data_uri" --projection mimetype:data1 2>/dev/null \
       | grep -q "$CONTACT_PHONE"; then
    return 0   # already seeded
  fi
  adb -s "$DEVICE" shell content insert --uri "$raw_uri" \
    --bind account_name:s: --bind account_type:s: >/dev/null 2>&1
  local rid
  rid=$(adb -s "$DEVICE" shell content query --uri "$raw_uri" --projection _id 2>/dev/null \
        | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)
  [[ -z "$rid" ]] && { echo "warning: could not seed contact" >&2; return 0; }
  adb -s "$DEVICE" shell "content insert --uri $data_uri --bind raw_contact_id:i:$rid \
    --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'$CONTACT_NAME'" >/dev/null 2>&1
  adb -s "$DEVICE" shell "content insert --uri $data_uri --bind raw_contact_id:i:$rid \
    --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:'$CONTACT_PHONE' \
    --bind data2:i:2" >/dev/null 2>&1
}
seed_contact

# Build the maestro target: `--tags <list>` runs the whole suite filtered by tag with
# JUnit output (CI); otherwise the args are treated as specific flow files (local dev).
if [[ "${1:-}" == "--tags" ]]; then
  shift
  include="${1:-smoke}"
  target=( --include-tags "$include"
           --exclude-tags "${MAESTRO_EXCLUDE_TAGS:-spends-funds}"
           --format junit --output "${MAESTRO_OUTPUT:-maestro-report.xml}"
           "$SCRIPT_DIR" )
elif [[ $# -eq 0 ]]; then
  echo "usage: $0 <flow.yaml> [more flows...]   |   $0 --tags <tag>" >&2
  exit 1
else
  target=( "$@" )
fi

maestro --device "$DEVICE" test \
  -e SEED_PHRASE="$SEED_PHRASE" \
  -e LOGIN_DEEPLINK="$LOGIN_DEEPLINK" \
  -e TIPCARD_DEEPLINK="$TIPCARD_DEEPLINK" \
  -e USDF_ONLY_DEEPLINK="$USDF_ONLY_DEEPLINK" \
  -e CONTACT_NAME="$CONTACT_NAME" \
  -e CONTACT_PHONE="$CONTACT_PHONE" \
  -e BETA_FLAGS="${BETA_FLAGS:-}" \
  "${target[@]}"
