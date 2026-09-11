#!/usr/bin/env bash
# scripts/shared-core-release-notes.sh
#
# Write the release notes for a SharedCore publish into
# code-payments/flipcash-shared-core-spm.
#
# That repo holds only a generated `Package.swift` and the XCFramework it points
# at, so a release there has no commits of its own to describe. The Kotlin and
# the Swift glue both live here, which makes this repo the only place the notes
# can be derived from.
#
# Usage:
#   scripts/shared-core-release-notes.sh <version> [options]
#
#   --since <ref>        Commit/tag to diff from. Defaults to the newest
#                        shared-core/* tag older than <version>.
#   --source <ref>       Commit the framework was built from. Defaults to HEAD.
#   --summary-file <f>   Prose to place above the generated changelog.
#   --checksum <sha256>  Checksum of the uploaded zip, as recorded in Package.swift.
#
# Prints markdown on stdout.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPM_REPO="code-payments/flipcash-shared-core-spm"
SOURCE_REPO="code-payments/code-android-app"
BUILD_FILE="kmp/shared-core/build.gradle.kts"

VERSION="${1:?Usage: shared-core-release-notes.sh <version> [--since <ref>] ...}"
shift

SINCE="" SOURCE_REF="HEAD" SUMMARY_FILE="" CHECKSUM=""
while [ $# -gt 0 ]; do
  case "$1" in
    --since)        SINCE="$2"; shift 2;;
    --source)       SOURCE_REF="$2"; shift 2;;
    --summary-file) SUMMARY_FILE="$2"; shift 2;;
    --checksum)     CHECKSUM="$2"; shift 2;;
    *) echo "Unknown option: $1" >&2; exit 2;;
  esac
done

cd "$REPO_ROOT"

SOURCE_SHA=$(git rev-parse "$SOURCE_REF")
SOURCE_SHORT=$(git rev-parse --short "$SOURCE_SHA")

# The framework exports a hand-maintained list of Gradle modules, and that list
# grows. Reading it out of the build script at release time keeps the changelog
# honest as modules are added, instead of pinning a path list here that silently
# goes stale.
exported_modules() {
  local ref="$1"
  git show "$ref:$BUILD_FILE" 2>/dev/null \
    | sed -n 's/^ *export(project("\(:[^"]*\)")).*/\1/p' \
    | sort -u
}

# A change in a module the framework depends on lands in the binary just as
# surely as a change in an exported one, so walk the `project(":…")` references
# out from the exported set until it stops growing.
transitive_modules() {
  local ref="$1" frontier next seen path
  seen=$(exported_modules "$ref")
  frontier="$seen"
  while [ -n "$frontier" ]; do
    next=""
    while IFS= read -r module; do
      [ -n "$module" ] || continue
      path="${module#:}"; path="${path//://}"
      next+=$(git show "$ref:$path/build.gradle.kts" 2>/dev/null \
        | sed -n 's/.*project("\(:[^"]*\)").*/\1/p')$'\n'
    done <<< "$frontier"
    frontier=$(printf '%s' "$next" | sort -u | comm -23 - <(printf '%s\n' "$seen" | sort -u))
    seen=$(printf '%s\n%s\n' "$seen" "$frontier" | sed '/^$/d' | sort -u)
  done
  printf '%s\n' "$seen"
}

module_paths() {
  transitive_modules "$1" | sed 's|^:||; s|:|/|g'
}

# `--since` unset: the newest shared-core tag that sorts below this version. Using
# the tag rather than a date means a publish cut from a feature branch still
# diffs against what was actually last released.
if [ -z "$SINCE" ]; then
  SINCE=$(git tag --list 'shared-core/*' \
    | sed 's|^shared-core/||' \
    | sort -V \
    | awk -v v="$VERSION" '{ print } $0 == v { exit }' \
    | sort -V | awk -v v="$VERSION" '$0 != v' | tail -1)
  [ -n "$SINCE" ] && SINCE="shared-core/$SINCE"
fi

# `mapfile` is bash 4; the macOS runners still ship 3.2, so read the list the
# portable way.
PATHS=()
while IFS= read -r p; do
  [ -n "$p" ] && PATHS+=("$p")
done < <(module_paths "$SOURCE_SHA")
PATHS+=("kmp/shared-core")

# Turn "feat(codes): share geometry (#1287)" into a bullet that keeps the scope,
# drops the type prefix, and links the PR (or the commit, when there isn't one).
format_bullet() {
  local sha="$1" subject="$2" scope="" desc pr
  if [[ "$subject" =~ ^[a-z]+\(([^\)]+)\): ]]; then
    scope="${BASH_REMATCH[1]}"
  fi
  desc="${subject#*: }"
  if [[ "$desc" =~ ^(.*)\ \(#([0-9]+)\)$ ]]; then
    desc="${BASH_REMATCH[1]}"
    pr="[#${BASH_REMATCH[2]}](https://github.com/$SOURCE_REPO/pull/${BASH_REMATCH[2]})"
  else
    pr="[\`$(git rev-parse --short "$sha")\`](https://github.com/$SOURCE_REPO/commit/$sha)"
  fi
  if [ -n "$scope" ]; then
    echo "- **$scope**: $desc ($pr)"
  else
    echo "- $desc ($pr)"
  fi
}

section() {
  local heading="$1"; shift
  [ $# -eq 0 ] && return 0
  echo "### $heading"
  echo
  printf '%s\n' "$@"
  echo
}

FEATS=() FIXES=() IMPROVEMENTS=() BUILDS=() OTHERS=()

if [ -n "$SINCE" ]; then
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    sha="${line%% *}"; subject="${line#* }"
    bullet=$(format_bullet "$sha" "$subject")
    case "$subject" in
      feat*:*)                      FEATS+=("$bullet");;
      fix*:*)                       FIXES+=("$bullet");;
      refactor*:*|perf*:*|style*:*) IMPROVEMENTS+=("$bullet");;
      build*:*|chore*:*|ci*:*)      BUILDS+=("$bullet");;
      test*:*|docs*:*)              ;;  # no effect on what ships in the framework
      *)                            OTHERS+=("$bullet");;
    esac
  done < <(git log --no-merges --format='%H %s' "$SINCE..$SOURCE_SHA" -- "${PATHS[@]}")
fi

# Header
if [ -n "$SUMMARY_FILE" ] && [ -s "$SUMMARY_FILE" ]; then
  cat "$SUMMARY_FILE"
  echo
fi

if [ -z "$SINCE" ]; then
  # No previous tag to diff against, so there is no changelog to print. Say so
  # only when nothing else has introduced the release.
  if [ -z "$SUMMARY_FILE" ] || [ ! -s "$SUMMARY_FILE" ]; then
    echo "First published version."
    echo
  fi
else
  # An added export is the one change an iOS consumer can act on immediately:
  # new Kotlin API reaches `import SharedCore` without any Swift glue.
  NEW_MODULES=$(comm -13 \
    <(exported_modules "$SINCE") \
    <(exported_modules "$SOURCE_SHA") || true)
  if [ -n "$NEW_MODULES" ]; then
    echo "### Newly exported modules"
    echo
    printf '%s\n' "$NEW_MODULES" | sed 's/^/- `/; s/$/`/'
    echo
  fi

  section "Features" "${FEATS[@]+"${FEATS[@]}"}"
  section "Fixes" "${FIXES[@]+"${FIXES[@]}"}"
  section "Improvements" "${IMPROVEMENTS[@]+"${IMPROVEMENTS[@]}"}"
  section "Build" "${BUILDS[@]+"${BUILDS[@]}"}"
  section "Other" "${OTHERS[@]+"${OTHERS[@]}"}"

  if [ ${#FEATS[@]} -eq 0 ] && [ ${#FIXES[@]} -eq 0 ] && [ ${#IMPROVEMENTS[@]} -eq 0 ] \
     && [ ${#BUILDS[@]} -eq 0 ] && [ ${#OTHERS[@]} -eq 0 ]; then
    echo "No change to the framework's sources since ${SINCE#shared-core/}. This is a rebuild: same API, new checksum."
    echo
  fi
fi

# Provenance
cat <<EOF
### Package

\`\`\`swift
.package(url: "https://github.com/$SPM_REPO", from: "$VERSION")
\`\`\`

Built from [\`$SOURCE_SHORT\`](https://github.com/$SOURCE_REPO/commit/$SOURCE_SHA) in \`$SOURCE_REPO\`.
EOF

if [ -n "$CHECKSUM" ]; then
  echo "XCFramework checksum \`$CHECKSUM\`."
fi
