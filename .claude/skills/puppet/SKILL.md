---
name: puppet
description: >
  Drive this app's real view models headlessly with the `puppet` CLI (no device, no network), run
  the shared behavior tests against the Android host, or add a screen to puppet. The skill lives in
  the orchestrator repo; this file points to it. Usage: /puppet [android|ios | test android|ios [path] | sync | add-screen <name>]
user-invocable: true
argument-hint: "[android|ios | test android|ios [path] | sync | add-screen <name>]"
---

# puppet (pointer)

The `puppet` skill, protocol and canonical behavior files live in the orchestrator repo, the root
this checkout sits in (`..` from a main checkout). Read and follow
`<orchestrator>/.agents/skills/puppet/SKILL.md`, treating the orchestrator as its root.

This repo holds the Android side:

- `libs/puppet/`: the request/response loop (`PuppetHost`) and the shared stack, platform-neutral.
- `:apps:flipcash:puppet`: the app host, one JVM process. `FlipcashPuppet` maps each hosted
  route (`/tab/chats` today) to its screen; `chats/` holds the seeded fixture and the Chats node,
  and `ChatsBehaviorTest` replays `test-vectors/behaviors/chats/` (a synced copy).
  `-Ppuppet.behaviors=<dir or file>` points it at other files. A new hosted screen is a new
  route here, not a new process.
- `flipcashTestDebug` runs that module's `test`, so CI replays the behavior files.

From a worktree, set `PUPPET_ANDROID_ROOT` to this checkout's root; otherwise the orchestrator's
`scripts/puppet/puppet` uses the sibling `code-android-app/`.

A change to a hosted screen (Chats) is done when `scripts/puppet/puppet test android` passes
against this checkout and `sync --check` is clean.
