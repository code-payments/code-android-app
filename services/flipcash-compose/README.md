# services/flipcash-compose

The intended **Compose-bindings layer** over [`services/flipcash`](../flipcash/README.md),
mirroring what [`services/opencode-compose`](../opencode-compose/README.md) does for
opencode.

> Namespace `com.flipcash.services.flipcash.compose`.

> **Status: currently a shell.** This module has **no `src/` yet** — only a
> `build.gradle.kts` that `api(...)`-exports `:services:flipcash` and
> `:services:opencode-compose`. It exists so feature/UI code can depend on a single
> Compose-aware entry point for the Flipcash backend.

## What belongs here

Compose-facing bindings for `:services:flipcash` — the same shape as
`opencode-compose`'s `LocalExchange`: `staticCompositionLocalOf` handles and
`@Composable` providers/`remember*` helpers that expose Flipcash controllers/state to
the composition tree (see the CompositionLocal pattern in
[02 — State & dependency injection](../../docs/architecture/02-state-and-dependency-injection.md)).
Keep the Compose runtime here, out of the Compose-free `:services:flipcash` core.

## See also

- [`services/flipcash`](../flipcash/README.md) · [`services/opencode-compose`](../opencode-compose/README.md) · [02 — State & DI](../../docs/architecture/02-state-and-dependency-injection.md)
