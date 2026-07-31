#!/usr/bin/env bash
# Convenience runner for the Maestro E2E suite.
#
# Usage:
#   maestro/run.sh <flow.yaml> [more flows...]
#   maestro/run.sh maestro/account_navigation.yaml
#
# Handles the fiddly setup that a fresh emulator/install needs:
#   - loads SEED_PHRASE / LOGIN_DEEPLINK from maestro/.env (values may contain spaces)
#   - approves App Links so https deeplinks route to the app instead of the browser
#   - targets a specific device when several are attached (DEVICE env, default emulator-5554)
#
# Prereqs (see maestro/README.md): emulator booted, debug app installed
# (./gradlew :apps:flipcash:app:installDebug), maestro CLI on PATH.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ID="com.flipcash.app.android"
DEVICE="${DEVICE:-emulator-5554}"
ENV_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "error: $ENV_FILE not found (needs SEED_PHRASE and LOGIN_DEEPLINK)." >&2
  exit 1
fi

# Load creds without word-splitting the space-containing seed phrase.
SEED_PHRASE="$(grep '^SEED_PHRASE=' "$ENV_FILE" | cut -d= -f2-)"
LOGIN_DEEPLINK="$(grep '^LOGIN_DEEPLINK=' "$ENV_FILE" | cut -d= -f2-)"

# App Links verification does not survive a fresh install; approve so
# https://app.flipcash.com/... deeplinks open the app, not Chrome.
adb -s "$DEVICE" shell pm set-app-links --package "$APP_ID" 2 all >/dev/null 2>&1 || true

if [[ $# -eq 0 ]]; then
  echo "usage: $0 <flow.yaml> [more flows...]" >&2
  exit 1
fi

maestro --device "$DEVICE" test \
  -e SEED_PHRASE="$SEED_PHRASE" \
  -e LOGIN_DEEPLINK="$LOGIN_DEEPLINK" \
  "$@"
