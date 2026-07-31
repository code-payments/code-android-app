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
   SEED_PHRASE=word1 word2 ... word12          # primary account (tip-enabled)
   LOGIN_DEEPLINK=https://app.flipcash.com/login?data=...   # same account as SEED_PHRASE
   TIPCARD_DEEPLINK=https://app.flipcash.com/tip/...        # the primary account's tip card
   USDF_ONLY_DEEPLINK=https://app.flipcash.com/login?data=...  # reserves-only gate account
   CONTACT_NAME=Brandon McAnsh                  # an on-Flipcash contact for send-to-contact
   CONTACT_PHONE=+15869802333                   # seed this contact into the emulator
   ```
   The runner (`run.sh`) forwards all of these to Maestro.

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

## Enabling beta flags from a test

Beta-gated features (Tipping, Blocklist, …) can be turned on **at launch** without toggling
them in the Labs UI — mirroring iOS's `--beta-flags`. Pass a `betaFlags` launch argument (a
comma-separated list of `FeatureFlag.key`s); `MainActivity` reads it on debug/UI-test builds
and force-enables those flags:

```yaml
- launchApp:
    arguments:
      isUiTest: true
      betaFlags: "tipping_enabled,blocklist_enabled"
```

The overrides must be applied in the **same process** that renders the feature — deeplink
login relaunches via `openLink` and would drop the argument. So use one of:
- `subflows/login_with_flags.yaml` — seed login into the **existing** account with flags set.
- `subflows/create_account.yaml` — a brand-new account through onboarding (test phone
  `+1 (500) 555-0000`, all-zero OTP), for one-run-per-account setup like the tip card. Both
  take a `BETA_FLAGS` env var; the runner forwards `BETA_FLAGS` from your shell.

```bash
BETA_FLAGS=tipping_enabled maestro/run.sh maestro/tipping_setup.yaml
```

## Coverage

**Verified green** (run any of these with `maestro/run.sh`):
- `login_logout.yaml` — real seed-login UI + logout (Log Out lives on My Account)
- `account_navigation.yaml` — menu → My Account → App Settings
- `wallet_token_info.yaml` — wallet → token info + market-cap chart
- `discovery_leaderboard.yaml` — Discover → leaderboard → token info
- `direct_send.yaml` — send entry → phone gate
- `withdraw.yaml` — menu → Withdraw Money → USDC → amount entry (fund-safe)
- `deposit.yaml` — menu → Add Money → Other Wallet → USDC deposit (fund-safe)
- `tipping_setup.yaml` — create account (beta flag) → set up tip card → tip card renders
- `tip_chat.yaml` — open the tip conversation from the Tips tab and send a message
- `blocking.yaml` — block a chat participant from their profile, verify in My Account →
  Blocked, then unblock (leaves the account clean)
- `tip_deeplink.yaml` — open a tip-card deeplink (`TIPCARD_DEEPLINK`) → presents the tip flow
  (waits for balances to sync first, else the empty-cache state trips the add-money gate)
- `buy.yaml` — token info → Buy → payment currency → confirm-purchase screen (fund-safe)
- `sell.yaml` — token info → Sell → amount entry (fund-safe)
- `currency_creator.yaml` — Discover → Create Your Own Currency → intro + $20 balance gate
- `coinbase_onramp.yaml` — Add Money → Coinbase/Google Pay method → onramp (phone verify);
  `coinbase_onramp_sandbox_enabled` set so a follow-up can drive a sandbox purchase
- Give/bill round-trip, token-info deeplink, screenshot suite (existing)

**Scaffolded — pending account provisioning** (flow authored + wired; drop in the account/contact
and it runs):
- `usdf_only_gate.yaml` — reserves-only account: tapping Cash routes to Discover ("No Community
  Currencies Yet"). Mirrors iOS `GiveDiscoverGateRegressionTests`. Needs `USDF_ONLY_DEEPLINK`
  (a dedicated USDF-only account, like iOS's `FLIPCASH_UI_TEST_USDF_ONLY_ACCESS_KEY`).
- `send_to_contact.yaml` — send to an on-Flipcash contact (mirrors iOS `SendSmokeTests`, which uses a
  fixed contact "Raul Riera"). Needs a **send-enabled** account (a phone linked — use the backend test
  number `+15005550000`/`000000`, or a real number with `adb emu sms send`) **and** the `CONTACT_NAME`
  contact seeded in the emulator as a real Flipcash user. Parameterized by `CONTACT_NAME`/`CONTACT_PHONE`.
- **Full Coinbase purchase** — the flow reaches the onramp; completing it needs phone verification
  (which links a phone to the shared account and would flip the send flows) plus driving the Google
  Pay sandbox sheet. Note: **iOS doesn't automate the payment either** — its E2E stops at the same
  onramp/verification boundary (`BuyApplePayRegressionTests`: unverified → verification sheet) and
  covers order-building/deposit/verification logic with unit tests (`OnrampOrderRequestTests`,
  `CoinbaseDepositOperationTests`, `OnrampVerificationViewModelTests`). So our `coinbase_onramp` entry
  test is at parity; the sandbox flag + method tag are in place if we later want to go further.

**Roadmap (tooling):**
- Buy/Sell/Withdraw past confirmation on a funded account (screens tagged).
- Wire a `flipcash_maestro` Fastlane lane (see below).

## CI

Not yet wired. Intended: a `flipcash_maestro` Fastlane lane running
`--include-tags smoke --exclude-tags spends-funds` on a KVM emulator per PR, with the fuller
set nightly. See `docs/superpowers/plans/2026-07-01-maestro-mcp-ui-testing.md`.
