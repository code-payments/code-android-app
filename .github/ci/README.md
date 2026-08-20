# CI fixtures

Files here exist so that the `flipcash-tests` job in [`../workflows/ci.yml`](../workflows/ci.yml)
can build, unit-test and lint the app **without any credentials**.

## Why

GitHub does not expose repository Actions secrets to `pull_request` runs opened by
`dependabot[bot]` — Dependabot reads from its own secret store — or by forks. On those runs every
`secrets.*` expression resolves to an empty string. Two things then break before Gradle produces
anything useful:

1. `google-services.json` never gets written, so the `com.google.gms.google-services` plugin fails
   to configure `:apps:flipcash:app`.
2. The secrets Gradle plugin copies each `local.properties` entry into `BuildConfig` verbatim, so
   an empty value emits `public static final String BUGSNAG_API_KEY = ;` and
   `compileDebugJavaWithJavac` fails.

The result was that every dependency PR was red for a reason unrelated to the bump, which destroys
the signal: a genuinely broken bump looked exactly like a healthy one.

The job therefore falls back to placeholders whenever a secret is empty. That is safe because the
lane runs `generateEmojiList flipcashTestDebug :apps:flipcash:app:lintDebug` — it compiles,
unit-tests and lints, and never contacts Firebase, Bugsnag, Mixpanel or Coinbase.

## `google-services.placeholder.json`

A **fake** Firebase config, committed on purpose. Structurally valid, deliberately meaningless:
project number `000000000000`, project id `flipcash-ci-placeholder`, zero-filled API key.

`client[].client_info.android_client_info.package_name` must stay in sync with the app's
`applicationId` (`com.flipcash.app.android`) or the plugin errors with "No matching client found
for package name".

## `local.properties` placeholders

Not a file — the fallbacks are inline in the workflow's `Write local.properties` step, zero-filled
for `BUGSNAG_API_KEY`, `MIXPANEL_API_KEY`, `COINBASE_ONRAMP_API_KEY` and
`GOOGLE_CLOUD_PROJECT_NUMBER`. Each key is substituted independently, so a run with only some
secrets available still uses the real ones it has, and the job logs a notice naming every key it
faked.

## Do not put real keys here

Anything that needs real credentials — release builds, upload lanes, the Maestro E2E suite — reads
them from secrets in its own workflow. If a job in `ci.yml` ever needs the *real* values on
Dependabot PRs, add them under **Settings → Secrets and variables → Dependabot** (repo admin
required) under the same names `ci.yml` already references.
