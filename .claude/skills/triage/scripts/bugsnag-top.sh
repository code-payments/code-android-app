#!/usr/bin/env bash
# bugsnag-top.sh — Fetch the top open Bugsnag error for Flipcash Android.
#
# Emits a single JSON object on stdout with the fields needed by the triage skill.
# Requires BUGSNAG_TOKEN (personal auth token) in the environment or in .env.
#
# Usage:
#   ./bugsnag-top.sh                    # top open error, production, since latest release
#   ./bugsnag-top.sh --all              # top open error, production, all versions
#   ./bugsnag-top.sh --since 2026-05-01T00:00:00Z  # custom since filter
#   ./bugsnag-top.sh --severity error   # filter by severity
#   ./bugsnag-top.sh --error-id <id>    # fetch a specific error by ID
#   ./bugsnag-top.sh --url <bugsnag_url>  # fetch a specific error by Bugsnag URL

set -euo pipefail

# ── Source .env from repo root ─────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"

if [[ -f "$REPO_ROOT/.env" ]]; then
  # shellcheck source=/dev/null
  source "$REPO_ROOT/.env"
fi
if [[ -f "$REPO_ROOT/.env.local" ]]; then
  # shellcheck source=/dev/null
  source "$REPO_ROOT/.env.local"
fi

# ── Configuration ──────────────────────────────────────────────────────
BROWSER_BASE="https://app.bugsnag.com/${BUGSNAG_ORG_SLUG}/${BUGSNAG_PROJECT_SLUG}/errors"
API_BASE="https://api.bugsnag.com"

if [[ -z "${BUGSNAG_TOKEN:-}" ]]; then
  echo "ERROR: BUGSNAG_TOKEN is not set. Add it to .env or export it." >&2
  exit 1
fi

if [[ -z "${BUGSNAG_PROJECT_ID:-}" ]]; then
  echo "ERROR: BUGSNAG_PROJECT_ID is not set. Add it to .env or export it." >&2
  exit 1
fi

PROJECT_ID="$BUGSNAG_PROJECT_ID"
# ──────────────────────────────────────────────────────────────────────

# ── Read release manifest ─────────────────────────────────────────────
MANIFEST="$REPO_ROOT/.well-known/release-manifest.json"
if [[ -f "$MANIFEST" ]]; then
  PROD_VERSION=$(jq -r '.tracks.production.versionName // "unknown"' "$MANIFEST")
  PROD_CODE=$(jq -r '.tracks.production.versionCode // "unknown"' "$MANIFEST")
  INTERNAL_VERSION=$(jq -r '.tracks.internal.versionName // "unknown"' "$MANIFEST")
  INTERNAL_CODE=$(jq -r '.tracks.internal.versionCode // "unknown"' "$MANIFEST")
  MANIFEST_UPDATED=$(jq -r '.updated // ""' "$MANIFEST")
else
  PROD_VERSION="unknown"
  PROD_CODE="unknown"
  INTERNAL_VERSION="unknown"
  INTERNAL_CODE="unknown"
  MANIFEST_UPDATED=""
fi

# ── Argument parsing ──────────────────────────────────────────────────
RELEASE_STAGE="production"
SEVERITY=""
SINCE=""
ALL_VERSIONS=false
EXPLICIT_ERROR_ID=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --internal)   RELEASE_STAGE="production"; SINCE=""; ALL_VERSIONS=true; shift ;;
    --severity)   SEVERITY="$2"; shift 2 ;;
    --all)        ALL_VERSIONS=true; shift ;;
    --since)      SINCE="$2"; shift 2 ;;
    --error-id)   EXPLICIT_ERROR_ID="$2"; shift 2 ;;
    --url)
      # Extract error ID from a Bugsnag dashboard URL
      # e.g. https://app.bugsnag.com/org/project/errors/abc123def456
      EXPLICIT_ERROR_ID=$(echo "$2" | grep -oE '/errors/([a-f0-9]+)' | sed 's|/errors/||')
      if [[ -z "$EXPLICIT_ERROR_ID" ]]; then
        echo "ERROR: Could not extract error ID from URL: $2" >&2
        exit 1
      fi
      shift 2
      ;;
    *)            echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

# Default: filter to events since the latest production release
if [[ "$ALL_VERSIONS" == false && -z "$SINCE" && -n "$MANIFEST_UPDATED" ]]; then
  SINCE="$MANIFEST_UPDATED"
fi

# ── Helpers ───────────────────────────────────────────────────────────
api() {
  local url="$1"; shift
  local attempt=0 max_attempts=3 delay=2

  while (( attempt < max_attempts )); do
    local http_code body
    body=$(curl -s --globoff -w "\n%{http_code}" \
      -H "Authorization: token $BUGSNAG_TOKEN" \
      -H "Accept: application/json" \
      "$url" "$@" 2>/dev/null) || true

    http_code=$(echo "$body" | tail -1)
    body=$(echo "$body" | sed '$d')

    case "$http_code" in
      200) echo "$body"; return 0 ;;
      429)
        attempt=$((attempt + 1))
        sleep "$delay"
        delay=$((delay * 2))
        ;;
      *)
        echo "ERROR: API returned HTTP $http_code for $url" >&2
        return 1
        ;;
    esac
  done

  echo "ERROR: Rate-limited after $max_attempts attempts" >&2
  return 1
}

# ── Fetch error ───────────────────────────────────────────────────────
if [[ -n "$EXPLICIT_ERROR_ID" ]]; then
  # Fetch a specific error by ID
  ERROR_URL="${API_BASE}/projects/${PROJECT_ID}/errors/${EXPLICIT_ERROR_ID}"
  ERROR_JSON=$(api "$ERROR_URL")

  if [[ -z "$ERROR_JSON" ]] || ! echo "$ERROR_JSON" | jq -e '.id' >/dev/null 2>&1; then
    echo "ERROR: Could not fetch error ${EXPLICIT_ERROR_ID}" >&2
    exit 1
  fi

  ERROR_ID="$EXPLICIT_ERROR_ID"
  TITLE=$(echo "$ERROR_JSON" | jq -r '(.error_class // "") + ": " + (.message // "")')
  SEVERITY_VAL=$(echo "$ERROR_JSON" | jq -r '.severity')
  EVENTS=$(echo "$ERROR_JSON" | jq -r '.events')
  USERS=$(echo "$ERROR_JSON" | jq -r '.users')
  FIRST_SEEN=$(echo "$ERROR_JSON" | jq -r '.first_seen')
else
  # Fetch top open error
  FILTERS="filters[error.status][]=open&filters[app.release_stage][]=${RELEASE_STAGE}"
  if [[ -n "$SEVERITY" ]]; then
    FILTERS="${FILTERS}&filters[event.severity][]=${SEVERITY}"
  fi
  if [[ -n "$SINCE" ]]; then
    FILTERS="${FILTERS}&filters[event.since][]=${SINCE}"
  fi

  ERRORS_URL="${API_BASE}/projects/${PROJECT_ID}/errors?${FILTERS}&sort=events&direction=desc&per_page=1"
  ERRORS_JSON=$(api "$ERRORS_URL")

  if [[ -z "$ERRORS_JSON" ]] || ! echo "$ERRORS_JSON" | jq -e '.[0]' >/dev/null 2>&1; then
    echo "No open errors found for release_stage=${RELEASE_STAGE}" >&2
    exit 1
  fi

  ERROR_ID=$(echo "$ERRORS_JSON" | jq -r '.[0].id')
  TITLE=$(echo "$ERRORS_JSON" | jq -r '.[0] | (.error_class // "") + ": " + (.message // "")')
  SEVERITY_VAL=$(echo "$ERRORS_JSON" | jq -r '.[0].severity')
  EVENTS=$(echo "$ERRORS_JSON" | jq -r '.[0].events')
  USERS=$(echo "$ERRORS_JSON" | jq -r '.[0].users')
  FIRST_SEEN=$(echo "$ERRORS_JSON" | jq -r '.[0].first_seen')
fi

# ── Fetch latest event for this error ─────────────────────────────────
EVENTS_URL="${API_BASE}/projects/${PROJECT_ID}/errors/${ERROR_ID}/events?sort=timestamp&direction=desc&per_page=1"
EVENTS_JSON=$(api "$EVENTS_URL")

EVENT_ID=$(echo "$EVENTS_JSON" | jq -r '.[0].id')
EVENT_URL="${API_BASE}/projects/${PROJECT_ID}/errors/${ERROR_ID}/events/${EVENT_ID}"
RELEASE=$(echo "$EVENTS_JSON" | jq -r '.[0].app.version // "unknown"')

# ── Emit result ───────────────────────────────────────────────────────
jq -n \
  --arg error_id          "$ERROR_ID" \
  --arg error_url         "${BROWSER_BASE}/${ERROR_ID}" \
  --arg event_url         "$EVENT_URL" \
  --arg title             "$TITLE" \
  --arg severity          "$SEVERITY_VAL" \
  --arg events            "$EVENTS" \
  --arg users             "$USERS" \
  --arg first_seen        "$FIRST_SEEN" \
  --arg release           "$RELEASE" \
  --arg prod_version      "$PROD_VERSION" \
  --arg prod_code         "$PROD_CODE" \
  --arg internal_version  "$INTERNAL_VERSION" \
  --arg internal_code     "$INTERNAL_CODE" \
  '{
    error_id:   $error_id,
    error_url:  $error_url,
    event_url:  $event_url,
    title:      $title,
    severity:   $severity,
    events:     ($events | tonumber),
    users:      ($users | tonumber),
    first_seen: $first_seen,
    release:    $release,
    current_versions: {
      production: { versionName: $prod_version, versionCode: ($prod_code | tonumber? // $prod_code) },
      internal:   { versionName: $internal_version, versionCode: ($internal_code | tonumber? // $internal_code) }
    }
  }'
