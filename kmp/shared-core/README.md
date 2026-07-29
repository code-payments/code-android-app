# :kmp:shared-core — cross-platform shared core (beachhead)

Kotlin Multiplatform module that produces an **iOS XCFramework** (`SharedCore`) from
already-native-safe Kotlin sources, **without moving or editing any existing code**, and publishes it
to iOS as a **Swift Package** via TouchLab **KMMBridge**.

## What it does

- Compiles the existing `:libs:network:jwt` sources **in place** via `kotlin.srcDir(...)` — the files
  stay where they are and the original Android library module is unchanged and keeps building.
- Exports them (plus a `SharedCore.version` marker) into a static XCFramework.
- **KMMBridge** (`co.touchlab.kmmbridge.github`) assembles the XCFramework and generates/maintains the
  SPM `Package.swift` at the **repo root** so iOS consumes it as a normal Swift package.
- Has **no Android target** — the Android app continues to use the original modules. An `androidTarget`
  is added only when we deliberately switch Android over (a later milestone step).

## What can and can't be exported today

Only sources with **zero `java.*` / Android dependencies** can be added as-is:

| Module | Status | Blocker |
|---|---|---|
| `:libs:network:jwt` | ✅ exported | none — pure interfaces + data classes |
| `libs/encryption/base58` | ❌ needs changes | `java.math.BigInteger`, `java.security.MessageDigest` |
| `libs/models` (`ID`, `PointerStatus`) | ❌ needs changes | `java.util.UUID`, `java.nio.ByteBuffer`, cross-module deps |

Adding the blocked modules means swapping JVM APIs for KMP equivalents — i.e. editing those files —
which is deliberately out of scope for the beachhead. See `docs/shared-reality-milestones.md` (M2+).

## Local development (no publishing required)

```bash
# Build a debug XCFramework and rewrite the root Package.swift to a LOCAL path.
# Re-run after any Kotlin change.
./gradlew :kmp:shared-core:spmDevBuild
```

Then point an SPM consumer at the repo-root package. The dev link-test lives in the iOS repo at
`code-ios-app/SharedCoreLinkTest` and consumes it via a path dependency:

```swift
// SharedCoreLinkTest/Package.swift
dependencies: [ .package(path: "../../code-android-app") ],
// target dep: .product(name: "SharedCore", package: "code-android-app")
```

```swift
import SharedCore
let v = SharedCore.shared.version   // "0.0.1-beachhead"
```

Run it: `cd code-ios-app/SharedCoreLinkTest && swift test`.

## Publishing (CI — the production consumption path)

```bash
# Uploads the XCFramework to a GitHub Release of this repo and pins url+checksum in Package.swift.
# Needs GITHUB_REPO + GITHUB_PUBLISH_TOKEN in the environment.
./gradlew :kmp:shared-core:kmmBridgePublish
```

iOS then consumes the package by **Git URL + version** (no sibling checkout / local path needed):
add `https://github.com/code-payments/code-android-app.git` as a Swift Package dependency, product
`SharedCore`.

## ⚠️ The root `Package.swift` is KMMBridge-managed — don't hand-edit

- `spmDevBuild` rewrites it to a **local path** (`./kmp/shared-core/build/XCFrameworks/debug/...`).
- `kmmBridgePublish` rewrites it to a **released** `.binaryTarget(url:checksum:)`.
- Do **not** commit the local-path (dev) form as the canonical manifest — commit the published form
  once release publishing is wired. The `macos-arm64` slice exists only so `swift test` can link on
  the host; the app links the iOS slice.

## Note on in-place `srcDir`

Because this module compiles another module's source directory, those classes are compiled twice in
the Gradle build (once by the original Android module, once here for native). There is no conflict
today because nothing on the Android/Gradle side depends on `:kmp:shared-core` — it exists purely to
emit the iOS package. When Android eventually consumes the shared module, the source will move here
for real and the original module will be retired.
