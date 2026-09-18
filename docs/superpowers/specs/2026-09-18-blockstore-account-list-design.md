# Block Store account list

Status: design, not started. Target: `code/cash`.

## The problem

iOS keeps a list of every account the user has logged into, and `AccountSelectionScreen`
renders it so they can switch between them. The list lives in the keychain under
`com.flipcash.account.list` (`Flipcash/Keychain/Secure.swift`), which survives an app
uninstall because iOS leaves keychain items behind. Reinstall the app and the accounts are
still there.

Android has no sandbox-local store with that property. Keystore keys are destroyed on
uninstall, and DataStore and EncryptedSharedPreferences go with app data. Today the account
list is a plaintext DataStore at `datastore/credentials.preferences_pb`
(`PassphraseCredentialManager`), keyed `{accountId}_entropy`, and it dies with the app.

The one durable store we have built is Google Password Manager: `storeCredential()` writes
each account's mnemonic as a password credential, and `selectCredential()` opens the GPM
picker. It has never been switched on — `FeatureFlag.CredentialManager` gates it, the
`SwitchAccount` entry behind it is staff-only, and no user has gone through it. It could not
have carried the list anyway: `GetPasswordOption` needs `allowedUserIds` up front and there is
no enumerate API, so it restores *an* account but cannot enumerate them.

## Why Block Store

Block Store (`com.google.android.gms:play-services-auth-blockstore`) stores bytes in Google
Play services' private data directory rather than the app sandbox, keyed to package name and
signing certificate. Uninstalling the app does not remove them. The data comes back on
same-device reinstall, on device-to-device transfer, and on cloud restore after a factory
reset.

Three limits shape the rest of this design:

- **16 entries, 4KB each.** Not a per-account key space to spend freely.
- **Persistence depends on the user's Backup setting.** Settings > Google > Backup. With it
  off, Block Store is local-only and an uninstall takes the list.
- **Cloud backup needs a screen lock.** `isEndToEndEncryptionAvailable()` requires API 29 and
  a PIN, pattern or password. We are minSdk 29, so the API floor is not a constraint, but the
  screen lock is. Cloud restore onto a non-Pixel target needs API 31.

The screen-lock gate is the same bargain iOS makes with `.afterFirstUnlock`, so the security
posture is comparable rather than worse.

## What gets stored

One entry, under one key, holding the whole list. Per-account keys would cap us at 16
accounts and introduce cross-entry consistency to manage; a single entry is one atomic read
and one atomic write, and leaves 15 entries for anything later.

Each record is fixed-width binary:

| Field | Bytes | Notes |
|---|---|---|
| `entropy` | 16 | The seed. Everything else derives from it. |
| `creationDate` | 8 | Epoch millis. |
| `lastSeen` | 8 | Epoch millis. Drives eviction; display sorts by `creationDate`. |
| `deletionDate` | 8 | Epoch millis, `0` for null. Soft delete, matching iOS. |

40 bytes per account, plus a 1-byte format version and a 2-byte count. The 4KB entry holds
about 100 accounts. Cap the list at 50 and evict the oldest `lastSeen` on overflow; that
writes 2003 bytes, leaving half the budget as headroom for a format change.

The owner public key and the display name are **not** stored. Both derive from the entropy
(`MnemonicManager.getKeyPair`, and iOS's `MnemonicPhrase.name` is first word + last word,
capitalised, joined by `...`). Deriving costs one SLIP-0010 + ed25519 pass per account at
list load, off the main thread, and the keypair is needed for the balance fetch anyway.

Soft delete rather than hard delete, because iOS does: `setDeleted(ownerPublicKey:deleted:)`
sets a `deletionDate`, `fetchActiveHistorical()` filters on it, and a later login to the same
account clears it. Keeping the record means a user who removes an account and logs back in
does not lose its creation date.

## Storage layer

`AccountStore` in `apps/flipcash/shared/authentication`, alongside the code it replaces:

```kotlin
interface AccountStore {
    suspend fun all(): List<AccountRecord>          // active, sorted by creationDate desc
    suspend fun upsert(entropy: String)             // inserts, or bumps lastSeen and undeletes
    suspend fun setDeleted(entropy: String, deleted: Boolean)
    suspend fun clear()
}
```

The Block Store implementation wraps `BlockstoreClient.retrieveBytes` / `storeBytes` /
`deleteBytes`. It gates `setShouldBackupToCloud(true)` on `isEndToEndEncryptionAvailable()`,
and leaves it unset otherwise — note that unsetting it deletes previously backed-up cloud
data on the next periodic sync, so the flag has to be recomputed on every write rather than
cached.

When Play services are unavailable, every method degrades to a no-op returning an empty list.
The app must still log in and work; it just loses the durable list.

Reads and writes go through a mutex. The whole entry is rewritten on each mutation, so two
concurrent writers would otherwise lose one of the two updates.

## The selection screen

`AccountSelectionScreen` in `features/login`, matching where iOS puts it and where
`SeedInputContent` already lives.

Per row, mirroring iOS's `AccountRow`:

- Check mark when the row is the account currently logged in, and the row is disabled.
- Derived name, `Firstword ... Lastword`.
- Live balance, right-aligned, or a "Not Found" badge when the backend does not know the
  account.
- Created date, relative.
- Truncated owner public key, middle ellipsis.

Below the list, "Enter a Different Access Key" falls through to `SeedInput`.

Long-press replaces iOS's swipe and context menu: Remove Account (with the same confirmation
dialog warning about the access key backup), Copy Access Key, Copy Vault Address.

### Balances

iOS fetches a balance per account concurrently in `fetchBalances()`, using
`client.fetchPrimaryAccounts(owner:)` and `client.fetchMints(mints:)`, and catches
`ErrorFetchBalance.notFound` to set the badge.

The Android equivalents exist:
`AccountController.getAccounts(accountOwner, requestingOwner, filter)` in
`services/opencode` (`GetTokenAccountInfos` underneath, same RPC as iOS), and
`CurrencyService.getMints(mintAddresses)`. Both take an `AccountCluster`, which we derive per
record from its entropy and pass as both `accountOwner` and `requestingOwner` — a
self-request, which is what iOS does by signing with the account's own owner keypair.

This runs while logged out, against N owners the session is not authenticated as. Each
request is signed by the owner it asks about, which is what makes it acceptable to the
backend — the same property iOS relies on.

Converting quarks to fiat goes through `VerifiedFiatCalculator.compute(amount, token,
balance, rate, trace)`, using the balance currency rate, matching iOS's
`ExchangedFiat.compute` with `supplyFromBonding` from the mint metadata.

Rows render immediately with the list data and fill in balances as they arrive. One account's
failure must not blank the others.

## Where it appears

**Login.** iOS's `OnboardingViewModel.loginAction` checks `fetchActiveHistorical().isEmpty`
and routes to `.accountSelection` or `.login`. Android's equivalent is the `login` callback
the onboarding flow hands to `LoginRouterScreenContent`, which today is unconditionally
`flowNavigator.navigateTo(OnboardingStep.SeedInput)`. It becomes the same branch, against a
`hasStoredAccounts` field added to `LoginViewModel.State` — the router screen already collects
that state, so nothing new is plumbed through.

Add `OnboardingStep.AccountSelection` alongside `SeedInput` and an `annotatedEntry` for it in
`OnboardingFlowScreen`. The KDoc flow diagram at the top of that file enumerates the
onboarding paths and has to gain the new step; it is the only map of this flow anyone reads.

Selecting a row dispatches the existing `LoginViewModel.Event.LogIn(entropyB64)`, which
already routes through `FacilitateLogin` to `authManager.login(entropyB64, isFromSelection =
true)` — the same path deep links take.

**In-app switching.** Add `AppRoute.Menu.AccountSelection` — a top-level route rather than an
`OnboardingStep`, since it is reached from the menu — and present the same composable.
Selection calls `authManager.logoutAndSwitchAccount(entropy)`, which sets
`pendingSwitchEntropy` for App.kt's auth guard to consume. That machinery already works; only
the account picker in front of it changes.

The staff-gated `SwitchAccount` entry in `AdvancedFeatureMenuItems` and its
`FeatureFlag.CredentialManager` gate are deleted, along with the
`Event.OnSwitchAccountsClicked` handler in `AdvancedFeaturesScreenViewModel`.

**Auto-login.** `AuthManager.init()` currently reads `credentialManager.lookup()`. That keeps
working off the local DataStore for the selected account. iOS additionally falls back to the
most recent historical account when `wasLoggedIn` is true, deliberately gated so the silent
login does not fire against an arbitrary entry and trip the OCP antispam guard
(`SessionAuthenticator.initializeState`). We are not adopting that fallback here — Android
has no `wasLoggedIn` equivalent, and adding one is a separate change with its own antispam
exposure.

## Removing Credential Manager

`PassphraseCredentialManager` currently mixes two concerns: it is the local account/session
store (selected account, entropy per account, onboarding and access-key flags) and it is the
GPM integration. Only the second goes away.

Deleted: `selectCredential()`, `getCredentialByEntropy()`, `storeCredential()`,
`credentialLookupCache`, `MnemonicPhrase.toCredentialId()`, the `androidx.credentials`
dependencies, `FeatureFlag.CredentialManager`, and the restore FAB in `SeedInputContent`.
`AuthManager.selectAccount()` goes with them.

Kept: the DataStore half, which is still how the app knows who is logged in and where
onboarding got to, and `AuthManager.presentCredentialStorage()`, which stays as the entry
point and only changes what it writes to. `AccountStore.upsert()` replaces `storeCredential()`
at all three of its call sites — once in `presentSaveOption()`, twice in `login()`.

### Migration

Because the flag was never on, no user has a credential in GPM and there is nothing to strand.
The removal is a straight deletion in one release rather than a staged retreat.

The only migration is local: on every launch, if Block Store reads empty and the local
DataStore holds an account, seed Block Store from it. That picks up everyone logged in at
upgrade time, recovers the corrupt-entry case below, and is idempotent because a non-empty
Block Store skips it.

## Backup rules

`allowBackup="true"` and `backup_rules.xml` currently exclude only the three feature-flag
datastores, so `credentials.preferences_pb` — plaintext seeds — is going into Auto Backup and
device transfer today. That is an accidental persistence path, and once Block Store is the
deliberate one, it should not be a second undocumented channel with different rules and no
screen-lock gate.

Add `datastore/credentials.preferences_pb` to the exclusions in both `backup_rules.xml` and
`data_extraction_rules.xml`.

This is worth doing regardless of the rest of this design.

## Failure modes

| Condition | Behaviour |
|---|---|
| Play services missing or out of date | `AccountStore` is a no-op, list is empty, login falls through to seed entry. |
| User has Backup services off | Block Store still works locally; the list survives app updates but not uninstall. No in-app signal — the setting is not readable. |
| No screen lock | Local-only, no cloud backup. Survives reinstall, not a new device. |
| Entry corrupt or version unknown | Treat as empty rather than crashing; the migration then repopulates it from the local DataStore. |
| More than 50 accounts | Evict the oldest `lastSeen`. |

## Testing

- `AccountStore` encode/decode round-trip, including the 50-account cap, eviction order,
  soft delete and undelete, and an unknown version byte.
- A fake `BlockstoreClient` for the store's behaviour, plus the no-op path when Play services
  are absent.
- Migration: empty Block Store plus a populated legacy DataStore produces the right records
  and is idempotent on second launch.
- `LoginViewModel.State.hasStoredAccounts` reflects the store, and the router's `login`
  callback branches on it: empty goes to `SeedInput`, non-empty to the account list.
- Manual, on a device, and this is the only check that proves the premise: log in to two
  accounts, uninstall, reinstall, confirm both are listed. Repeat with Backup services off to
  confirm the documented behaviour is the behaviour we get.
