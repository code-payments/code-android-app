#!/usr/bin/env bash
# build-lookup.sh — Resolve a versionCode to its git commit and GitHub Action run.
#
# The Flipcash versionCode is derived from `git rev-list --count HEAD` (see
# apps/flipcash/app/build.gradle.kts:20-25). versionCode N = the Nth commit
# from the root of the repo.
#
# Builds are cut from code/cash, so commits are counted from origin/code/cash
# (fetched first), not from the local HEAD, which may be behind or on another
# branch.
#
# Usage:
#   ./build-lookup.sh <versionCode>
#   ./build-lookup.sh 3797
#   ./build-lookup.sh 3797 --ref <ref>   # count from another ref
#
# Output: JSON with commit_sha, commit_short, commit_message, and (if found)
#         the GitHub Actions run ID and URL for the Flipcash2 workflow.

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: build-lookup.sh <versionCode> [--ref <ref>]" >&2
  exit 1
fi

VERSION_CODE="$1"; shift
REF="origin/code/cash"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref) REF="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ "$REF" == "origin/code/cash" ]]; then
  git fetch --quiet origin code/cash 2>/dev/null \
    || echo "WARN: could not fetch origin/code/cash; using the local copy" >&2
fi
WORKFLOW_ID="229420296"  # "Flipcash2 Build and Deploy"

# ── Resolve versionCode to commit ───────────────────────────────────
# versionCode = git rev-list --count HEAD at the time of build.
# The Nth commit (1-indexed from root) is at position N in `git rev-list --reverse $REF`.
# Equivalently: git rev-list $REF | sed -n '<offset>p' where offset = total - N + 1.

TOTAL=$(git rev-list --count "$REF")

if (( VERSION_CODE > TOTAL )); then
  echo "ERROR: versionCode $VERSION_CODE exceeds the commit count of $REF ($TOTAL)" >&2
  exit 1
fi

if (( VERSION_CODE < 1 )); then
  echo "ERROR: versionCode must be >= 1" >&2
  exit 1
fi

OFFSET=$(( TOTAL - VERSION_CODE + 1 ))
COMMIT_SHA=$(git rev-list "$REF" | sed -n "${OFFSET}p")

# The offset assumes linear history. Confirm the commit really is the Nth.
if [[ "$(git rev-list --count "$COMMIT_SHA")" != "$VERSION_CODE" ]]; then
  echo "ERROR: commit ${COMMIT_SHA:0:9} on $REF has $(git rev-list --count "$COMMIT_SHA") ancestors, not $VERSION_CODE (non-linear history?)" >&2
  exit 1
fi
COMMIT_SHORT="${COMMIT_SHA:0:9}"
COMMIT_MESSAGE=$(git log -1 --format='%s' "$COMMIT_SHA")
COMMIT_DATE=$(git log -1 --format='%aI' "$COMMIT_SHA")

# ── Find GitHub Actions run for this commit ─────────────────────────
RUN_JSON=""
RUN_ID=""
RUN_URL=""
RUN_STATUS=""
RUN_CONCLUSION=""

if command -v gh &>/dev/null; then
  # gh run list --commit requires a full SHA match, so fetch recent runs and
  # filter by headSha prefix instead. We pull the last 100 runs to cover ~2
  # weeks of builds.
  ALL_RUNS=$(gh run list \
    --workflow="$WORKFLOW_ID" \
    --json databaseId,url,status,conclusion,headSha \
    --limit 100 2>/dev/null || true)

  if [[ -n "$ALL_RUNS" ]]; then
    MATCH=$(echo "$ALL_RUNS" | jq --arg sha "$COMMIT_SHA" '[.[] | select(.headSha == $sha)] | .[0] // empty')
    if [[ -n "$MATCH" ]]; then
      RUN_ID=$(echo "$MATCH" | jq -r '.databaseId')
      RUN_URL=$(echo "$MATCH" | jq -r '.url')
      RUN_STATUS=$(echo "$MATCH" | jq -r '.status')
      RUN_CONCLUSION=$(echo "$MATCH" | jq -r '.conclusion')
    fi
  fi
fi

# ── Emit result ─────────────────────────────────────────────────────
jq -n \
  --arg version_code    "$VERSION_CODE" \
  --arg commit_sha      "$COMMIT_SHA" \
  --arg commit_short    "$COMMIT_SHORT" \
  --arg commit_message  "$COMMIT_MESSAGE" \
  --arg commit_date     "$COMMIT_DATE" \
  --arg total_commits   "$TOTAL" \
  --arg run_id          "${RUN_ID:-}" \
  --arg run_url         "${RUN_URL:-}" \
  --arg run_status      "${RUN_STATUS:-}" \
  --arg run_conclusion  "${RUN_CONCLUSION:-}" \
  '{
    version_code:   ($version_code | tonumber),
    commit: {
      sha:     $commit_sha,
      short:   $commit_short,
      message: $commit_message,
      date:    $commit_date
    },
    total_commits: ($total_commits | tonumber),
    github_actions: (
      if $run_id != "" then {
        run_id:     ($run_id | tonumber),
        url:        $run_url,
        status:     $run_status,
        conclusion: $run_conclusion
      } else null end
    )
  }'
