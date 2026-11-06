---
name: puppet
description: >
  Drive this app's real view models headlessly with the `puppet` CLI (no device, no network), run
  the shared behavior tests against the Android host, or add a screen to puppet. The skill lives in
  the flipcash-puppet repo; this file points to it. Usage: /puppet [ls|test|build|add-screen <name>]
user-invocable: true
argument-hint: "[ls|test|build|add-screen <name>]"
---

# puppet (pointer)

The `puppet` skill is owned by the `flipcash-puppet` repo, which sits beside this checkout under
the orchestrator root (`../flipcash-puppet` from a main checkout). Read and follow
`flipcash-puppet/.claude/skills/puppet/SKILL.md`, treating `flipcash-puppet/` as its root. If you
can't find it, ask the user where it is; the repo isn't published yet, so there is nothing to clone.

This repo holds only the Android host:

- `libs/puppet/`: the protocol loop (`PuppetHost`), platform-neutral.
- `apps/desktop/src/main/kotlin/com/flipcash/desktop/puppet/`: the Chats screen, its seeded
  fixture and guard, and `PuppetMain`.
- `./gradlew :apps:desktop:installPuppet` builds the launcher at
  `apps/desktop/build/install/puppet-host/bin/puppet-host`. It is a desktop JVM task; no device.

When running puppet against this checkout from a worktree, set `PUPPET_ANDROID_ROOT` to this
checkout's root (or pass `--root`); otherwise puppet uses the sibling `code-android-app/`.

A change to a screen with a host (Chats) is done when `puppet test android` passes against a host
built from the change, in addition to this repo's Definition of Done.
