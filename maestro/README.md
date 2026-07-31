# Maestro E2E UI tests

End-to-end UI flows that drive the real app on an emulator/device, in the spirit of
iOS's `FlipcashUITests`. Flows are plain YAML under `maestro/`; reusable pieces live in
`subflows/` and `helpers/`.

## Prerequisites

1. A booted emulator (or attached device). The suite defaults to `emulator-5554`.
2. The **debug** app installed:
   ```bash
   ANDROID_SERIAL=emulator-5554 ./gradlew :apps:flipcash:app:installDebug
   ```
   The debug build sets `testTagsAsResourceId = true` (guarded by `BuildConfig.UI_TESTABLE`
   in `App.kt`), which exposes Compose `testTag`s as resource-ids that Maestro targets with
   `id:`. Release builds do **not** expose them.
3. The [Maestro CLI](https://maestro.mobile.dev) on your `PATH` (`maestro --version`).
4. Test-account credentials in `maestro/.env` (git-ignored):
   ```
   SEED_PHRASE=word1 word2 ... word12
   LOGIN_DEEPLINK=https://app.flipcash.com/login?data=...
   ```

## Running

Use the runner — it loads `.env`, approves App Links, and targets the device:

```bash
maestro/run.sh maestro/account_navigation.yaml
maestro/run.sh maestro/wallet_token_info.yaml maestro/view_token_info.yaml
DEVICE=emulator-5556 maestro/run.sh maestro/account_navigation.yaml   # pick a device
```

Or invoke Maestro directly:

```bash
maestro --device emulator-5554 test \
  -e SEED_PHRASE="..." -e LOGIN_DEEPLINK="..." maestro/account_navigation.yaml
```

### App Links gotcha (fresh installs)

A freshly-installed debug build has **unverified** App Links, so `https://app.flipcash.com/...`
deeplinks open in Chrome instead of the app (you'll see "Cannot GET /login"). Approve them once
per install (the runner does this automatically):

```bash
adb -s emulator-5554 shell pm set-app-links --package com.flipcash.app.android 2 all
```

### Login

Prefer **deeplink login** (`subflows/login_with_deeplink.yaml`): it clears state and logs the
test account straight to the scanner, so every flow starts from a deterministic home screen.
Seed login (`subflows/login.yaml`) assumes a logged-out start and is only for exercising the
login screen itself.

## Screen-root test anchors (how tagging works)

Every routed screen is addressable by a stable `<name>_screen` resource-id. These are applied
**centrally**, at the single place every destination is registered — `annotatedEntry` in
`AppScreenContent.kt` — not scattered across screen composables:

- The tag defaults to one **derived from the route type name** (`screenRootTag` in
  `NavMetadata.kt`): `AppRoute.Menu.MyAccount` → `my_account_screen`,
  `AppRoute.Main.Scanner` → `scanner_screen`.
- Pass an explicit `testTag` only when a route needs a different id than its type name, e.g.
  `annotatedEntry<AppRoute.Sheets.Give>(testTag = "cash_screen") { ... }`.

Because the tag lives with the route registration, adding a screen tags it automatically and
the anchors can't drift out of sync with the UI. Screens that are **not** nav entries (the
pre-login landing, inner FlowHost steps like the withdrawal wizard) still need a manual
`testTag` on their root — e.g. `login_screen` in `LoginScreenContent.kt`.

Sub-element anchors (buttons, lists, inputs) remain plain `testTag`s in the component code —
e.g. `menu_button`, `market_cap_chart`, `chat_message_list`, `send_contact_list`, `keypad_<n>`.

## Coverage

**Verified green** (deeplink-login entry):
- Login → home (`scanner_screen`)
- Menu navigation (`menu_screen`), My Account (`my_account_screen`), App Settings
  (`app_settings_screen`) — `account_navigation.yaml`
- Wallet (`wallet_screen`) → Token Info (`token_info_screen`) + market-cap chart —
  `wallet_token_info.yaml`
- Give / bill round-trip, token-info deeplink (existing flows)
- Screenshot suite (`screenshots/`, 19 baselines)

**Roadmap** (each new journey = deeplink login + central anchors, plus any missing
sub-element `testTag`s):
- Repair the drifted seed-login flows (`login.yaml`, `login_logout.yaml`) — standardise on
  deeplink login; the logout menu item needs a scroll-into-view.
- Direct Send (phone-number send) — `send_contact_list` / `send_contact_row` exist.
- Deposit / Add-money and Withdraw as functional flows (inner FlowHost steps need root tags).
- Token Discovery / leaderboard (`discovery_leaderboard`, `leaderboard_token_row` exist).
- Chat: send a message end-to-end (`chat_message_input`, `chat_send_icon` exist).
- Currency Creator, Swap/Buy, Onboarding / phone verification, Coinbase onramp.

## CI

Not yet wired. Intended: a `flipcash_maestro` Fastlane lane running
`--include-tags smoke --exclude-tags spends-funds` on a KVM emulator per PR, with the fuller
set nightly. See `docs/superpowers/plans/2026-07-01-maestro-mcp-ui-testing.md`.
