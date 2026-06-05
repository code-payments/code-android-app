#!/usr/bin/env bash
# r8-mapping.sh — Download the R8 mapping file for a Flipcash release build.
#
# Downloads the `release-artifacts` artifact from the GitHub Actions run
# associated with a given versionCode, then extracts the R8 mapping.txt.
#
# Usage:
#   ./r8-mapping.sh <versionCode>
#   ./r8-mapping.sh --run-id <github_actions_run_id>
#
# Output: JSON with the path to the downloaded mapping file and metadata.
# The mapping file is saved to /tmp/r8-mapping-<versionCode>/mapping/release/mapping.txt

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
BUILD_LOOKUP="$REPO_ROOT/.claude/skills/build-lookup/scripts/build-lookup.sh"
WORKFLOW_ID="229420296"

# ── Argument parsing ────────────────────────────────────────────────
RUN_ID=""
VERSION_CODE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --run-id) RUN_ID="$2"; shift 2 ;;
    *)        VERSION_CODE="$1"; shift ;;
  esac
done

# ── Resolve run ID if only versionCode given ────────────────────────
if [[ -z "$RUN_ID" && -n "$VERSION_CODE" ]]; then
  LOOKUP_JSON=$(bash "$BUILD_LOOKUP" "$VERSION_CODE")

  RUN_ID=$(echo "$LOOKUP_JSON" | jq -r '.github_actions.run_id // empty')
  COMMIT_SHA=$(echo "$LOOKUP_JSON" | jq -r '.commit.sha')

  if [[ -z "$RUN_ID" ]]; then
    echo "ERROR: No GitHub Actions run found for versionCode $VERSION_CODE (commit $COMMIT_SHA)" >&2
    echo "Try: gh run list --workflow=$WORKFLOW_ID --commit=${COMMIT_SHA:0:9} --limit=10" >&2
    exit 1
  fi
elif [[ -z "$RUN_ID" ]]; then
  echo "Usage: r8-mapping.sh <versionCode>" >&2
  echo "       r8-mapping.sh --run-id <run_id>" >&2
  exit 1
fi

# ── Download artifact ───────────────────────────────────────────────
DEST_DIR="/tmp/r8-mapping-${VERSION_CODE:-$RUN_ID}"
MAPPING_PATH="$DEST_DIR/mapping/release/mapping.txt"

if [[ -f "$MAPPING_PATH" ]]; then
  # Already downloaded, skip
  LINE_COUNT=$(wc -l < "$MAPPING_PATH" | tr -d ' ')
else
  rm -rf "$DEST_DIR"
  mkdir -p "$DEST_DIR"

  echo "Downloading release-artifacts from run $RUN_ID..." >&2
  gh run download "$RUN_ID" \
    -n release-artifacts \
    -D "$DEST_DIR" 2>&1 >&2

  if [[ ! -f "$MAPPING_PATH" ]]; then
    # Try alternate path — artifact may have different structure
    ALT=$(find "$DEST_DIR" -name "mapping.txt" -type f 2>/dev/null | head -1)
    if [[ -n "$ALT" ]]; then
      MAPPING_PATH="$ALT"
    else
      echo "ERROR: mapping.txt not found in downloaded artifacts" >&2
      echo "Contents of $DEST_DIR:" >&2
      find "$DEST_DIR" -type f >&2
      exit 1
    fi
  fi

  LINE_COUNT=$(wc -l < "$MAPPING_PATH" | tr -d ' ')
  echo "Downloaded mapping.txt ($LINE_COUNT lines)" >&2
fi

# ── Emit result ─────────────────────────────────────────────────────
jq -n \
  --arg mapping_path  "$MAPPING_PATH" \
  --arg run_id        "$RUN_ID" \
  --arg version_code  "${VERSION_CODE:-unknown}" \
  --arg line_count    "$LINE_COUNT" \
  '{
    mapping_path: $mapping_path,
    run_id:       ($run_id | tonumber),
    version_code: (if $version_code != "unknown" then ($version_code | tonumber) else null end),
    line_count:   ($line_count | tonumber)
  }'
