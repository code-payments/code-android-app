---
name: build-lookup
description: >
  Look up the git commit and GitHub Actions run for a Flipcash versionCode.
  Usage: /build-lookup <versionCode>
user-invocable: true
argument-hint: "<versionCode>"
allowed-tools:
  - Bash
---

# Build Lookup: versionCode to commit + CI run

Resolve a Flipcash versionCode to its git commit and the associated GitHub
Actions build run.

## Background

The Flipcash versionCode is the output of `git rev-list --count HEAD` at build
time (see `apps/flipcash/app/build.gradle.kts:20-25`). This means
**versionCode N = the Nth commit from the root of the repository**.

When someone says "commit 3797" or "build 3797" they mean versionCode 3797.

## Usage

Parse `$ARGUMENTS` for a versionCode (integer). If not provided, ask the user.

Run the helper script:

```bash
bash .claude/skills/build-lookup/scripts/build-lookup.sh <versionCode>
```

The script emits JSON:

```json
{
  "version_code": 3797,
  "commit": {
    "sha": "637722b9c...",
    "short": "637722b9c",
    "message": "the commit message",
    "date": "2026-06-03T..."
  },
  "total_commits": 3810,
  "github_actions": {
    "run_id": 26846060877,
    "url": "https://github.com/code-payments/code-android-app/actions/runs/26846060877",
    "status": "completed",
    "conclusion": "success"
  }
}
```

`github_actions` is `null` if no matching workflow run was found.

## Output

Present the results clearly:

- **versionCode**: N
- **Commit**: `<short_sha>` — `<message>` (date)
- **GitHub Actions**: [Run #ID](url) — status/conclusion
