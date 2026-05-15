---
name: fetch-protos
description: >
  Fetch latest protobuf definitions, verify build, summarize API changes,
  and scaffold new service stubs. Usage: /fetch-protos [flipcash|opencode] [commit_sha]
user-invocable: true
argument-hint: "[flipcash|opencode] [commit_sha]"
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

Fetch protobuf definitions from upstream repos, verify they compile, summarize
API changes, and scaffold missing service layer implementations.

## Pre-flight context

- Current proto files: !`find definitions/*/protos/src/main/proto -name "*.proto" 2>/dev/null | wc -l | tr -d ' '` proto files across targets
- Git status: !`git status --short definitions/`

## Input

Parse `$ARGUMENTS` to determine targets and optional commit SHA.

**Rules:**
- Known targets: `flipcash`, `opencode`
- If no targets specified, fetch **both** (`flipcash` and `opencode`)
- A hex string (7+ chars) as the last argument is treated as a commit SHA
- Examples:
  - `/fetch-protos` → fetch flipcash + opencode at HEAD
  - `/fetch-protos flipcash` → fetch flipcash only
  - `/fetch-protos opencode abc1234` → fetch opencode at commit abc1234
  - `/fetch-protos flipcash opencode` → fetch both explicitly

## Steps

### Step 1 — Fetch protos

For each target, run the fetch script from the repo root:

```bash
bash scripts/fetch-protos.sh -t <target> [commit_sha]
```

Target-to-repo mapping (handled by the script):
| Target | Repository |
|--------|-----------|
| `flipcash` | `git@github.com:code-payments/flipcash2-protobuf-api.git` |
| `opencode` | `git@github.com:code-payments/ocp-protobuf-api.git` |

Show the script output to the user.

### Step 2 — Diff and summarize changes

Run `git diff` on the proto directories to identify what changed:

```bash
git diff --stat definitions/
git diff definitions/
```

For each changed `.proto` file, summarize:
- **New RPCs** added to services
- **Modified RPCs** (changed request/response types or fields)
- **Removed RPCs**
- **New/modified messages** and fields

Present a structured change summary table to the user. If nothing changed, report
that protos are already up to date and stop here.

### Step 3 — Build verification

Build the definitions modules to verify the protos compile:

```bash
./gradlew :definitions:flipcash:models:assembleDebug :definitions:opencode:models:assembleDebug
```

Only build the targets that were fetched. If the build fails, show errors and stop.

### Step 4 — Detect service layer impact

For each new or modified RPC found in Step 2:

1. Identify which service proto file it belongs to (e.g., `account/v1/flipcash_account_service.proto`)
2. Search for the corresponding Api class in `services/<target>/src/**/network/api/`
3. Check if a method exists for the RPC
4. If the RPC is new, check whether Service, Repository, and Controller layers also need updates

Present a report:

| RPC | Api | Service | Repository | Controller | Status |
|-----|-----|---------|------------|------------|--------|
| `NewRpc` | missing | missing | missing | missing | **New — needs scaffolding** |
| `ModifiedRpc` | exists | exists | exists | exists | **Signature may need update** |

### Step 5 — Scaffold new service stubs

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

### Step 6 — Review and commit

Show the user a summary of all changes (proto updates + any scaffolded code).

Offer to commit with a conventional commit message:
```
chore(protos): update <target> protobuf definitions
```

If service stubs were also scaffolded, suggest a separate commit:
```
feat(<target>): scaffold service stubs for new RPCs
```

## Never

- Edit generated protobuf code in `definitions/*/models/build/`
- Commit without user approval
- Skip build verification
- Scaffold service code without asking the user first
