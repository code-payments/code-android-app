# Solana dApp Store publishing: restore and keep in step with Play

## Open questions (block step 1)

1. **Which package does the dApp Store list?** The legacy setup registered `com.flipcash.android.app`
   (app NFT `BiJpNgpFRtxWfgSgypWp7q8VddRgmAHpKPU5KtM8CXvr`). The current app is `com.flipcash.app.android`
   (`appNamespace` in `apps/flipcash/app/build.gradle.kts`). CLI 1.x picks the portal app from the APK's
   package name, and its help text says the app "must already exist in the portal and already have its
   App NFT". So someone has to create (or get Solana Mobile to migrate) a portal entry for
   `com.flipcash.app.android` before any upload can work.
   *Default:* create a new portal app for `com.flipcash.app.android` and retire the legacy listing.
2. **Which key signs the dApp Store APK?** The legacy script took `DAPP_KEYSTORE_PATH` from `.env.local`.
   `build_flipcash` signs its bundletool universal APK with the Play upload key (`STORE_FILE`). The dApp
   Store requires one signing key for every release of an app. If the listing is new (question 1), this
   is a free choice.
   *Decided:* publish the Google-signed universal APK that Play generates from the production bundle, not
   the bundletool APK. That way both stores ship byte-identical APKs under Google's app signing key, so
   updates can cross between stores and anything keyed to the cert fingerprint behaves the same.
3. **Solana signer keypair and portal API key:** who holds them, and may they go into GitHub secrets?
   *Default:* yes, as `DAPP_STORE_API_KEY` and `DAPP_STORE_KEYPAIR_JSON` (base64), scoped to a
   `dapp-store` GitHub environment that needs approval.

## What history has

| What | Where | Status today |
|---|---|---|
| `config.yaml`, `package.json` (CLI `^0.11.0`), `pnpm-lock.yaml` | `apps/flipcash/app/dapp_publishing/` | deleted in `f4d91c2fbd` (FC Legacy cleanup) |
| `scripts/dapp-store-publish.sh`, `scripts/dapp-store-apk-relative-path.sh` | `scripts/` | still present, but they call `create release` / `publish submit`, which no longer exist |
| Store media (banner, feature graphic, promo video, screenshots) | `fastlane/metadata/flipcash/en-US/` | still present, last touched July 2025 (`601ae7bb53`) |
| `.gitignore` entries for `node_modules` and `.asset-manifest.json` | `.gitignore` | still present |
| Last on-chain submission | `config.yaml` → `lastSubmittedVersionOnChain` | versionCode 2625; Play production is now 4875 |

## Why the old config can't come back as-is

`@solana-mobile/dapp-store-cli` went from 0.11 (what we used) to 1.0.1 (released 2026-09-14).
`solana-mobile/dapp-publishing#323` (0.16.1) removed `init`, `create`, `validate`, `publish submit|update`,
and the whole `config.yaml` schema. The CLI now has a single publish command plus `resume`:

```
dapp-store --apk-file <apk> --whats-new "<text>" --keypair <signer.json>
# API key from $DAPP_STORE_API_KEY (or --api-key-env / --api-key-stdin)
# Portal URL from $DAPP_STORE_PORTAL_URL, defaults to production
dapp-store resume --release-id <id> [--session-id <id>]
```

Listing metadata (descriptions, screenshots, banner, video) now lives in the publisher portal, not the
repo. The docs page at docs.solanamobile.com/dapp-publishing/publishing-updates still describes the old
mint-then-`publish update` flow. `packages/cli/src/CliSetup.ts` upstream is the source of truth.

## Plan

### 1. Portal setup (manual, outside the repo)
- Resolve questions 1–3.
- Create the app for `com.flipcash.app.android` in the publisher portal, mint its App NFT, and upload the
  listing media from `fastlane/metadata/flipcash/en-US/` (the media still meets the banner/feature graphic
  requirements; refresh screenshots if they're stale).
- Generate a portal API key.

### 2. Restore the publishing package
- `apps/flipcash/app/dapp_publishing/package.json`: pin `@solana-mobile/dapp-store-cli` to exactly `1.0.1`
  and commit a fresh lockfile. Don't restore the 0.11 lockfile from history.
- No `config.yaml`. Drop the `.asset-manifest.json` line from `.gitignore`.

### 3. Rewrite the local script
- `scripts/dapp-store-publish.sh <apk> [whats-new]`: sources `.env.local` if present
  (`DAPP_STORE_API_KEY`, `DAPP_STORE_KEYPAIR_PATH`), runs the CLI from `dapp_publishing/`, and defaults
  `--whats-new` to `scripts/changelog.sh` output between the last two `fcash/*` tags.
- Before uploading, check that the APK's `versionCode` equals Play production in
  `.well-known/release-manifest.json`, so we never publish a build Play doesn't have.
- Delete `scripts/dapp-store-apk-relative-path.sh`. The new CLI takes a plain path, so the `grealpath`
  dependency goes away too.

### 4. CI workflow (manual trigger)
- `scripts/download-play-universal-apk.sh` fetches the Google-signed universal APK for a versionCode
  (default: production in `.well-known/release-manifest.json`) through the Android Publisher
  `generatedApks` endpoints, using the existing service account. It also writes that release's
  `en-US` production release notes from Play, which become `--whats-new`. This is a script rather than
  a fastlane lane because `download_universal_apk_from_google_play` first shipped in fastlane 2.214.0
  and we pin 2.212.1. The script shares token minting with `update-release-manifest.sh` through
  `scripts/lib/play-api.sh`.
- `.github/workflows/dapp-store-publish.yml`, triggered two ways:
  - `push` to `code/cash` with `paths: [.well-known/release-manifest.json]`. `bump-patch.yml` pushes
    the manifest with `BOT_GITHUB_TOKEN` (a PAT), so that push does fire other workflows. A push made
    with `GITHUB_TOKEN` would not.
  - `workflow_dispatch` with an optional versionCode, for retries and backfills.
  No build step and no build artifact: Play is the source of the APK.
- First job, `check`: read the manifest and continue only if `production.versionCode > dappStore.versionCode`.
  The path filter alone isn't enough, because internal/alpha/beta updates touch the same file.
- `concurrency: dapp-store-publish` so two manifest pushes in quick succession can't publish twice.
- The publish job runs in the `dapp-store` environment. Keep a required reviewer if you want a person
  to approve each publish; drop it for fully automatic.
- Steps: download the APK, decode the keypair with `timheuer/base64-to-file` (same pattern as the
  deploy job), run `scripts/dapp-store-publish.sh`. The CLI runs with `--verbose`, so a failed
  run's log has the release and session ids for a manual `dapp-store resume`. A `force` rerun reuses
  `--idempotency-key flipcash-<versionCode>`. Whether the portal treats that as a resume or as a
  duplicate is untested.
- The local script takes the same downloaded APK. The bundletool APK in `release-artifacts` is not
  published anywhere.
- Staged rollouts are covered already: `update-release-manifest.sh` records only `completed` releases,
  so a partial rollout never reaches `tracks.production`.
- No reviewer on the `dapp-store` environment, so publishing is fully automatic. The manifest commit
  goes out with `BOT_GITHUB_TOKEN` like `bump-patch.yml` does. It retriggers this workflow, and `check`
  then stops it because production equals `dappStore`.
- The CLI refuses to run when a newer minor or major version exists (`selfUpdate.ts`), and
  `--skip-self-update` needs `--local-dev`. The pin is exact, so a Dependabot npm entry for
  `apps/flipcash/app/dapp_publishing` raises each bump as a PR. Until that PR merges, publishing fails
  with the CLI's "Please update" error.

### 5. Drift guard (keeps it in step with Play)
- Add a `dappStore: { versionCode, versionName }` entry to `.well-known/release-manifest.json`, written by
  the publish workflow on success. It replaces the old `lastSubmittedVersionOnChain`.
- In `scripts/update-release-manifest.sh` (run by `bump-patch.yml`), emit a warning and a
  `dapp_behind=true` output when `production.versionCode > dappStore.versionCode`, and show it in the
  bump-patch summary.

### 6. Fastlane cleanup (done)
- `download_from_playstore_flipcash` still hardcodes `com.flipcash.android.app`; switch it to
  `com.flipcash.app.android`. Use it to refresh `fastlane/metadata/flipcash` so the media we upload to the
  portal matches Play.

## Acceptance
- `scripts/dapp-store-publish.sh` against the staging portal (`DAPP_STORE_PORTAL_URL=https://staging.publish.solanamobile.com`)
  with the current production APK completes, or fails only at a portal-side gate we can name.
- The workflow, dispatched against the latest production run, publishes `2026.10.3 (4875)` and writes
  `dappStore.versionCode: 4875` to the manifest.
- The next `bump-patch.yml` run that records a new production versionCode starts `dapp-store-publish.yml`
  on its own, and a run that records only internal-track changes stops at `check`.
