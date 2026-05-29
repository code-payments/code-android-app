#!/usr/bin/env bash
# scripts/changelog.sh
#
# Generate a conventional changelog between two git refs.
#
# Usage:
#   bash scripts/changelog.sh <from> <to>
#   bash scripts/changelog.sh fcash/2026.4.9 fcash/2026.4.10
#   bash scripts/changelog.sh fcash/2026.4.9 HEAD

set -euo pipefail

# Resolve a ref that might be a commit number (e.g. 3599 = the 3599th commit).
# Pure numbers with fewer than 7 digits are always treated as commit ordinals
# (7 is the minimum git short-SHA length, so this avoids ambiguity).
# Everything else (tags, SHAs, HEAD) passes through as-is.
resolve_ref() {
  local ref="$1"
  if [[ "$ref" =~ ^[0-9]+$ ]] && [ "${#ref}" -lt 7 ]; then
    local sha
    sha=$(git rev-list --reverse HEAD | sed -n "${ref}p")
    if [ -z "$sha" ]; then
      echo "Error: commit #$ref not found (repo has $(git rev-list --count HEAD) commits)" >&2
      exit 1
    fi
    echo "$sha"
  else
    echo "$ref"
  fi
}

FROM=$(resolve_ref "${1:?Usage: changelog.sh <from|commit#> [to|commit#]}")
TO=$(resolve_ref "${2:-HEAD}")

# Collect commits (skip merges)
COMMITS=$(git log --no-merges --format="%H %s" "$FROM".."$TO")

if [ -z "$COMMITS" ]; then
  echo "No commits between $FROM and $TO"
  exit 0
fi

# Parse a conventional commit subject into scope and description.
# e.g. "feat(exchange): add thing" → scope="exchange" desc="add thing"
# e.g. "fix: something"           → scope=""          desc="something"
parse_cc() {
  local msg="$1"
  # Strip the type prefix (already categorized by caller)
  local rest="${msg#*:}"
  rest="${rest# }" # trim leading space

  # Extract scope if present: "type(scope): desc"
  local pattern='^[a-z]+\(([^)]+)\):'
  if [[ "$msg" =~ $pattern ]]; then
    CC_SCOPE="${BASH_REMATCH[1]}"
  else
    CC_SCOPE=""
  fi
  CC_DESC="$rest"
}

FEATS=() FIXES=() IMPROVEMENTS=() CHORES=() BUILDS=() OTHERS=()

while IFS= read -r line; do
  hash="${line%% *}"
  short=$(git rev-parse --short "$hash")
  msg="${line#* }"

  case "$msg" in
    feat*:*)     parse_cc "$msg"; bucket=FEATS;;
    fix*:*)      parse_cc "$msg"; bucket=FIXES;;
    refactor*:*) parse_cc "$msg"; bucket=IMPROVEMENTS;;
    style*:*)    parse_cc "$msg"; bucket=IMPROVEMENTS;;
    perf*:*)     parse_cc "$msg"; bucket=IMPROVEMENTS;;
    chore*:*)    parse_cc "$msg"; bucket=CHORES;;
    build*:*)    parse_cc "$msg"; bucket=BUILDS;;
    *)           CC_SCOPE=""; CC_DESC="$msg"; bucket=OTHERS;;
  esac

  if [ -n "$CC_SCOPE" ]; then
    entry="- **${CC_SCOPE}:** ${CC_DESC} (\`$short\`)"
  else
    entry="- ${CC_DESC} (\`$short\`)"
  fi

  case "$bucket" in
    FEATS)        FEATS+=("$entry");;
    FIXES)        FIXES+=("$entry");;
    IMPROVEMENTS) IMPROVEMENTS+=("$entry");;
    CHORES)       CHORES+=("$entry");;
    BUILDS)       BUILDS+=("$entry");;
    OTHERS)       OTHERS+=("$entry");;
  esac
done <<< "$COMMITS"

TO_NAME=$(git describe --tags --exact-match "$TO" 2>/dev/null || git rev-parse --short "$TO")

echo "## $TO_NAME"
echo ""

section() {
  local title="$1"; shift
  local -n items="$1"
  if [ "${#items[@]}" -gt 0 ]; then
    echo "### $title"
    echo ""
    printf '%s\n' "${items[@]}"
    echo ""
  fi
}

section "Features" FEATS
section "Bug Fixes" FIXES
section "Improvements" IMPROVEMENTS
section "Chores" CHORES
section "Build" BUILDS
section "Other" OTHERS
