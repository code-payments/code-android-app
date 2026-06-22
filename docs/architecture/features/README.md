# Feature catalog

The app has **26 feature modules** under
[`apps/flipcash/features/`](../../../apps/flipcash/features/). Each is
self-contained — it owns its screens, ViewModels, and Hilt wiring, inherits the UI
layer and `:apps:flipcash:core` from the `flipcash.android.feature` convention
plugin ([01](../01-modules-and-boundaries.md)), and is reachable through a route in
`AppRoute` registered in `appEntryProvider` ([03](../03-navigation.md)).

This catalog highlights **representative features per category**, not every module.
Paths are relative to each module's `src/main/kotlin/`.

## Patterns

Two recurring shapes show up in the **Pattern** column:

- **VM-driven** — a `BaseViewModel<State, Event>` ([02](../02-state-and-dependency-injection.md))
  coordinates multi-step state and async work; the screen renders `state` and
  dispatches `Event`s.
- **Flow** — a `FlowRoute` / `FlowRouteWithResult<T>` whose screen hosts an inner
  back stack of steps and (often) returns a typed result to its caller.

## A. Onboarding & auth

| Feature | Purpose | Screen / ViewModel | Pattern & integration |
|---------|---------|--------------------|-----------------------|
| **login** | Account creation, seed login, access-key, and permission steps | `login/OnboardingFlowScreen.kt` · `…/internal/SeedInputViewModel`, `LoginAccessKeyViewModel` | **Flow** (`AppRoute.OnboardingFlow`); drives `UserManager`/`AuthState`, hands off to **purchase** |
| **purchase** | First-purchase / account funding during onboarding | `purchase/PurchaseAccountScreen.kt` · `…/internal/PurchaseAccountViewModel` | **VM-driven**; uses `PurchaseMethodController` ([06](../06-payments-and-operations.md)) |
| **backupkey** | Show / confirm the recovery access key | `backupkey/BackupKeyScreen.kt` · `…/internal/BackupKeyScreenViewModel` | **VM-driven**; reads mnemonic from `UserManager` |

## B. Money movement

| Feature | Purpose | Screen / ViewModel | Pattern & integration |
|---------|---------|--------------------|-----------------------|
| **cash** | Amount entry for a device-to-device cash bill (sends the selected token — a launchpad currency or USDF) | `cash/CashScreen.kt` · `…/internal/CashScreenViewModel` | **VM-driven**; combines token + balance + rate flows, emits `PresentBill`, calls `SessionController.showBill` |
| **scanner** | Camera capture of a peer's cash bill / QR | `scanner/ScannerScreen.kt` | Screen-local; uses `:ui:scanner`, routes scans through `Router.classify` |
| **direct-send** | Send to a contact / phone number | `directsend/SendFlowScreen.kt` · `…/internal/SendFlowViewModel` | **Flow**; contact list + phone gate steps |
| **withdrawal** | Withdraw funds on-chain | `withdrawal/WithdrawalFlowScreen.kt` · `withdrawal/WithdrawalViewModel` | **Flow** (`AppRoute.Transfers.Withdrawal`); entry → destination → confirmation |
| **deposit** | Add funds via on-ramp / deposit address | `deposit/DepositFlowScreen.kt` · `…/internal/DepositViewModel` | **Flow** (`FlowRouteWithResult<DepositResult>`); USDC deposit info, Coinbase on-ramp |
| **transactions** | Activity / transaction history | `transactions/TransactionHistoryScreen.kt` · `…/internal/TransactionHistoryViewModel` | **VM-driven**; Paging-backed list from persistence ([05](../05-persistence.md)) |

## C. Currencies & tokens

| Feature | Purpose | Screen / ViewModel | Pattern & integration |
|---------|---------|--------------------|-----------------------|
| **balance** | Wallet balance across tokens | `balance/BalanceScreen.kt` · `…/internal/BalanceViewModel` | **VM-driven**; observes token balances + exchange rates |
| **tokens** | Token info (price/market cap in USDF), selection, and buy/sell swaps against USDF | `tokens/TokenInfoScreen.kt`, `tokens/SwapFlowScreen.kt`, `tokens/TokenSelectScreen.kt` | **Flow** for swap (`FlowRouteWithResult<SwapResult>`, `SwapPurpose.Buy`/`Sell`); screen-local for info/selection |
| **currency-creator** | Create a launchpad currency (USDF-backed), seeded by an initial USDF buy | `currencycreator/CurrencyCreatorFlowScreen.kt` · `…/internal/CurrencyCreatorViewModel` | **Flow**; name → icon → info → processing steps |

## D. Profile, settings & misc

| Feature | Purpose | Screen / ViewModel | Pattern & integration |
|---------|---------|--------------------|-----------------------|
| **myaccount** | Account screen + user profile | `myaccount/MyAccountScreen.kt`, `myaccount/UserProfileScreen.kt` · `…/internal/MyAccountScreenViewModel` | **VM-driven**; reactive feature-flag / staff binding, event-driven nav |
| **messenger** | In-app chat & chat-amount entry | `messenger/ChatFlowScreen.kt` · `…/internal/ChatViewModel` | **Flow** (`AppRoute.Messaging.Chat`); Paging-backed messages |
| **appsettings** | App preferences | `apps/flipcash/features/appsettings/…` | **VM-driven**; backed by `AppSettingsCoordinator` (`LocalAppSettings`) |
| **invite / shareapp** | Invite & share flows | `apps/flipcash/features/invite`, `…/shareapp` | Screen-local; use `InviteController` / `ShareSheetController` locals |
| **device-logs / lab / advanced** | Debug & internal tooling | `apps/flipcash/features/device-logs`, `…/lab`, `…/advanced` | Internal; surface trace logs and feature-flag overrides |

## Full module list

The 26 feature directories: `advanced`, `appsettings`, `appupdates`, `backupkey`,
`balance`, `bill-customization`, `cash`, `contact-verification`, `currency-creator`,
`deposit`, `device-logs`, `direct-send`, `discovery`, `invite`, `lab`, `login`,
`menu`, `messenger`, `myaccount`, `purchase`, `scanner`, `shareapp`, `tokens`,
`transactions`, `userflags`, `withdrawal`.

> Keep this catalog representative, not exhaustive — when you add a feature, add a
> row to the relevant category if it introduces a new user-facing capability or a
> notable pattern, and update the full list above.
