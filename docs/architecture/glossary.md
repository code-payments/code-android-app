# Glossary

Domain and architecture terms used throughout this codebase and these docs.
Definitions are kept short; the **See** column points to the doc (or code) with the
full story.

## Product & money

| Term | Meaning | See |
|------|---------|-----|
| **USDF** | The USD-pegged **base / reserve** stablecoin (the "core mint"). Everything is priced in and backed by USDF; it is itself sendable. In code: a `Token` with `launchpadMetadata = null`. | [06](06-payments-and-operations.md) |
| **Launchpad token / currency** | The user-facing, tradable currency people **create, buy, sell, and share** — a custom on-chain token **backed by USDF reserves** and priced by an on-chain bonding curve. Like a memecoin backed by USDC. | [06](06-payments-and-operations.md) |
| **Core mint** | The codebase's name for USDF — the reserve currency that launchpad currencies are denominated in and backed by. | [06](06-payments-and-operations.md) |
| **Bonding curve** | The on-chain formula that sets a launchpad currency's price from its circulating supply (`price = f(supply)`, in USDF). | [06](06-payments-and-operations.md) |
| **Liquidity pool / `coreMintVault`** | A launchpad currency's bonding-curve pool; its `coreMintVault` holds the **USDF reserves** backing the token (vs `mintVault`, which holds the token). | [06](06-payments-and-operations.md) |
| **Reserves** | Two senses: (1) on-chain USDF backing a token in its `coreMintVault`; (2) the **user's own USDF balance** (`observeReservesBalance()`) spent to buy launchpad currencies. | [06](06-payments-and-operations.md) |
| **Cash bill** | A digital representation of "cash" — a note/denomination, like a US dollar — used for device-to-device transfers. Modeled as `Bill.Cash`; carries a token (a launchpad currency or USDF) and **displays a Kik Code** for the recipient to scan. One of the surfaces a Kik Code rides on. | [06](06-payments-and-operations.md), [features](features/README.md) |
| **Kik Code** | The scannable, animated **circular code** itself — the device-to-device transfer transport. It is **surface-agnostic**: today it's displayed on cash bills, soon on digital gold bars, and could later back other flows such as merchant payments. Captured via the camera scanner (`:ui:scanner`). | [07](07-design-system.md) |
| **On-ramp** | Funding the wallet with fiat (Coinbase) or in-app purchase. | [04](04-networking.md), [06](06-payments-and-operations.md) |
| **Swap** | Buying or selling a launchpad currency **against USDF** (`SwapPurpose.Buy`/`Sell`); sells charge a ~1% fee. | [03](03-navigation.md), [06](06-payments-and-operations.md) |
| **Withdrawal** | Moving funds on-chain to an external Solana address. | [features](features/README.md) |

## Identity, keys & accounts

| Term | Meaning | See |
|------|---------|-----|
| **Self-custodial** | Keys live on the device; the user — not a server — controls funds. | [06](06-payments-and-operations.md) |
| **Mnemonic** | The BIP39 word phrase from which all of a user's keys are derived. | [06](06-payments-and-operations.md) |
| **Entropy** | The raw seed bytes behind the mnemonic; its Base58 prefix also names the user's local database. | [05](05-persistence.md), [06](06-payments-and-operations.md) |
| **Access key** | The user-facing recovery credential (backed by the mnemonic) that the user saves during onboarding. | [features](features/README.md) |
| **Ed25519** | The signature scheme used to sign requests and transactions. | [04](04-networking.md), [06](06-payments-and-operations.md) |
| **AccountCluster** | The bundle of derived accounts that makes up a wallet: authority + timelock + per-token deposit addresses. | [06](06-payments-and-operations.md) |
| **Authority** | The master keypair that controls an account. | [06](06-payments-and-operations.md) |
| **Timelock** | The custody mechanism — vault accounts in a timelock virtual machine that hold funds. | [06](06-payments-and-operations.md) |
| **Vault** | A timelock account that actually holds a token balance. | [06](06-payments-and-operations.md) |
| **Rendezvous** | The keypair used to sign requests during the payment handshake (the cluster's authority key). | [06](06-payments-and-operations.md) |
| **Deposit address** | A per-token address derived from the cluster, used for on-ramp funding. | [06](06-payments-and-operations.md) |
| **AuthState** | The auth-state machine (`Unknown → Onboarding → Authenticating → Ready → LoggedOut`) owned by `UserManager`. | [06](06-payments-and-operations.md) |

## Backend & transactions

| Term | Meaning | See |
|------|---------|-----|
| **OCP / Open Code Protocol** | The gRPC backend for transactions, intents, swaps, and exchange rates (module `:services:opencode`). | [04](04-networking.md) |
| **Flipcash service** | The gRPC backend for accounts, profiles, chat, and activity (module `:services:flipcash`). | [04](04-networking.md) |
| **Intent** | A signed unit of money movement (transfer, remote send/receive, withdraw, swap, distribution) submitted over the `SubmitIntent` bidirectional stream. | [04](04-networking.md), [06](06-payments-and-operations.md) |
| **Mint** | A Solana token mint address (`PublicKey` subtype); identifies a token such as USDF (`Mint.usdf`) or a launchpad currency. | [06](06-payments-and-operations.md) |
| **MintMetadata / Token** | The model for any currency (`MintMetadata`, aliased `Token`). A non-null `launchpadMetadata` makes it a launchpad currency; `null` means it's USDF (the core mint). | [06](06-payments-and-operations.md) |
| **Protobuf / proto** | The Protocol Buffers contract; generated code lives in `:definitions:*:models` and is never hand-edited. | [13](13-protobuf-and-codegen.md) |
| **NotifiableError** | Marker for errors that represent bugs (not user-caused) and should alert via Bugsnag/Slack. | [14](14-error-handling.md) |

## Architecture roles

| Term | Meaning | See |
|------|---------|-----|
| **Coordinator** | App-layer, session/lifecycle-aware owner of a domain's cached, synced state; wraps stateless controllers. | [02](02-state-and-dependency-injection.md#roles-coordinators-controllers-managers-services) |
| **Controller** | A domain/feature API — often a stateless network gateway, or light UI-facing state. | [02](02-state-and-dependency-injection.md#roles-coordinators-controllers-managers-services) |
| **Manager** | Owner of a state machine / resource lifecycle (e.g. `UserManager`, `AuthManager`). | [02](02-state-and-dependency-injection.md#roles-coordinators-controllers-managers-services) |
| **Service** | Internal gRPC/REST adapter returning `Result<T>`; never exposed to the UI. | [02](02-state-and-dependency-injection.md#roles-coordinators-controllers-managers-services), [04](04-networking.md) |
| **CompositionLocal / `Local*`** | The ambient handle (e.g. `LocalRouter`) through which Compose reads app-wide controllers. | [02](02-state-and-dependency-injection.md) |
| **AppRoute** | A typed, serializable navigation destination (a Navigation3 `NavKey`). | [03](03-navigation.md) |
| **CodeNavigator** | The custom navigator wrapping the Navigation3 back stack. | [03](03-navigation.md) |
| **Convention plugin** | A `build-logic` Gradle plugin (`flipcash.android.*`) that standardizes a module's setup and dependencies. | [01](01-modules-and-boundaries.md) |

> Solana/Kin lineage: the codebase descends from the Code/Kin wallet; some packages
> use the legacy `com.getcode` namespace. Current accounts and tokens are
> Solana-based.
