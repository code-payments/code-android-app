# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Code/Flipcash is a mobile wallet app for instant, global, private payments using self-custodial blockchain (Solana/Kin) technology. The Android app is a multi-module Gradle project with 100+ modules.

## Build Commands

```bash
# Build debug APK
./gradlew :apps:flipcash:app:assembleDebug

# Build release bundle
./gradlew :apps:flipcash:app:bundleRelease

# Run unit tests (all modules)
./gradlew test

# Run unit tests for a specific module
./gradlew :apps:flipcash:features:<feature>:test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run tests via Fastlane (used in CI)
bundle exec fastlane android flipcash_tests
```

**Requirements**: Java 21 (Corretto), `google-services.json` in `apps/flipcash/app/src/`, API keys in `local.properties` (BUGSNAG_API_KEY, MIXPANEL_API_KEY, COINBASE_ONRAMP_API_KEY, GOOGLE_CLOUD_PROJECT_NUMBER). Keys resolve via `tryReadProperty` (local.properties → env var → empty), so a missing key won't fail the build but disables the dependent feature. See `docs/architecture/10-build-and-run.md`.

## Module Structure

```
apps/flipcash/
  app/              — Main application entry point (FlipcashApp, MainActivity)
  core/             — Base Compose utilities, payment models, billing state, cache policies
  features/         — 20+ isolated feature modules (login, cash, balance, etc.)
  shared/           — 30+ shared modules (auth, tokens, payments, notifications, etc.)

services/
  flipcash/         — Flipcash gRPC services and models
  opencode/         — Open Code Protocol gRPC services
  *-compose/        — Compose wrappers for services

definitions/
  flipcash/         — Protobuf definitions for Flipcash
  opencode/         — Protobuf definitions for OCP

libs/               — 20+ internal libraries
  crypto/           — Solana, Kin, Ed25519, encryption, key management
  network/          — Connectivity, JWT, exchange rates, Coinbase
  models, messaging, logging, etc.

ui/                 — Shared UI layer
  core/, components/, theme/, resources/
  navigation/, scanner/, biometrics/, analytics/

vendor/             — Third-party: Kik scanner, TipKit, OpenCV
build-logic/        — Convention plugins for consistent module setup
```

## Convention Plugins (build-logic)

All modules use convention plugins applied via `build.gradle.kts`:
- `flipcash.android.library` — Base Android library config (compile SDK 36, min SDK 29, Java 21)
- `flipcash.android.library.compose` — Adds Jetpack Compose support
- `flipcash.android.feature` — Full feature module: Compose + Hilt + KSP + Parcelize + common UI dependencies

The feature plugin automatically includes `:libs:logging`, `:ui:core`, `:ui:components`, `:ui:navigation`, `:ui:resources`, `:ui:theme`, and `:apps:flipcash:core`.

## Architecture

- **Pattern**: MVI/MVVM hybrid with Compose-driven UI and reactive state (`BaseViewModel<State, Event>`)
- **DI**: Hilt — all feature modules get Hilt via the convention plugin
- **Navigation**: Jetpack **Navigation 3** (`androidx.navigation3`) wrapped by a custom `CodeNavigator`; a custom `Router` resolves deeplinks. (No Voyager.)
- **Networking**: gRPC with Protobuf for backend services; Retrofit/OkHttp for REST
- **Async**: Kotlin Coroutines + Flow (no RxJava); dispatchers injected via `DispatcherProvider`
- **Persistence**: Room with a per-user database name derived from account entropy (not SQLCipher-encrypted); DataStore for preferences
- **Crypto**: Ed25519, mnemonic/key derivation, Solana SDK for on-chain operations

> Full architecture documentation lives in `docs/architecture/` (modules, state & DI, navigation, networking, persistence, payments, testing, and a "build & run" / "adding a feature" guide).

## Key Patterns

- **CompositionLocal injection**: `MainActivity` provides dozens of controllers/services via `CompositionLocalProvider` — features access dependencies through `Local*` composition locals rather than direct injection
- **Feature modules are self-contained**: Each has its own state, controllers, and UI; communicates via shared modules
- **Protobuf models**: Backend models are generated from `.proto` files in `definitions/`; don't hand-edit generated code
- **Dark mode only**: App forces `MODE_NIGHT_YES`

## Namespaces

- App: `com.flipcash.app.android` (debug: `com.flipcash.app.android.dev`)
- Legacy/shared: `com.getcode`
- Features: `com.flipcash.features.*`
- Shared modules: `com.flipcash.shared.*`
- UI: `com.getcode.ui.core`

## Git Conventions

- Conventional commits: `feat:`, `fix:`, `chore:`, with optional scope in parens (e.g., `feat(oc):`, `fix(tokens):`)
- Main branch: `code/cash`
- CI runs on all PRs (tests via Fastlane)