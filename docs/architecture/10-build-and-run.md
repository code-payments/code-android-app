# 10 — Build & run

How to get a debug build of Flipcash compiling and running locally, and what the
build actually needs from you. This is the day-one setup doc.

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| **JDK 21** | Amazon Corretto 21 is what CI uses. The convention plugins pin the Java/Kotlin toolchain to 21 ([01](01-modules-and-boundaries.md)). |
| **Android SDK** | `compileSdk 36`, `minSdk 29`, `targetSdk` per the version catalog. |
| **`google-services.json`** | Required at `apps/flipcash/app/src/google-services.json` (Firebase). The build fails without it. |
| **`local.properties`** | API keys (below) plus the standard `sdk.dir`. |

## API keys in `local.properties`

Keys are read by `tryReadProperty(...)`
([`buildSrc/.../LocalPropertyFetcher.kt`](../../buildSrc/src/main/java/LocalPropertyFetcher.kt)),
which resolves **`local.properties` → environment variable → empty string**. A
missing key therefore **does not fail the build** — it compiles with an empty value
and the dependent feature simply won't work. The keys that are actually consumed:

| Key | Read in | Powers |
|-----|---------|--------|
| `MIXPANEL_API_KEY` | `apps/flipcash/app/build.gradle.kts` → `BuildConfig`, used in `app/.../inject/ApiModule.kt` | Analytics ([08](08-cross-cutting-concerns.md)) |
| `BUGSNAG_API_KEY` | `apps/flipcash/app/build.gradle.kts` → manifest placeholder | Crash/error reporting ([08](08-cross-cutting-concerns.md)) |
| `COINBASE_ONRAMP_API_KEY` | `apps/flipcash/shared/onramp/coinbase/build.gradle.kts` → `BuildConfig` | Coinbase on-ramp ([04](04-networking.md)) |
| `GOOGLE_CLOUD_PROJECT_NUMBER` | `services/{flipcash,opencode}{,-compose}/build.gradle.kts` → `BuildConfig` | gRPC / backend integration |

A minimal `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
MIXPANEL_API_KEY=...
BUGSNAG_API_KEY=...
COINBASE_ONRAMP_API_KEY=...
GOOGLE_CLOUD_PROJECT_NUMBER=...
```

> There is **no** `FINGERPRINT_API_KEY`. (`Build.FINGERPRINT` appears in
> `GooglePayReadiness` for emulator detection — unrelated to any key.) In CI these
> values come from secrets injected into `local.properties` by `.github/workflows/ci.yml`.

## Common Gradle commands

```bash
# Debug APK
./gradlew :apps:flipcash:app:assembleDebug

# Release bundle (AAB)
./gradlew :apps:flipcash:app:bundleRelease

# All unit tests
./gradlew test

# Unit tests for one module (fast inner loop)
./gradlew :apps:flipcash:features:cash:test
./gradlew :apps:flipcash:shared:router:test

# Instrumented tests (needs a device/emulator)
./gradlew connectedAndroidTest

# What CI runs (unit tests via Fastlane)
bundle exec fastlane android flipcash_tests
```

See [12 — Testing](12-testing.md) for the testing approach.

## Build variants & namespaces

| | Application ID |
|---|----------------|
| Release | `com.flipcash.app.android` |
| Debug | `com.flipcash.app.android.dev` (suffix lets debug + release coexist on one device) |

The app forces **dark mode** (`MODE_NIGHT_YES`, see [07](07-design-system.md)).
`versionCode` comes from `Packaging.Flipcash.versionCode` or `gitVersionCode()`.

## Module structure at a glance

The project is ~132 modules; the directory a module lives in defines its layer and
the convention plugin it applies. If you're about to add code, read
[01 — Modules & boundaries](01-modules-and-boundaries.md) and
[11 — Adding a feature](11-adding-a-feature.md) first.

## CI

[`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) runs on PRs: it sets up
JDK 21 + Ruby, decodes `google-services.json`, writes the API-key secrets into
`local.properties`, and runs `bundle exec fastlane android flipcash_tests`
(Fastfile lane `flipcash_tests`). Other workflows handle version bumps, dev prep,
and the upload pipeline.

## Troubleshooting

- **`google-services.json` missing** → place it at `apps/flipcash/app/src/`.
- **A feature behaves as if unconfigured** (no analytics, on-ramp fails) → the
  corresponding key is absent from `local.properties` (the build won't warn loudly,
  because the fallback is an empty string).
- **Wrong JDK** → ensure `JAVA_HOME` points at a 21 JDK; the toolchain is pinned but
  Gradle itself must launch on a compatible JVM.
