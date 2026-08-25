---
name: fetch-protos
description: >
  Bump a client-protocol artifact, summarize the contract changes it carries,
  and scaffold new service stubs. Usage: /fetch-protos [flipcash|opencode] [version]
user-invocable: true
argument-hint: "[flipcash|opencode] [version]"
allowed-tools:
  - Bash
  - Read
  - Edit
  - Write
  - Glob
  - Grep
  - Agent
---

# Fetch Protos

The protos are no longer vendored here. Both contracts arrive as published
artifacts, so "fetching" is bumping a version pin and reacting to what the new
version changed.

| Target | Artifact | Client repo | Upstream contract |
|--------|----------|-------------|-------------------|
| `flipcash` | `com.flipcash:flipcash2-client-protocol` | `code-payments/flipcash2-client-protocol` | `code-payments/flipcash2-protobuf-api` |
| `opencode` | `com.flipcash:ocp-client-protocol` | `code-payments/ocp-client-protocol` | `code-payments/ocp-protobuf-api` |

## Pre-flight context

- Pinned versions: !`grep -E "^(ocp|flipcash2)-client-protocol = " gradle/libs.versions.toml`
- Git status: !`git status --short gradle/libs.versions.toml services/`

## Input

Parse `$ARGUMENTS` to determine targets and an optional version.

**Rules:**
- Known targets: `flipcash`, `opencode`
- If no targets specified, check **both**
- A semver-looking string as the last argument is the version to move to; without
  one, use the latest release
- Examples:
  - `/fetch-protos` → check both artifacts for newer releases
  - `/fetch-protos flipcash` → flipcash only, latest release
  - `/fetch-protos opencode 0.2.0` → opencode at 0.2.0

## Steps

### Step 1 — Find the release

```bash
gh release list --repo code-payments/<ocp|flipcash2>-client-protocol --limit 10
```

Compare against the pin in `gradle/libs.versions.toml`. If the pinned version is
already the latest and no version was requested, say so and stop.

If the contract change you want has **not been released**, it has to land in the
client repo first: sync its protos at the upstream SHA, regenerate, and publish.
That repo's README covers it — this skill does not do it.

### Step 2 — Bump the pin

Edit `gradle/libs.versions.toml`:

```toml
ocp-client-protocol = "<new>"          # or flipcash2-client-protocol
```

### Step 3 — Diff and summarize the contract change

The `.proto` sources are not in this repo. Diff them between the two release tags:

```bash
gh api repos/code-payments/<repo>/compare/<old-version>...<new-version> \
  --jq '.files[] | select(.filename | startswith("proto/")) | .filename'
```

Read the patch for each changed file. Summarize:
- **New RPCs** added to services
- **Modified RPCs** (changed request/response types or fields)
- **Removed RPCs**
- **New/modified messages** and fields

Present a structured change summary table. If the diff carries no `proto/` change,
the release is generator or packaging work only — say so, and expect no service
layer impact.

### Step 4 — Build verification

Build the service module that consumes the artifact:

```bash
./gradlew :services:<flipcash|opencode>:assembleDebug
```

Only build the targets that were bumped. If the build fails, show errors and stop —
a removed or renamed field breaks compilation here, which is the point.

### Step 5 — Detect service layer impact

#### 5a — RPC changes

For each new or modified RPC found in Step 3:

1. Identify which service proto file it belongs to (e.g., `account/v1/flipcash_account_service.proto`)
2. Search for the corresponding Api class in `services/<target>/src/**/network/api/`
3. Check if a method exists for the RPC
4. If the RPC is new, check whether Service, Repository, and Controller layers also need updates

Present a report:

| RPC | Api | Service | Repository | Controller | Status |
|-----|-----|---------|------------|------------|--------|
| `NewRpc` | missing | missing | missing | missing | **New — needs scaffolding** |
| `ModifiedRpc` | exists | exists | exists | exists | **Signature may need update** |

#### 5b — Message field changes (domain models)

For each message with added or removed fields (e.g., `UserFlags`, `UserProfile`):

1. Search for the corresponding domain model in `services/<target>/src/**/models/`
2. Search for the corresponding mapper in `services/<target>/src/**/internal/domain/`
3. For **added fields**: add the property to the domain data class, set a sensible
   default in the `Default` companion, and map it in the mapper
4. For **removed fields**: remove the property from the domain data class, its
   default, and the mapper line

**Common type mappings** (match existing fields on the same model):

| Proto type | Domain type | Mapping |
|------------|-------------|---------|
| `uint64` amount/quarks fields | `Fiat` | `Fiat(quarks = from.fieldName)` |
| `bool` | `Boolean` | `from.fieldName` |
| `string` | `String` | `from.fieldName` |
| `int32`/`uint32` | `Int` | `from.fieldName` |
| `Duration` | `kotlin.time.Duration` | `from.fieldName.seconds.toDuration(DurationUnit.SECONDS)` |
| `enum` | sealed/enum domain type | `from.fieldName.toDomain()` (add private extension) |

When in doubt, look at how neighboring fields on the same message are typed and
mapped — follow the same pattern.

##### UserFlags-specific chain

When `UserFlags` fields change, the following files form a chain that must all be
updated together. Ask the user whether the new field should be **read-only** (display
only) or **editable** (overridable via the debug editor).

| # | File | What to update |
|---|------|----------------|
| 1 | `services/flipcash/src/**/models/UserFlags.kt` | Add/remove property + `Default` companion value |
| 2 | `services/flipcash/src/**/internal/domain/UserFlagsMapper.kt` | Add/remove mapping line in `map()` |
| 3 | `apps/flipcash/shared/userflags/src/**/ResolvedUserFlags.kt` | Add/remove `ResolvedFlag<T>` property + line in `resolve()` extension |
| 4 | `apps/flipcash/features/userflags/src/**/internal/UserFlagsViewModel.kt` | Add to `readOnlyEntries` (bool) or `editableEntries()` list |

If the field is **editable** (overridable), also update:

| # | File | What to update |
|---|------|----------------|
| 5 | `apps/flipcash/shared/userflags/src/**/UserFlagsCoordinator.kt` | Add `FieldOverride<T>` to `Overrides` data class + `Overrides.None` + `overrides` flow mapping |
| 6 | `apps/flipcash/shared/userflags/src/**/Field.kt` | Add `data object` subclass with preference key, encode/decode, label, editor |
| 7 | `apps/flipcash/shared/userflags/src/main/res/values/strings.xml` | Add `label_flag_*` (and `hint_flag_*` if needed) string resources |

For **read-only** fields (e.g., booleans like `enablePhoneNumberSend`):
- In `ResolvedUserFlags.resolve()`, use `FieldOverride.None` (no override support)
- In `UserFlagsViewModel`, add to `readOnlyEntries` with a string resource label
- No changes needed in `Overrides`, `Field.kt`, or `UserFlagsCoordinator`

Present a report of domain model updates needed and apply them after user confirmation.

### Step 6 — Scaffold new service stubs

For RPCs marked as needing scaffolding, ask the user if they want to scaffold them.
If confirmed, generate code following the patterns below.

#### Api method pattern

Location: `services/<target>/src/main/kotlin/.../internal/network/api/<ServiceName>Api.kt`

```kotlin
// @Singleton class with @Inject constructor taking qualified ManagedChannel
// private val api = XxxGrpcKt.XxxCoroutineStub(managedChannel).withWaitForReady()

suspend fun newRpc(owner: KeyPair, ...): RpcServiceName.NewRpcResponse {
    val request = RpcServiceName.NewRpcRequest.newBuilder()
        .apply { setAuth(authenticate(owner)) }  // or .apply { setSignature(sign(owner)) }
        // ... set other fields
        .build()

    request.validate().orThrow()

    return withContext(Dispatchers.IO) {
        api.newRpc(request)
    }
}
```

Key conventions:
- Use `authenticate(owner)` for Flipcash endpoints (returns `Common.Auth`)
- Use `sign(owner)` for OpenCode endpoints (returns `Model.Signature`)
- Always call `request.validate().orThrow()` before the RPC
- Always dispatch on `Dispatchers.IO`
- Return raw proto response type

#### Service method pattern

Location: `services/<target>/src/main/kotlin/.../internal/network/services/<ServiceName>Service.kt`

```kotlin
// internal class with @Inject constructor(private val api: XxxApi)

suspend fun newRpc(owner: KeyPair, ...): Result<DomainType> {
    return runCatching {
        api.newRpc(owner, ...)
    }.foldWithSuppression(
        onSuccess = { response ->
            when (response.result) {
                RpcServiceName.NewRpcResponse.Result.OK -> Result.success(/* mapped value */)
                RpcServiceName.NewRpcResponse.Result.DENIED -> Result.failure(NewRpcError.Denied())
                RpcServiceName.NewRpcResponse.Result.UNRECOGNIZED -> Result.failure(NewRpcError.Unrecognized())
                else -> Result.failure(NewRpcError.Other())
            }
        },
        onFailure = { cause ->
            Result.failure(cause.toValidationOrElse { NewRpcError.Other(cause = it) })
        }
    )
}
```

#### Error sealed class pattern

Location: `services/<target>/src/main/kotlin/.../models/Errors.kt`

```kotlin
sealed class NewRpcError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : NewRpcError("Denied")
    class Unrecognized : NewRpcError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : NewRpcError(message = cause?.message, cause = cause), NotifiableError
}
```

Add a subclass for each non-OK result enum value in the proto response. Mark
`Unrecognized` and `Other` with `NotifiableError`. Mark expected/benign errors
(e.g., `NotFound`, `Denied`) without `NotifiableError`.

#### Repository pattern

- **Interface** in `services/<target>/src/main/kotlin/.../repository/`:
  ```kotlin
  suspend fun newRpc(...): Result<DomainType>
  ```
- **Internal impl** in `services/<target>/src/main/kotlin/.../internal/repositories/`:
  ```kotlin
  override suspend fun newRpc(...): Result<DomainType> {
      return service.newRpc(...)
          .map { mapper.map(it) }  // if domain mapping needed
          .onFailure { if (it !is NewRpcError.ExpectedCase) ErrorUtils.handleError(it) }
  }
  ```

#### Controller pattern

Location: `services/<target>/src/main/kotlin/.../controllers/<ServiceName>Controller.kt`

```kotlin
// @Singleton class with @Inject constructor(repository, userManager)

suspend fun newRpc(...): Result<DomainType> {
    val owner = userManager.accountCluster?.authority?.keyPair
        ?: return Result.failure(Throwable("No account cluster"))
    return repository.newRpc(owner, ...)
}
```

#### Hilt wiring

If a new Repository interface+impl pair was created, add a `@Provides` binding in
the corresponding Hilt module (`FlipcashModule.kt` or `OpenCodeModule.kt`).

### Step 7 — Review and commit

Show the user a summary of all changes (proto updates + any scaffolded code).

Offer to commit with a conventional commit message:
```
chore(protos): bump <flipcash2|ocp>-client-protocol to <version>
```

If service stubs were also scaffolded, suggest a separate commit:
```
feat(<target>): scaffold service stubs for new RPCs
```

## Never

- Try to edit the generated protobuf code — it lives in the published artifact
- Commit without user approval
- Skip build verification
- Scaffold service code without asking the user first
