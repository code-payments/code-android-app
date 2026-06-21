# 16 — Agents & skills

This repo ships **Claude Code** automation tuned to its architecture: subagents
(under `.claude/agents/`) for multi-step investigations, and slash-command skills
(under `.claude/skills/`) for repeatable workflows. This page maps a task to the
right tool so you (or an agent) don't reinvent work the repo already automates.

## Task → tool

| When you want to… | Use | Type |
|-------------------|-----|------|
| Triage a Bugsnag issue / crash with evidence and a fix direction | `/triage` | skill |
| Investigate a crash/stack trace and trace it to a root cause | `bug-triage` | agent |
| Create a new feature / shared / lib module skeleton | `module-scaffolder` | agent |
| Add a screen end-to-end (uses the scaffolder) | follow [11 — Adding a feature](11-adding-a-feature.md) | guide |
| Fetch latest protobufs, verify, summarize, scaffold stubs | `/fetch-protos` | skill |
| Trace the impact of a proto change through the codebase | `proto-change-tracer` | agent |
| Assess the blast radius of a dependency bump | `dependency-impact` | agent |
| Review a Dependabot PR for breaking changes | `/dep-review` | skill |
| Review a PR (or local changes) for quality & arch consistency | `pr-reviewer` | agent |
| Find untested code and scaffold tests | `test-gap-finder` | agent |
| Map a `versionCode` to its commit / CI run | `/build-lookup` | skill |
| Deobfuscate a release stack trace | `/r8-mapping` | skill |
| Generate GitHub release notes | `/release-notes` | skill |

## Agents (`.claude/agents/`)

Agents are launched via the `Agent` tool for open-ended, multi-step work:

- **bug-triage** — traces a Bugsnag link / stack trace / error through the codebase
  to a root cause and suggested fix.
- **module-scaffolder** — generates a full module skeleton: `build.gradle.kts`,
  package structure, entry points, navigation registration, `settings.gradle.kts`
  inclusion.
- **proto-change-tracer** — after `/fetch-protos`, traces
  `generated models → Api → Service → Repository → Controller → features`
  ([13](13-protobuf-and-codegen.md)).
- **dependency-impact** — for a dependency bump, finds dependent modules, breaking
  API changes, and targeted tests to run.
- **pr-reviewer** — reviews a PR or local diff against the project's patterns
  (CompositionLocal injection, MVI/MVVM, convention plugins, proto boundaries).
- **test-gap-finder** — finds coverage gaps and scaffolds tests in the project's
  style ([12](12-testing.md)).

## Skills (`.claude/skills/`)

Skills are slash commands for repeatable workflows:

- **/triage** — triage a Bugsnag production issue (top open or a specific
  URL/ID) end-to-end.
- **/fetch-protos** `[flipcash|opencode] [commit]` — pull + regenerate protos
  ([13](13-protobuf-and-codegen.md)).
- **/dep-review** `<PR>` — review a Dependabot PR for breaking changes and required
  code updates.
- **/build-lookup** `<versionCode>` — git commit + Actions run for a build
  ([15](15-ci-and-release.md)).
- **/r8-mapping** `<versionCode>` — download the R8 mapping to deobfuscate a trace.
- **/release-notes** `<from> <to>` — generate polished release notes.

## Project context for agents

The repo root **`CLAUDE.md`** is the canonical orientation file (build commands,
module layout, key patterns, namespaces, git conventions) and points here. New
agents should read it first, then this `docs/architecture/` suite for depth.

## Why this matters

The architecture has sharp conventions (layered modules, proto boundaries,
coordinator/controller roles, MVI), and these agents/skills already encode them.
Reaching for the right one keeps work consistent with the codebase instead of
re-deriving the patterns each time.
