---
name: dep-review
description: >
  Review a Dependabot PR for breaking changes, gotchas, and required code
  updates. Usage: /dep-review <PR number or URL>
user-invocable: true
argument-hint: "<PR # or URL>"
allowed-tools:
  - Bash
  - Read
  - Edit
  - Write
  - Glob
  - Grep
  - Agent
  - WebFetch
---

# Dependency Update Review

Review a Dependabot pull request to evaluate the dependency update for breaking
changes, deprecations, and required code modifications.

## Input

Parse `$ARGUMENTS` for a PR reference:
- `#803` or `803` — PR number
- `https://github.com/code-payments/code-android-app/pull/803` — full URL (extract the number)

If no argument is provided, stop and ask the user for a PR number.

## Steps

### Step 1 — Fetch PR details

```bash
gh pr view <number> --repo code-payments/code-android-app --json title,body,files,headRefName,baseRefName,diff
```

Extract:
- **Title** — Dependabot titles follow: `Bump <group-or-artifact> from X.Y.Z to A.B.C`
- **Body** — Contains Dependabot's auto-generated release notes, changelog links, and compatibility scores
- **Changed files** — Typically `gradle/libs.versions.toml` and possibly `build.gradle.kts` files
- **Diff** — The actual version change

If the PR is not a Dependabot PR (author is not `dependabot[bot]` or title doesn't match the pattern), warn the user but continue — they may want to review a manual dependency bump.

### Step 2 — Parse the version change

From the diff of `gradle/libs.versions.toml`, identify:
- **Catalog key** — the version alias (e.g., `grpc = "1.68.0"` → `grpc`)
- **Old version** — the version being replaced
- **New version** — the version being introduced
- **Affected libraries** — all `[libraries]` entries that reference this version alias

If multiple versions are bumped (grouped Dependabot PR), repeat the analysis for each.

Present a summary table:

| Catalog Key | Library | Old Version | New Version | Bump Type |
|-------------|---------|-------------|-------------|-----------|
| `grpc` | `io.grpc:grpc-okhttp` | 1.68.0 | 1.69.0 | minor |

**Bump type** classification:
- **patch** (0.0.x) — bug fixes, lowest risk
- **minor** (0.x.0) — new features, backward compatible
- **major** (x.0.0) — potential breaking changes, highest risk

### Step 3 — Fetch release notes / changelog

For each dependency being bumped:

1. Check the Dependabot PR body for embedded release notes and changelog links
2. If the PR body contains a GitHub releases link or compare link, fetch it:
   ```bash
   gh api repos/<owner>/<repo>/releases/tags/v<new_version> --jq '.body' 2>/dev/null
   ```
3. If no GitHub release exists, try the changelog compare URL from the PR body using `WebFetch`
4. If no release notes are available, note this and proceed with extra caution

Summarize the release notes, highlighting:
- **Breaking changes** or migration requirements
- **Deprecated APIs** that may affect the codebase
- **Behavioral changes** that could silently break things
- **New features** worth noting

### Step 4 — Analyze codebase impact

Use the `dependency-impact` agent to trace the library's usage:

```
Analyze the impact of updating <library> from <old> to <new> in this project.
Focus on:
1. Which modules depend on this library (direct, convention plugin, transitive)
2. Which specific APIs from this library are used in the codebase
3. Whether any deprecated APIs flagged in the release notes are in use
4. Any code that might break based on the changelog
Do NOT make any code changes — research only.
```

### Step 5 — Evaluate and report

Compile findings into a structured assessment:

---

## Dependency Update Assessment

### Overview
| Field | Value |
|-------|-------|
| PR | #NNN |
| Library | `group:artifact` |
| Version | `X.Y.Z` → `A.B.C` |
| Bump type | patch / minor / major |
| Risk | Low / Medium / High |

### Release Highlights
- Key changes from release notes (bulleted)

### Breaking Changes
List any breaking changes from the release notes and whether they affect this project.
If none, state: "No breaking changes identified."

### Deprecations
List any deprecated APIs that the codebase currently uses.
If none, state: "No deprecated API usage found."

### Behavioral Changes
Note any behavioral changes that could silently affect the app.
If none, state: "No behavioral changes of concern."

### Affected Modules
Table of modules that depend on this library.

### Code Changes Required
If code changes are needed:
- **File**: `path/to/file.kt:NN`
- **Change**: Description of what needs to change
- **Reason**: Why this change is necessary

If no code changes are needed, state: "No code changes required — safe to merge as-is."

### Recommended Test Plan
```bash
./gradlew :affected:module:test
```

### Verdict
**APPROVE** / **APPROVE WITH CHANGES** / **INVESTIGATE FURTHER**

- APPROVE — No breaking changes, no deprecated usage, safe to merge
- APPROVE WITH CHANGES — Merge-safe but requires code modifications (list them)
- INVESTIGATE FURTHER — Risky bump, needs manual testing or deeper review

---

### Step 6 — Propose code changes (if needed)

If the verdict is **APPROVE WITH CHANGES**:

1. Show the user each required change with a before/after diff
2. Ask the user if they want to apply the changes
3. If confirmed, check out the PR branch and apply the modifications:
   ```bash
   gh pr checkout <number>
   ```
4. Make the code changes using Edit
5. Offer to commit with:
   ```
   fix(deps): adapt to <library> <new_version> API changes
   ```

## Never

- Auto-merge a PR without user approval
- Make code changes without showing them first
- Skip the release notes check — even for patch bumps, behavioral changes happen
- Dismiss a major version bump as low risk without evidence
- Push changes to the PR branch without explicit user confirmation
