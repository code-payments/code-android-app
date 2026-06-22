# services/opencode-compose

A thin Compose-bindings layer over [`services/opencode`](../opencode/README.md). It
re-exports the opencode API (`api(:services:opencode)`) and surfaces the things
features read from the composition tree rather than inject directly.

> Namespace `com.getcode.opencode.compose`. See the CompositionLocal injection
> pattern in [02 — State & dependency injection](../../docs/architecture/02-state-and-dependency-injection.md).

## What's here

- **`LocalExchange`** (`Exchange.kt`) — `staticCompositionLocalOf<Exchange>` that
  exposes the OCP [`Exchange`](../opencode/README.md) (rates, preferred currency) to
  Compose. Provided in `MainActivity`'s `CompositionLocalProvider`; read with
  `LocalExchange.current`.
- **`ExchangeStub`** (`ExchangeStub.kt`) — an inert `Exchange` (empty/identity rates)
  used as the default when no real `Exchange` is provided (previews, tests).

## Why a separate module

`:services:opencode` is Compose-free (it's a plain library). This module adds the
Compose dependency and the `Local*` bindings, keeping the Compose runtime out of the
core service module. Features depend on `:services:opencode-compose` (often
transitively via `:services:flipcash-compose`) to get both the API and its ambient
handles.

## See also

- [`services/opencode`](../opencode/README.md) · [02 — State & DI](../../docs/architecture/02-state-and-dependency-injection.md) · [06 — Payments & operations](../../docs/architecture/06-payments-and-operations.md)
