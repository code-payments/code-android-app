# Bugsnag Event Shape — Android

A single event fetched from `GET /events/{event_id}` (with project auth).

## Four Evidence Sources

### 1. App Logs — `metaData["App Logs"]["app_log"]`

A single string containing the last ~64 KB of log output captured at crash time.
Attached by `FlipcashBugsnagErrorCallback` in
`apps/flipcash/app/src/main/kotlin/com/flipcash/app/internal/debug/FlipcashBugsnagErrorCallback.kt`.

Unlike iOS's structured `metaData.app_logs.recent_logs` array, this is
unstructured text. Grep it for keywords from the exception to find relevant
context.

### 2. Stack Trace — `exceptions[0].stacktrace`

Array of frame objects:

```json
{
  "file": "com/flipcash/features/cash/CashViewModel.kt",
  "method": "com.flipcash.features.cash.CashViewModel.loadBalance",
  "lineNumber": 42,
  "inProject": true,
  "columnNumber": null
}
```

- `file` — path-like representation of the Kotlin/Java source file using
  package-qualified slashes (e.g. `com/flipcash/features/cash/CashViewModel.kt`)
- `method` — fully qualified method name with package
- `lineNumber` — source line (may be approximate after R8/ProGuard)
- `inProject` — `true` for app code, `false` for framework / library code

**Path mapping**: Android frames use Java package paths. To find the source
file, either:
- Convert to a glob: `**/CashViewModel.kt` and search the repo
- Or convert dots to slashes and search: `com/flipcash/features/cash/CashViewModel.kt`

### 3. Breadcrumbs — `breadcrumbs[]`

Array of timestamped events (same structure as iOS):

```json
{
  "timestamp": "2026-05-10T14:23:01.000Z",
  "name": "Navigate to CashScreen",
  "type": "navigation",
  "metaData": { "route": "/cash" }
}
```

Types correspond to `BreadcrumbType` values: `ERROR`, `LOG`, `NAVIGATION`,
`REQUEST`, `PROCESS`, `STATE`, `USER` (see `BugsnagBreadcrumbSink`).

### 4. Exception Info — `exceptions[0]`

```json
{
  "errorClass": "java.lang.NullPointerException",
  "message": "Attempt to invoke virtual method 'void ...' on a null object reference",
  "type": "android"
}
```

Replaces iOS's `nserror` concept. The `errorClass` is the Java/Kotlin exception
class name; `message` is the detail string.

For Kotlin-specific exceptions, look for:
- `kotlin.KotlinNullPointerException`
- `kotlinx.coroutines.JobCancellationException`
- `java.util.concurrent.CancellationException`
- `IllegalStateException` (often lifecycle-related)

## Secondary Context

| Path | Notes |
|------|-------|
| `app.version` | versionName (e.g. `2026.5.3`) |
| `app.versionCode` | Integer version code |
| `app.releaseStage` | `production` / `development` |
| `device.manufacturer` | e.g. `Samsung`, `Google` |
| `device.model` | e.g. `Pixel 8`, `SM-S918B` |
| `device.osVersion` | Android version string (e.g. `14`) |
| `device.totalMemory` | Total RAM in bytes |
| `device.freeMemory` | Free RAM at crash time |
| `user.id` | Anonymized user identifier |
| `session` | Session start, events handled/unhandled |
| `featureFlags[]` | Active feature flags at crash time |

## Filtering Noise

The app's error callback (`FlipcashBugsnagErrorCallback`) already filters:
- gRPC status codes in `ErrorUtils.ignoredGrpcStatusCodes` (transport, validation)
- Handled gRPC `INTERNAL` errors

So events that reach Bugsnag are either unhandled crashes or explicitly notified
errors that passed the filter.
