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
e.g. `market_cap_chart`, `chat_message_list`, `send_contact_list`, `keypad_<n>`.

Two anchors exist purely for the tests and are worth knowing about:

- **The tab bar** is icon-only (no labels, no content descriptions), so its four tabs would
  otherwise be unaddressable. They carry `nav_scanner`, `nav_wallet`, `nav_chats` and
  `nav_tipcard` (`NavigationBar.kt`). Tabs are *replaced* on a single root back stack, so Back
  never unwinds between them — `helpers/close_open_sheet.yaml` is how flows get home.
- **`token_info_screen`** is on both the pushed currency-info screen *and* the expanded-card
  overlay (`CurrencyInfoExpansion.kt`). Tapping a card in the wallet expands it in place rather
  than pushing a screen, so the overlay carries the same anchor and flows don't care which
  presentation they got.

## Enabling beta flags from a test

Beta-gated features (Blocklist, …) can be turned on **at launch** without toggling
them in the Labs UI — mirroring iOS's `--beta-flags`. Pass a `betaFlags` launch argument (a
comma-separated list of `FeatureFlag.key`s); `MainActivity` reads it on debug/UI-test builds
and force-enables those flags:

```yaml
- launchApp:
    arguments:
      isUiTest: true
      betaFlags: "blocklist_enabled"
```

The overrides must be applied in the **same process** that renders the feature — deeplink
login relaunches via `openLink` and would drop the argument. So use one of:
- `subflows/login_with_flags.yaml` — seed login into the **existing** account with flags set.
- `subflows/create_account.yaml` — a brand-new account through onboarding (test phone
  `+1 (500) 555-0000`, all-zero OTP), for one-run-per-account setup like the tip card. Both
  take a `BETA_FLAGS` env var; the runner forwards `BETA_FLAGS` from your shell.

```bash
maestro/run.sh maestro/tipping_setup.yaml
```

## Coverage

**Verified green** (run any of these with `maestro/run.sh`):
- `login_logout.yaml` — real seed-login UI + logout (Log Out lives on My Account)
- `account_navigation.yaml` — menu → My Account → App Settings
- `wallet_token_info.yaml` — wallet → token info + market-cap chart
- `discovery_leaderboard.yaml` — wallet → Discover Currencies → leaderboard → token info
- `withdraw.yaml` — menu → Withdraw Money → USDC → amount entry (fund-safe)
- `deposit.yaml` — menu → Add Money → Other Wallet → USDC deposit (fund-safe)
- `tipping_setup.yaml` — create account → set up tip card → tip card renders
- `tip_chat.yaml` — open the tip conversation from the Chats tab and send a message
- `blocking.yaml` — block a chat participant from their profile, verify in My Account →
  Blocked, then unblock (leaves the account clean)
- `tip_deeplink.yaml` — open a tip-card deeplink (`TIPCARD_DEEPLINK`) → presents the tip flow
  (waits for balances to sync first, else the empty-cache state trips the add-money gate)
- `buy.yaml` — Discover → an unheld currency → Get → amount → receipt (fund-safe)
- `sell.yaml` — wallet → Float → Convert → amount entry (fund-safe)
- `currency_creator.yaml` — wallet → Create a Currency → intro + $20 balance gate
- `coinbase_onramp.yaml` — Add Money → Coinbase/Google Pay method → onramp (phone verify);
  `coinbase_onramp_sandbox_enabled` set so a follow-up can drive a sandbox purchase
- Give/bill round-trip, token-info deeplink, screenshot suite (existing)

**Scaffolded — pending account provisioning** (flow authored + wired; drop in the account/contact
and it runs):
- `usdf_only_gate.yaml` — reserves-only account: the wallet deck holds nothing giveable and the
  "Discover Currencies" tile is the way out. Mirrors iOS `GiveDiscoverGateRegressionTests`. Needs
  `USDF_ONLY_DEEPLINK` (a dedicated USDF-only account, like iOS's
  `FLIPCASH_UI_TEST_USDF_ONLY_ACCESS_KEY`).

### Two phone-verification paths

- **Onboarding / account creation** uses the **backend test number** `+15005550000` with OTP `000000`
  (`create_account.yaml`). This is a backend test hook — no real SMS, no linkable identity.
- **Linking a phone (e.g. for onramp verification)** — status: **blocked on code delivery.** What's verified:
  - A valid-format number is required (the emulator's own `555-521-5554` is an invalid NPA and is
    rejected at phone entry). A number like `+1 415-555-0100` is accepted and the code is requested.
  - The app uses Android's **SMS User Consent** reader: an SMS delivered via
    `adb emu sms send <sender> "…code is 123456"` lands in the inbox and the app prompts to read it and
    auto-fills the code. **This path works.**
  - **But the backend validates the real code** — an injected placeholder is rejected
    ("Please enter a valid code"). The real code is sent to the entered number, which does **not** route
    to the emulator, so it never arrives and can't be read.
  - **To unblock:** the dev/staging backend must route the verification SMS for the test number **to this
    emulator** (e.g. a webhook that calls `adb emu sms send`), so the real code lands in the inbox and the
    app reads it. Once that exists, phone-linking is one-time per account.
- **Full Coinbase purchase** — the flow reaches the onramp; completing it needs phone verification
  (which links a phone to the shared account) plus driving the Google
  Pay sandbox sheet. Note: **iOS doesn't automate the payment either** — its E2E stops at the same
  onramp/verification boundary (`BuyApplePayRegressionTests`: unverified → verification sheet) and
  covers order-building/deposit/verification logic with unit tests (`OnrampOrderRequestTests`,
  `CoinbaseDepositOperationTests`, `OnrampVerificationViewModelTests`). So our `coinbase_onramp` entry
  test is at parity; the sandbox flag + method tag are in place if we later want to go further.

**Roadmap (tooling):**
- Get/Convert/Withdraw past confirmation on a funded account (screens tagged).
- Wire a `flipcash_maestro` Fastlane lane (see below).

## CI

Wired via the **`flipcash_maestro`** Fastlane lane and the **`.github/workflows/maestro.yml`**
workflow:

- The lane installs the debug build and runs `maestro/run.sh --tags <MAESTRO_TAGS>` (default
  `smoke`, excluding `spends-funds,creates-account`), emitting a JUnit report.

Side-effecting flows are tagged so runs stay clean: `spends-funds` (moves money) and
`creates-account` (onboards a new account, e.g. `tipping_setup.yaml`) are **excluded by
default**. `smoke` contains only read-only / fund-safe navigation. To run an account-creating
flow deliberately, clear the exclude, e.g. `MAESTRO_TAGS=tipping MAESTRO_EXCLUDE_TAGS= maestro/run.sh --tags tipping`.
- The workflow boots a KVM `x86_64` emulator (`reactivecircus/android-emulator-runner`), sets up
  the same build secrets as the unit-test job, installs the Maestro CLI, runs the lane, and uploads
  the report.
- Triggers: **`workflow_dispatch`** (choose `tags`/`exclude_tags`) and a **nightly schedule**
  (smoke). It's real-backend E2E against the shared account, so it's deliberately not on every PR;
  add a `pull_request:` trigger to gate PRs (won't run on fork PRs, which lack secrets).

Run locally the same way CI does:
```bash
MAESTRO_TAGS=smoke maestro/run.sh --tags smoke
```

**Required GitHub secrets** (test-account creds — the workflow maps them to the env vars
`run.sh` reads): `MAESTRO_SEED_PHRASE`, `MAESTRO_LOGIN_DEEPLINK`, `MAESTRO_TIPCARD_DEEPLINK`,
`MAESTRO_USDF_ONLY_DEEPLINK`, `MAESTRO_CONTACT_NAME`, `MAESTRO_CONTACT_PHONE` — plus the existing
build secrets (`FLIPCASH2_GOOGLE_SERVICES`, `FLIPCASH_BUGSNAG_API_KEY`, `FLIPCASH_MIXPANEL_API_KEY`,
`COINBASE_ONRAMP_API_KEY`, `GOOGLE_CLOUD_PROJECT_NUMBER`).
