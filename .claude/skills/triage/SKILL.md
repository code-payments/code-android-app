---
name: triage
description: >
  Fetch the top open Bugsnag production issue, investigate with evidence from
  stack traces / logs / breadcrumbs, propose a fix direction, route through
  domain experts, and write a lean review brief.
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
  - Write
  - Edit
  - Agent
  - WebFetch
  - Skill
---

# Triage: daily Bugsnag issue investigation

You are a senior Android engineer performing daily Bugsnag triage for the
Flipcash Android app. Follow the steps below exactly.

## Step 1 — Fetch the top open issue

Run the helper script:

```bash
bash .claude/skills/triage/scripts/bugsnag-top.sh
```

The script emits a single JSON object with:

| Field | Description |
|-------|-------------|
| `error_id` | Bugsnag error group ID |
| `error_url` | Deep link into the Bugsnag dashboard |
| `event_url` | REST API URL for the latest event |
| `title` | Error class + message |
| `severity` | `error` / `warning` / `info` |
| `events` | Total occurrences |
| `users` | Unique affected users |
| `first_seen` | ISO-8601 timestamp |
| `release` | `app_version` from the latest event |

If the script exits non-zero, stop and report the error to the user.

## Step 2 — Pull event detail

Fetch `event_url` (include header `Authorization: token $BUGSNAG_TOKEN`).
Source `.env` from the repo root if the variable is not already set.

Parse the response using the event shape documented in
`.claude/skills/triage/references/event-shape.md`.

Extract four evidence sources:

1. **Stack trace** — `exceptions[0].stacktrace` (frames with `file`, `method`,
   `lineNumber`, `inProject`)
2. **Exception info** — `exceptions[0].errorClass` + `exceptions[0].message`
3. **App logs** — `metaData["App Logs"]["app_log"]` (single string, last ~64 KB
   of log output)
4. **Breadcrumbs** — `breadcrumbs[]` (timestamped UI / state / network events)

## Step 3 — Map stack frames to source

Android stack frames use Java/Kotlin package-qualified class names (e.g.
`com.flipcash.features.cash.CashViewModel`). To locate the source file:

1. Convert the class name to a path fragment: replace `.` with `/` and append
   `.kt` (try `.java` as a fallback).
2. Use `Glob` to find the file in the repo (e.g. `**/**/CashViewModel.kt`).
3. Read the relevant lines (`lineNumber` from the frame +/- 30 lines of
   context).

Only map frames where `inProject` is `true`.

## Step 4 — Build the evidence timeline

### 4a. Version check

Read `.well-known/release-manifest.json` to get the current production and
internal release versions:

```json
{
  "tracks": {
    "production": { "versionCode": 3508, "versionName": "2026.5.2" },
    "internal":   { "versionCode": 3508, "versionName": "2026.5.2" }
  }
}
```

Compare the event's `app.version` against `tracks.production.versionName` to
determine if the crash still affects the latest release.

### 4b. Assemble evidence

Collect:

- The in-project stack frames mapped to source (file:line + code snippet)
- The exception class and message
- Relevant log lines (grep the `app_log` string for keywords from the exception)
- The last 10-20 breadcrumbs before the crash
- `app.version`, `device.manufacturer`, `device.model`, `os.version`

## Step 5 — Investigate root cause

Using the evidence from Step 4:

1. Read the source files identified in the stack trace.
2. Follow the call chain — read callers and callees within 2 hops.
3. Check for known patterns: null-safety violations, lifecycle issues,
   threading bugs, uncaught coroutine exceptions, missing error handling.
4. Form a hypothesis and verify it against the logs and breadcrumbs.

## Step 6 — Propose a fix direction

Write a concrete fix direction (NOT a full implementation):

- Which file(s) to change and roughly where (`.kt:NN`)
- What the fix involves (e.g. "add null check before accessing X",
  "move coroutine launch to lifecycleScope", "catch Y in Z")
- Why this addresses the root cause
- Any risks or side effects

## Step 7 — Route through domain experts

Based on the evidence, tag relevant experts by adding their labels to the brief.
An issue may match multiple experts.

| Expert | Trigger |
|--------|---------|
| `compose` | Touches files with `@Composable`, `Modifier`, `remember`, `LaunchedEffect`, or under `ui/`, `features/*/ui/` |
| `kotlin-coroutines` | Stack contains `CoroutineScope`, `Dispatchers`, `suspend`, `launch`, `async`, `withContext`, `Job`, `SupervisorJob` |
| `android-tdd` | Proposed fix direction adds or modifies test files |
| `kotlin-flows` | Stack or fix involves `Flow`, `StateFlow`, `SharedFlow`, `collect`, `stateIn` |

## Step 8 — Write the review brief

Use the template in `.claude/skills/triage/references/brief-template.md`.

Save the brief to `.claude/plans/triage-<error_id>.md`.

Keep the brief under 300 words (excluding code snippets and the evidence
appendix).

## Step 9 — Next steps

If the fix direction is clear and well-scoped, offer to draft an implementation
plan using `superpowers:writing-plans`.

If the root cause is ambiguous, suggest specific debugging steps (add logging,
reproduce locally, check related Bugsnag issues).
