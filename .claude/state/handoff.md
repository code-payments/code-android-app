# Handoff — group-chat Edit UI

**Done and shipped.** [code-android-app#1525](https://github.com/code-payments/code-android-app/pull/1525),
stacked on #1524 (`feat/chat-roster-edit-rpcs`). Worktree `.claude/worktrees/edit-chat-ui`,
branch `feat/edit-chat-ui`, commit `e0479e2e9`.

## Verified green
`:services:flipcash:compileDebugKotlin`, `:apps:flipcash:core:`, `:apps:flipcash:shared:chat:`,
messenger + tipping `testDebugUnitTest`, and `:apps:flipcash:app:assembleDebug` (the last one
proves the two new `@HiltViewModel`s wire up). 12 new tests, all ran.

## Key decisions
- `canEdit` gate is passed in, never derived. `groupProfileOverflowItems(canEdit)` returns a list
  so no-permission means no overflow button.
- `titleOnly` / `pictureOnly` in `PartialEdits.kt` encode "send only what changed" — EditChat
  leaves unset fields alone, so an over-filled request is destructive.
- Title length counted in **code points** (protovalidate max_len semantics), on the trimmed value.
- Echo is idempotent: `TitleChanged`/`PictureChanged` carry absolute values, so persisting the
  editChat response and then receiving the stream event writes the same thing.
- `moderationDescription` extracted from `CreateGroupViewModel` to `core/moderation`, unchanged.
- Built Icon + Name only; the other three node rows have no flipcash2 0.11.0 contract.
- Used repo `MenuList`/`ListItem` metrics (24dp/17sp), not the node's 26dp/20sp — flagged in the PR.
- Terminology: "Group", not the node's "Community".

## Environment gotcha
`google-services.json` is gitignored and absent from fresh worktrees; copy it from the main
checkout (`apps/flipcash/app/`) before any `assembleDebug`.
