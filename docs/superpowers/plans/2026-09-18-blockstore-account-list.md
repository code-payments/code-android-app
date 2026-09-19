# Block Store Account List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Android an account list that survives app uninstall, backed by Google Block Store, with an iOS-equivalent selection screen wired into both the login flow and an in-app switcher, replacing Google Credential Manager.

**Architecture:** A pure codec packs account records into one 4KB Block Store entry. A thin `BlockStoreBytes` seam wraps Play services so everything above it is unit-testable. `AccountStore` sits on top and is what the rest of the app talks to, replacing the Google Password Manager half of `PassphraseCredentialManager` while its DataStore half stays as the session store. The selection screen fetches per-account balances concurrently through the existing `TokenController.fetchTokenAccounts`, which already takes an arbitrary owner cluster.

**Tech Stack:** Kotlin, Hilt, Jetpack Compose, Navigation 3, `com.google.android.gms:play-services-auth-blockstore`, `kotlinx-coroutines-play-services`, JUnit 4 + Robolectric + MockK.

**Design doc:** `docs/superpowers/specs/2026-09-18-blockstore-account-list-design.md`

**Base branch:** `origin/code/cash`. Branch name: `feat/blockstore-account-list`.

---

## File Structure

**New — storage** (`apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/`)

| File | Responsibility |
|---|---|
| `AccountRecord.kt` | The stored record. Data only. |
| `AccountRecordCodec.kt` | Pure bytes to records and back, plus the cap and eviction rule. No Android. |
| `BlockStoreBytes.kt` | Interface: read, write and delete a single keyed blob. The seam that makes everything above testable. |
| `PlayBlockStoreBytes.kt` | The only file that touches `BlockstoreClient`. Swallows Play services failures. |
| `AccountStore.kt` | Interface the app depends on. |
| `BlockStoreAccountStore.kt` | Record-level semantics: upsert, soft delete, ordering, mutex. |
| `AccountStoreMigration.kt` | Seeds the store from the legacy DataStore. |
| `AccountStoreModule.kt` | Hilt bindings. |

**Modified**

| File | Change |
|---|---|
| `apps/flipcash/app/src/main/res/xml/backup_rules.xml` | Exclude the credentials DataStore. |
| `apps/flipcash/app/src/main/res/xml/data_extraction_rules.xml` | Same, both sections. |
| `gradle/libs.versions.toml` | Add the Block Store artifact. |
| `apps/flipcash/shared/authentication/build.gradle.kts` | Swap `androidx.credentials` for Block Store and coroutines-play-services. |
| `.../auth/internal/credentials/PassphraseCredentialManager.kt` | Delete the GPM half; call `AccountStore`. |
| `.../auth/AuthManager.kt` | Delete `selectAccount()`; expose the store. |
| `.../featureflags/FeatureFlag.kt` | Delete `CredentialManager`. |
| `.../login/internal/screens/SeedInputContent.kt` | Delete the restore FAB. |
| `.../advanced/internal/AdvancedFeatureMenuItems.kt` | Replace the staff `SwitchAccount` with a full menu item. |
| `.../advanced/internal/AdvancedFeaturesScreenViewModel.kt` | Delete the GPM switch handler. |
| `apps/flipcash/core/.../onboarding/OnboardingStep.kt` | Add `AccountSelection`. |
| `apps/flipcash/core/.../AppRoute.kt` | Add `Menu.AccountSelection`. |
| `.../login/router/LoginViewModel.kt` | Add `hasStoredAccounts` to state. |
| `.../login/OnboardingFlowScreen.kt` | Branch `login`, register the step, update the KDoc diagram. |
| `apps/flipcash/app/.../navigation/AppScreenContent.kt` | Register `Menu.AccountSelection`. |
| `ui/resources/src/main/res/values/strings-localized.xml` | New strings. |

**New — UI** (`apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/internal/accounts/`)

| File | Responsibility |
|---|---|
| `AccountSelectionViewModel.kt` | List plus concurrent balance fetch. |
| `AccountRow.kt` | One row. |
| `AccountSelectionContent.kt` | Screen scaffold and list. |
| `../AccountSelectionScreen.kt` | Public entry point for the in-app switcher. |

---

## Task 1: Stop leaking seeds into Google's backup

Independent of everything else and worth landing first. `credentials.preferences_pb` holds base64 seeds in cleartext and is currently included in Auto Backup and device transfer.

**Files:**
- Modify: `apps/flipcash/app/src/main/res/xml/backup_rules.xml`
- Modify: `apps/flipcash/app/src/main/res/xml/data_extraction_rules.xml`

- [ ] **Step 1: Read both files to see the existing exclusion style**

```bash
cat apps/flipcash/app/src/main/res/xml/backup_rules.xml apps/flipcash/app/src/main/res/xml/data_extraction_rules.xml
```

Expected: each excludes `datastore/beta-flags.preferences_pb`, `datastore/user-flag-overrides.preferences_pb` and `datastore/release-stage.preferences_pb`, with `domain="file"`.

- [ ] **Step 2: Add the exclusion to `backup_rules.xml`**

Inside `<full-backup-content>`, next to the existing three:

```xml
<exclude domain="file" path="datastore/credentials.preferences_pb" />
```

- [ ] **Step 3: Add the exclusion to both sections of `data_extraction_rules.xml`**

That file has a `<cloud-backup>` and a `<device-transfer>` section. Add the same line to **each**:

```xml
<exclude domain="file" path="datastore/credentials.preferences_pb" />
```

- [ ] **Step 4: Verify the app still assembles**

Run: `./gradlew :apps:flipcash:app:processDebugMainManifest`
Expected: `BUILD SUCCESSFUL`. A malformed XML rules file fails here.

- [ ] **Step 5: Commit**

```bash
git add apps/flipcash/app/src/main/res/xml/backup_rules.xml apps/flipcash/app/src/main/res/xml/data_extraction_rules.xml
git commit -m "fix(auth): keep the credentials datastore out of Google backup"
```

Commit body:

```
It holds base64 seeds in cleartext and the rules only excluded the three
feature-flag datastores, so it was going to cloud backup and device transfer.
```

---

## Task 2: `AccountRecord` and the codec

The whole list lives in one 4KB entry, so the encoding and the cap are the load-bearing part. Pure Kotlin, no Android, fully testable.

**Files:**
- Create: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/AccountRecord.kt`
- Create: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/AccountRecordCodec.kt`
- Test: `apps/flipcash/shared/authentication/src/test/kotlin/com/flipcash/app/auth/internal/accounts/AccountRecordCodecTest.kt`

- [ ] **Step 1: Write `AccountRecord`**

```kotlin
package com.flipcash.app.auth.internal.accounts

/**
 * One account in the durable list. Mirrors iOS's `AccountDescription`.
 *
 * Only the entropy is stored — the owner public key and the display name derive from it, which
 * halves the per-record footprint. See the design doc for the 4KB budget.
 */
data class AccountRecord(
    /** Base64-encoded 16-byte seed. */
    val entropy: String,
    val creationDate: Long,
    val lastSeen: Long,
    /** Soft delete. `null` means active, matching iOS's `fetchActiveHistorical`. */
    val deletionDate: Long? = null,
) {
    val isActive: Boolean get() = deletionDate == null
}
```

- [ ] **Step 2: Write the failing codec test**

```kotlin
package com.flipcash.app.auth.internal.accounts

import com.getcode.utils.encodeBase64
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountRecordCodecTest {

    private fun entropy(seed: Int): String = Random(seed).nextBytes(16).encodeBase64()

    private fun record(seed: Int, lastSeen: Long = 1_000L, deleted: Long? = null) =
        AccountRecord(
            entropy = entropy(seed),
            creationDate = 500L,
            lastSeen = lastSeen,
            deletionDate = deleted,
        )

    @Test
    fun `round trips a single record`() {
        val records = listOf(record(1))
        assertEquals(records, AccountRecordCodec.decode(AccountRecordCodec.encode(records)))
    }

    @Test
    fun `round trips a soft-deleted record`() {
        val records = listOf(record(1, deleted = 9_999L))
        assertEquals(records, AccountRecordCodec.decode(AccountRecordCodec.encode(records)))
    }

    @Test
    fun `round trips a full list and preserves order`() {
        val records = (1..AccountRecordCodec.MAX_ACCOUNTS).map { record(it, lastSeen = it.toLong()) }
        assertEquals(records, AccountRecordCodec.decode(AccountRecordCodec.encode(records)))
    }

    @Test
    fun `a full list fits well inside the block store entry`() {
        val records = (1..AccountRecordCodec.MAX_ACCOUNTS).map { record(it) }
        assertTrue(AccountRecordCodec.encode(records).size <= 2048)
    }

    @Test
    fun `encoding over the cap evicts the least recently seen`() {
        val records = (1..AccountRecordCodec.MAX_ACCOUNTS + 3).map { record(it, lastSeen = it.toLong()) }
        val decoded = AccountRecordCodec.decode(AccountRecordCodec.encode(records))

        assertEquals(AccountRecordCodec.MAX_ACCOUNTS, decoded.size)
        // lastSeen 1, 2 and 3 are the oldest, so they are the ones dropped.
        assertTrue(decoded.none { it.lastSeen <= 3L })
    }

    @Test
    fun `decodes empty bytes as an empty list`() {
        assertEquals(emptyList(), AccountRecordCodec.decode(ByteArray(0)))
    }

    @Test
    fun `decodes an unknown version as an empty list`() {
        val bytes = AccountRecordCodec.encode(listOf(record(1)))
        bytes[0] = 99
        assertEquals(emptyList(), AccountRecordCodec.decode(bytes))
    }

    @Test
    fun `decodes truncated bytes as an empty list`() {
        val bytes = AccountRecordCodec.encode(listOf(record(1), record(2)))
        assertEquals(emptyList(), AccountRecordCodec.decode(bytes.copyOf(bytes.size - 5)))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :apps:flipcash:shared:authentication:testDebugUnitTest --tests '*AccountRecordCodecTest*'`
Expected: FAIL — `Unresolved reference: AccountRecordCodec`.

- [ ] **Step 4: Write the codec**

```kotlin
package com.flipcash.app.auth.internal.accounts

import com.getcode.utils.decodeBase64
import com.getcode.utils.encodeBase64
import java.nio.ByteBuffer

/**
 * Packs the account list into the single Block Store entry.
 *
 * Layout: `[version:1][count:2][record × count]`, each record a fixed 40 bytes of
 * `[entropy:16][creationDate:8][lastSeen:8][deletionDate:8]`, with `0` standing in for a null
 * deletion date. That is 2003 bytes at the cap, roughly half of Block Store's 4KB per-entry
 * limit, which leaves room for a format change.
 *
 * Every decode failure returns an empty list rather than throwing: a corrupt entry must not
 * brick login, and the migration repopulates from the local DataStore.
 */
internal object AccountRecordCodec {

    const val VERSION: Byte = 1
    const val MAX_ACCOUNTS = 50

    private const val ENTROPY_BYTES = 16
    private const val RECORD_BYTES = ENTROPY_BYTES + 8 + 8 + 8
    private const val HEADER_BYTES = 1 + 2

    fun encode(records: List<AccountRecord>): ByteArray {
        val capped = cap(records)
        val buffer = ByteBuffer.allocate(HEADER_BYTES + capped.size * RECORD_BYTES)
        buffer.put(VERSION)
        buffer.putShort(capped.size.toShort())
        capped.forEach { record ->
            val entropy = record.entropy.decodeBase64()
            require(entropy.size == ENTROPY_BYTES) {
                "Entropy must be $ENTROPY_BYTES bytes, was ${entropy.size}"
            }
            buffer.put(entropy)
            buffer.putLong(record.creationDate)
            buffer.putLong(record.lastSeen)
            buffer.putLong(record.deletionDate ?: 0L)
        }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): List<AccountRecord> {
        if (bytes.isEmpty()) return emptyList()
        return runCatching {
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != VERSION) return emptyList()
            val count = buffer.short.toInt()
            if (count < 0 || count > MAX_ACCOUNTS) return emptyList()
            if (bytes.size != HEADER_BYTES + count * RECORD_BYTES) return emptyList()

            (0 until count).map {
                val entropy = ByteArray(ENTROPY_BYTES).also(buffer::get)
                AccountRecord(
                    entropy = entropy.encodeBase64(),
                    creationDate = buffer.long,
                    lastSeen = buffer.long,
                    deletionDate = buffer.long.takeIf { date -> date != 0L },
                )
            }
        }.getOrElse { emptyList() }
    }

    /** Keeps the [MAX_ACCOUNTS] most recently seen, preserving the caller's ordering. */
    fun cap(records: List<AccountRecord>): List<AccountRecord> {
        if (records.size <= MAX_ACCOUNTS) return records
        val keep = records.sortedByDescending { it.lastSeen }.take(MAX_ACCOUNTS).toSet()
        return records.filter { it in keep }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :apps:flipcash:shared:authentication:testDebugUnitTest --tests '*AccountRecordCodecTest*'`
Expected: PASS, 8 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts apps/flipcash/shared/authentication/src/test/kotlin/com/flipcash/app/auth/internal/accounts
git commit -m "feat(auth): add the account record codec for the block store entry"
```

---

## Task 3: The Play services seam

`BlockstoreClient` is final and awkward to fake, so one small interface keeps it out of every test above it.

**Files:**
- Create: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/BlockStoreBytes.kt`
- Create: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/PlayBlockStoreBytes.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `apps/flipcash/shared/authentication/build.gradle.kts`

- [ ] **Step 1: Add the dependency to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` alongside the other `play-services-*` entries:

```toml
play-services-blockstore = "16.4.0"
```

Under `[libraries]`, next to the other `play-services-*` entries:

```toml
play-services-blockstore = { module = "com.google.android.gms:play-services-auth-blockstore", version.ref = "play-services-blockstore" }
```

- [ ] **Step 2: Wire it into the module**

In `apps/flipcash/shared/authentication/build.gradle.kts`, replace these two lines:

```kotlin
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.auth)
```

with:

```kotlin
    implementation(libs.play.services.blockstore)
    implementation(libs.kotlinx.coroutines.play.services)
```

Leave `implementation(libs.androidx.datastore)` and everything else as it is.

- [ ] **Step 3: Verify the dependency resolves**

Run: `./gradlew :apps:flipcash:shared:authentication:dependencies --configuration debugCompileClasspath`
Expected: the output contains `com.google.android.gms:play-services-auth-blockstore:16.4.0`. If that version does not resolve, use the newest published `play-services-auth-blockstore` and keep the rest of this task unchanged.

The module will not compile until Task 6 removes the `androidx.credentials` imports from `PassphraseCredentialManager`. That is expected; Steps 4 and 5 below only add files.

- [ ] **Step 4: Write the seam interface**

```kotlin
package com.flipcash.app.auth.internal.accounts

/**
 * A single keyed blob in a store that outlives the app sandbox.
 *
 * Exists so that everything above it is testable without Play services, and so that the one file
 * touching `BlockstoreClient` stays small.
 */
internal interface BlockStoreBytes {
    /** The stored blob, or `null` when nothing is stored or the store is unavailable. */
    suspend fun read(): ByteArray?

    /** Returns false when the write did not happen. */
    suspend fun write(bytes: ByteArray): Boolean

    suspend fun delete()
}
```

- [ ] **Step 5: Write the Play services implementation**

```kotlin
package com.flipcash.app.auth.internal.accounts

import android.content.Context
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Block Store keeps this blob in Play services' own directory rather than the app sandbox, so it
 * survives uninstall when the user has Backup services on.
 *
 * Cloud backup is requested only when Play services reports end-to-end encryption is available,
 * which needs a screen lock. The flag is recomputed on every write rather than cached, because
 * leaving it unset deletes previously backed-up cloud data on the next sync.
 *
 * Every Play services failure is swallowed: no Play services means no durable list, not a broken
 * login.
 */
@Singleton
internal class PlayBlockStoreBytes @Inject constructor(
    @ApplicationContext context: Context,
) : BlockStoreBytes {

    private val client = Blockstore.getClient(context)

    override suspend fun read(): ByteArray? = runCatching {
        val request = RetrieveBytesRequest.Builder()
            .setKeys(listOf(KEY))
            .build()
        client.retrieveBytes(request).await()
            .blockstoreDataMap[KEY]
            ?.bytes
    }.getOrElse { error ->
        trace(tag = TAG, message = "Block Store read failed", error = error, type = TraceType.Error)
        null
    }

    override suspend fun write(bytes: ByteArray): Boolean = runCatching {
        val canEncrypt = runCatching { client.isEndToEndEncryptionAvailable.await() }
            .getOrDefault(false)

        val data = StoreBytesData.Builder()
            .setKey(KEY)
            .setBytes(bytes)
            .setShouldBackupToCloud(canEncrypt)
            .build()

        client.storeBytes(data).await()
        true
    }.getOrElse { error ->
        trace(tag = TAG, message = "Block Store write failed", error = error, type = TraceType.Error)
        false
    }

    override suspend fun delete() {
        runCatching {
            val request = DeleteBytesRequest.Builder()
                .setKeys(listOf(KEY))
                .build()
            client.deleteBytes(request).await()
        }.onFailure { error ->
            trace(tag = TAG, message = "Block Store delete failed", error = error, type = TraceType.Error)
        }
    }

    private companion object {
        const val TAG = "BlockStore"

        /**
         * One of Block Store's 16 entries. The whole list lives here so a mutation is one atomic
         * write with no cross-entry consistency to manage.
         */
        const val KEY = "com.flipcash.account.list"
    }
}
```

If `isEndToEndEncryptionAvailable` does not resolve as a property, it is a method on this version of the artifact — call `client.isEndToEndEncryptionAvailable().await()` instead. Everything else is unchanged. Confirm the `trace` signature against another caller in the module before relying on the named arguments above.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml apps/flipcash/shared/authentication/build.gradle.kts apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts
git commit -m "feat(auth): add the block store byte seam"
```

---

## Task 4: `AccountStore`

Record-level semantics on top of the codec and the seam.

**Files:**
- Create: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/AccountStore.kt`
- Create: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/BlockStoreAccountStore.kt`
- Test: `apps/flipcash/shared/authentication/src/test/kotlin/com/flipcash/app/auth/internal/accounts/BlockStoreAccountStoreTest.kt`

- [ ] **Step 1: Write the interface**

```kotlin
package com.flipcash.app.auth.internal.accounts

/**
 * The durable account list. Replaces the Google Password Manager half of
 * [com.flipcash.app.auth.internal.credentials.PassphraseCredentialManager], which could restore an
 * account but never enumerate them.
 */
interface AccountStore {
    /** Active accounts, newest created first. Empty when the store is unavailable. */
    suspend fun all(): List<AccountRecord>

    /** Every record, including soft-deleted ones. */
    suspend fun allIncludingDeleted(): List<AccountRecord>

    /** Inserts the account, or bumps its `lastSeen` and undeletes it if it is already known. */
    suspend fun upsert(entropy: String)

    suspend fun setDeleted(entropy: String, deleted: Boolean)

    /** Wipes the entry. Account removal uses [setDeleted]; this is for a full reset. */
    suspend fun clear()
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.flipcash.app.auth.internal.accounts

import com.getcode.utils.encodeBase64
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// `encodeBase64` is a KMP actual over `android.util.Base64`. The module sets
// `unitTests.isReturnDefaultValues = true`, so on the plain JVM runner it returns null rather
// than throwing. Robolectric supplies the real implementation, as it does in
// `libs/encryption/utils`' own `Base64ExtensionsTest`.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BlockStoreAccountStoreTest {

    private class FakeBlockStoreBytes(var stored: ByteArray? = null) : BlockStoreBytes {
        var available = true
        override suspend fun read(): ByteArray? = if (available) stored else null
        override suspend fun write(bytes: ByteArray): Boolean {
            if (!available) return false
            stored = bytes
            return true
        }
        override suspend fun delete() { stored = null }
    }

    private fun entropy(seed: Int): String = Random(seed).nextBytes(16).encodeBase64()

    private fun store(
        bytes: FakeBlockStoreBytes = FakeBlockStoreBytes(),
        clock: () -> Long = { 1_000L },
    ) = BlockStoreAccountStore(bytes, clock)

    @Test
    fun `starts empty`() = runTest {
        assertEquals(emptyList(), store().all())
    }

    @Test
    fun `upsert inserts an account`() = runTest {
        val store = store()
        store.upsert(entropy(1))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(entropy(1), all.first().entropy)
        assertEquals(1_000L, all.first().creationDate)
        assertEquals(1_000L, all.first().lastSeen)
        assertNull(all.first().deletionDate)
    }

    @Test
    fun `upsert of a known account bumps lastSeen and keeps creationDate`() = runTest {
        var now = 1_000L
        val bytes = FakeBlockStoreBytes()
        store(bytes) { now }.upsert(entropy(1))

        now = 5_000L
        val store = store(bytes) { now }
        store.upsert(entropy(1))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(1_000L, all.first().creationDate)
        assertEquals(5_000L, all.first().lastSeen)
    }

    @Test
    fun `setDeleted hides the account from all but keeps the record`() = runTest {
        val store = store()
        store.upsert(entropy(1))
        store.setDeleted(entropy(1), deleted = true)

        assertTrue(store.all().isEmpty())
        assertEquals(1, store.allIncludingDeleted().size)
    }

    @Test
    fun `upsert undeletes and preserves the original creationDate`() = runTest {
        var now = 1_000L
        val bytes = FakeBlockStoreBytes()
        store(bytes) { now }.let {
            it.upsert(entropy(1))
            it.setDeleted(entropy(1), deleted = true)
        }

        now = 9_000L
        val store = store(bytes) { now }
        store.upsert(entropy(1))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(1_000L, all.first().creationDate)
        assertNull(all.first().deletionDate)
    }

    @Test
    fun `all returns newest created first`() = runTest {
        var now = 1_000L
        val bytes = FakeBlockStoreBytes()
        store(bytes) { now }.upsert(entropy(1))
        now = 2_000L
        store(bytes) { now }.upsert(entropy(2))

        assertEquals(listOf(entropy(2), entropy(1)), store(bytes).all().map { it.entropy })
    }

    @Test
    fun `clear empties the store`() = runTest {
        val bytes = FakeBlockStoreBytes()
        val store = store(bytes)
        store.upsert(entropy(1))
        store.clear()

        assertTrue(store.all().isEmpty())
        assertNull(bytes.stored)
    }

    @Test
    fun `degrades to empty when the store is unavailable`() = runTest {
        val bytes = FakeBlockStoreBytes().apply { available = false }
        val store = store(bytes)

        store.upsert(entropy(1))

        assertEquals(emptyList(), store.all())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :apps:flipcash:shared:authentication:testDebugUnitTest --tests '*BlockStoreAccountStoreTest*'`
Expected: FAIL — `Unresolved reference: BlockStoreAccountStore`.

- [ ] **Step 4: Write the implementation**

```kotlin
package com.flipcash.app.auth.internal.accounts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The whole entry is rewritten on every mutation, so concurrent writers would otherwise lose an
 * update. The mutex is the only thing standing between two coroutines and a clobbered list.
 */
@Singleton
internal class BlockStoreAccountStore @Inject constructor(
    private val bytes: BlockStoreBytes,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountStore {

    private val mutex = Mutex()

    override suspend fun all(): List<AccountRecord> =
        allIncludingDeleted().filter { it.isActive }

    override suspend fun allIncludingDeleted(): List<AccountRecord> = mutex.withLock { read() }

    override suspend fun upsert(entropy: String) = mutate { records ->
        val now = clock()
        val existing = records.firstOrNull { it.entropy == entropy }
        if (existing == null) {
            records + AccountRecord(entropy = entropy, creationDate = now, lastSeen = now)
        } else {
            records.map { record ->
                if (record.entropy == entropy) {
                    record.copy(lastSeen = now, deletionDate = null)
                } else {
                    record
                }
            }
        }
    }

    override suspend fun setDeleted(entropy: String, deleted: Boolean) = mutate { records ->
        records.map { record ->
            if (record.entropy == entropy) {
                record.copy(deletionDate = if (deleted) clock() else null)
            } else {
                record
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock { bytes.delete() }
    }

    private suspend fun mutate(block: (List<AccountRecord>) -> List<AccountRecord>) {
        mutex.withLock {
            val updated = block(read())
            bytes.write(AccountRecordCodec.encode(updated))
        }
    }

    /** Newest created first, matching how the selection screen lists them. */
    private suspend fun read(): List<AccountRecord> =
        AccountRecordCodec.decode(bytes.read() ?: ByteArray(0))
            .sortedByDescending { it.creationDate }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :apps:flipcash:shared:authentication:testDebugUnitTest --tests '*BlockStoreAccountStoreTest*'`
Expected: PASS, 8 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts apps/flipcash/shared/authentication/src/test/kotlin/com/flipcash/app/auth/internal/accounts
git commit -m "feat(auth): add the block store backed account store"
```

---

## Task 5: Hilt wiring

**Files:**
- Create: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/AccountStoreModule.kt`

- [ ] **Step 1: Write the module**

```kotlin
package com.flipcash.app.auth.internal.accounts

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccountStoreModule {

    @Binds
    @Singleton
    abstract fun bindBlockStoreBytes(impl: PlayBlockStoreBytes): BlockStoreBytes

    companion object {
        @Provides
        @Singleton
        fun provideAccountStore(bytes: BlockStoreBytes): AccountStore =
            BlockStoreAccountStore(bytes)
    }
}
```

`BlockStoreAccountStore`'s second constructor parameter defaults to `System::currentTimeMillis`, so `@Provides` rather than `@Binds` keeps that default without Dagger needing to inject a clock.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :apps:flipcash:shared:authentication:compileDebugKotlin`
Expected: compiles, or fails only on the `androidx.credentials` imports in `PassphraseCredentialManager`, which Task 6 removes.

- [ ] **Step 3: Commit**

```bash
git add apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/AccountStoreModule.kt
git commit -m "feat(auth): wire the account store through Hilt"
```

---

## Task 6: Replace Credential Manager inside `PassphraseCredentialManager`

That class does two jobs: it is the local session store (selected account, per-account entropy, onboarding flags) and it is the GPM integration. Only the second goes. Every `storeCredential()` call becomes an `AccountStore.upsert()`, and the migration lands here too.

**Files:**
- Create: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/accounts/AccountStoreMigration.kt`
- Modify: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/credentials/PassphraseCredentialManager.kt`
- Test: `apps/flipcash/shared/authentication/src/test/kotlin/com/flipcash/app/auth/internal/accounts/AccountStoreMigrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.flipcash.app.auth.internal.accounts

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountStoreMigrationTest {

    /** Records what the production code asks of the store, without Block Store or Play services. */
    private class RecordingAccountStore : AccountStore {
        val records = mutableListOf<AccountRecord>()
        override suspend fun all(): List<AccountRecord> = records.filter { it.isActive }
        override suspend fun allIncludingDeleted(): List<AccountRecord> = records
        override suspend fun upsert(entropy: String) {
            if (records.none { it.entropy == entropy }) {
                records += AccountRecord(entropy, creationDate = 1L, lastSeen = 1L)
            }
        }
        override suspend fun setDeleted(entropy: String, deleted: Boolean) {
            records.replaceAll { record ->
                if (record.entropy == entropy) record.copy(deletionDate = if (deleted) 2L else null)
                else record
            }
        }
        override suspend fun clear() = records.clear()
    }

    @Test
    fun `seeds an empty store from the legacy entropy`() = runTest {
        val store = RecordingAccountStore()

        AccountStoreMigration.run(store, legacyEntropy = { listOf("legacy-entropy") })

        assertEquals(listOf("legacy-entropy"), store.all().map { it.entropy })
    }

    @Test
    fun `is a no-op when the store already has accounts`() = runTest {
        val store = RecordingAccountStore()
        store.upsert("existing")

        AccountStoreMigration.run(store, legacyEntropy = { listOf("legacy-entropy") })

        assertEquals(listOf("existing"), store.all().map { it.entropy })
    }

    @Test
    fun `is a no-op when there is no legacy entropy`() = runTest {
        val store = RecordingAccountStore()

        AccountStoreMigration.run(store, legacyEntropy = { emptyList() })

        assertTrue(store.all().isEmpty())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :apps:flipcash:shared:authentication:testDebugUnitTest --tests '*AccountStoreMigrationTest*'`
Expected: FAIL — `Unresolved reference: AccountStoreMigration`.

- [ ] **Step 3: Write the migration**

```kotlin
package com.flipcash.app.auth.internal.accounts

/**
 * Seeds Block Store from the pre-existing local DataStore.
 *
 * A condition rather than a one-shot upgrade hook: it runs on every launch and does nothing once
 * the store is non-empty. Written that way it also recovers a corrupt or unreadable entry, which
 * decodes as empty.
 */
internal object AccountStoreMigration {
    suspend fun run(store: AccountStore, legacyEntropy: suspend () -> List<String>) {
        if (store.allIncludingDeleted().isNotEmpty()) return
        legacyEntropy().forEach { store.upsert(it) }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :apps:flipcash:shared:authentication:testDebugUnitTest --tests '*AccountStoreMigrationTest*'`
Expected: PASS, 3 tests.

- [ ] **Step 5: Delete the Credential Manager code from `PassphraseCredentialManager`**

Delete these members entirely:
- the `CredentialManager.create(context)` field
- `private val credentialLookupCache` (line ~66)
- `suspend fun selectCredential(): Result<MnemonicPhrase>` (line ~312)
- `private suspend fun getCredentialByEntropy(...)` (line ~386)
- `private suspend fun storeCredential(...)` (line ~416)
- `private fun MnemonicPhrase.toCredentialId(): String` (line ~466)

Delete these imports:

```kotlin
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
```

Add:

```kotlin
import com.flipcash.app.auth.internal.accounts.AccountStore
import com.flipcash.app.auth.internal.accounts.AccountStoreMigration
```

- [ ] **Step 6: Inject `AccountStore` and replace the three `storeCredential` call sites**

Add the parameter to the constructor, after `mnemonicManager`:

```kotlin
    private val accountStore: AccountStore,
```

At line ~166 in `presentSaveOption()`, replace the `storeCredential(entropy, accountId)` call and its comment with:

```kotlin
        accountStore.upsert(entropy)
```

At lines ~271 and ~293 inside `login()`, replace each `storeCredential(entropy, ...)` call with the same line:

```kotlin
        accountStore.upsert(entropy)
```

At line ~252 the lookup reads `credentialLookupCache[entropy] ?: getCredentialByEntropy(entropy, userId)`, which no longer has a GPM source. The DataStore is now the only source, so delete that credential round-trip and use the entropy the surrounding code already holds. Read the enclosing function before editing — it must keep returning the same type.

- [ ] **Step 7: Run the migration on init**

`lookup()` is called by `AuthManager.init()` and is the natural place. At the top of `lookup()`, before anything else:

```kotlin
        AccountStoreMigration.run(accountStore) {
            val selected = storage.data.map { it[selectedAccountIdKey] }.firstOrNull()
                ?: return@run emptyList()
            listOfNotNull(storage.data.map { it[entropyKey(selected)] }.firstOrNull())
        }
```

Match the actual DataStore key names and accessor style used elsewhere in the file — the names above follow the existing pattern but are not verbatim.

- [ ] **Step 8: Compile the module**

Run: `./gradlew :apps:flipcash:shared:authentication:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Any remaining `androidx.credentials` reference is a missed deletion.

- [ ] **Step 9: Run the module's full test suite**

Run: `./gradlew :apps:flipcash:shared:authentication:testDebugUnitTest`
Expected: PASS. `AuthManagerTest` mocks `PassphraseCredentialManager` with `mockk(relaxed = true)`, so the new constructor parameter does not break it.

- [ ] **Step 10: Commit**

```bash
git add apps/flipcash/shared/authentication
git commit -m "feat(auth): store the account list in block store instead of Google Password Manager"
```

Commit body:

```
Google Password Manager could restore an account but never enumerate them, so
it could not back a list. It also never shipped -- the flag gating it was never
switched on, so there is nothing to migrate out of it. The only migration is
local: seed the store from the existing DataStore entropy.
```

---

## Task 7: Delete the rest of Credential Manager

**Files:**
- Modify: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/AuthManager.kt:312-314`
- Modify: `apps/flipcash/shared/featureflags/src/main/kotlin/com/flipcash/app/featureflags/FeatureFlag.kt:37,133,146`
- Modify: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/internal/screens/SeedInputContent.kt`
- Modify: `apps/flipcash/features/advanced/src/main/kotlin/com/flipcash/app/advanced/internal/AdvancedFeatureMenuItems.kt:77-90`
- Modify: `apps/flipcash/features/advanced/src/main/kotlin/com/flipcash/app/advanced/internal/AdvancedFeaturesScreenViewModel.kt`
- Modify: `apps/flipcash/shared/authentication/src/main/kotlin/com/flipcash/app/auth/internal/credentials/PassphraseCredentialManager.kt:343-345`
- Modify: `apps/flipcash/core/src/main/res/values/strings.xml:81-82`

- [ ] **Step 1: Delete `AuthManager.selectAccount()` and expose the store**

Remove these three lines, and the `MnemonicPhrase` import if nothing else in the file uses it:

```kotlin
    suspend fun selectAccount(): Result<MnemonicPhrase> {
        return credentialManager.selectCredential()
    }
```

Add `private val accountStore: AccountStore,` to the constructor, import `com.flipcash.app.auth.internal.accounts.AccountStore`, and expose it for the selection screen:

```kotlin
    val accounts: AccountStore get() = accountStore
```

- [ ] **Step 2: Delete the feature flag**

In `FeatureFlag.kt`, delete the `CredentialManager` declaration (line ~37) and its two `when` branches — the display name at line ~133 and the description at line ~146.

- [ ] **Step 3: Delete the restore FAB from `SeedInputContent`**

Remove the whole `floatingActionButton = { ... }` argument to `CodeScaffold` (lines 100-135), along with:
- the `featureFlags` and `restoreEnabled` locals (lines 88-89)
- the `onRestore` parameter from both `SeedInputContent` overloads and the `onRestore = { viewModel.restoreAccount() }` argument at line 73
- the now-unused imports: `FloatingActionButton`, `Icons`, `SettingsBackupRestore`, `CircleShape`, `ColorFilter`, `FeatureFlag`, `LocalFeatureFlags`, and `Row` and `Alignment` if nothing else in the file uses them — the bottom `Row` at line 167 does use both, so keep those two.

Then delete `restoreAccount()` from `SeedInputViewModel`, along with its now-unused
`SelectCredentialError` and `BottomBarManager` imports if nothing else in the file uses them.

That was the only caller of `SelectCredentialError` and of the two error strings, so they go
with it. In `PassphraseCredentialManager.kt`, delete lines 343-345:

```kotlin
sealed class SelectCredentialError : Exception() {
    class UserCancelled : SelectCredentialError()
}
```

In `apps/flipcash/core/src/main/res/values/strings.xml`, delete lines 81-82:

```xml
    <string name="error_title_selectCredential">Something went wrong</string>
    <string name="error_description_selectCredential">Failed to restore selected account. Please try again</string>
```

- [ ] **Step 4: Delete the staff menu item and its handler**

In `AdvancedFeatureMenuItems.kt`, delete the `SwitchAccount` object and its KDoc (lines 77-90), and the `FeatureFlag` and `StaffMenuItem` imports if nothing else in the file uses them. Task 11 adds a replacement `SwitchAccount` back as a `FullMenuItem`.

In `AdvancedFeaturesScreenViewModel.kt`:
- remove `add(SwitchAccount)` from `FullMenuList` (line ~34)
- delete the `Event.OnSwitchAccountsClicked` handler (lines 88-103) and both event declarations, `OnSwitchAccountsClicked` and `OnSwitchAccountTo` (lines ~67-68)
- remove those two from the `when` at lines ~199-200
- drop the now-unused `authManager` and `mnemonicManager` constructor parameters if nothing else in the class uses them — check first
- delete the matching `OnSwitchAccountTo` collector in `AdvancedFeaturesScreen.kt` (lines ~66-71)

- [ ] **Step 5: Drop the dangling dependency declarations**

`apps/flipcash/core` declares both credentials artifacts but no source file in that module
imports them, so they go with the rest. In `apps/flipcash/core/build.gradle.kts`, delete lines
24-25:

```kotlin
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.auth)
```

Task 3 already removed the same two lines from the authentication module, which leaves the
catalog entries unreferenced. In `gradle/libs.versions.toml`, delete the `androidx-credentials`
entry under `[versions]` and both `androidx-credentials` and `androidx-credentials-play-auth`
under `[libraries]`.

- [ ] **Step 6: Confirm nothing references Credential Manager**

Run:

```bash
grep -rnE "androidx[.-]credentials|(^|[^A-Za-z])CredentialManager|selectCredential|selectAccount\(\)" --include=*.kt --include=*.toml --include=*.kts apps services libs gradle
```

Expected: no output. Any hit is a missed deletion.

`PassphraseCredentialManager` survives this task and contains `CredentialManager` as a
substring, which is what the `(^|[^A-Za-z])` guard excludes. A plain `CredentialManager`
pattern reports its four remaining references as failures.

- [ ] **Step 7: Build the app**

Run: `./gradlew :apps:flipcash:app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore(auth): remove Google Credential Manager"
```

Commit body:

```
Never enabled -- the flag gating it stayed off and the only entry point was
staff-only, so there is no user state to preserve.
```

---

## Task 8: The selection view model

Mirrors iOS's `AccountSelectionScreen.fetchBalances()`: a concurrent per-account fetch, each signed by that account's own owner key, with a "not found" state when the backend does not know the account.

**Files:**
- Create: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/internal/accounts/AccountSelectionViewModel.kt`
- Modify: `apps/flipcash/features/login/build.gradle.kts`
- Test: `apps/flipcash/features/login/src/test/kotlin/com/flipcash/app/login/internal/accounts/AccountSelectionViewModelTest.kt`

- [ ] **Step 1: Add the module dependencies**

Check what the login module already has:

```bash
grep -n "shared:authentication\|services:opencode" apps/flipcash/features/login/build.gradle.kts
```

Add whichever of these is missing to `apps/flipcash/features/login/build.gradle.kts`:

```kotlin
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":services:opencode"))
```

`AccountStore` and `AccountRecord` live under `com.flipcash.app.auth.internal.accounts`. If Kotlin rejects the import, move `AccountStore.kt` and `AccountRecord.kt` up to `com.flipcash.app.auth.accounts` — they are the module's public surface and the `internal` package was the wrong home for them. Keep the codec, the seam, the migration and the implementation where they are, and update their imports.

- [ ] **Step 2: Write the failing test**

```kotlin
package com.flipcash.app.login.internal.accounts

import com.getcode.crypt.MnemonicPhrase
import org.junit.Test
import kotlin.test.assertEquals

class AccountSelectionViewModelTest {

    private fun phrase(vararg words: String) = MnemonicPhrase(words.toList())

    @Test
    fun `derives the display name from the first and last word`() {
        val name = AccountSelectionViewModel.displayName(
            phrase("apple", "banana", "cherry", "date", "elder")
        )
        assertEquals("Apple ... Elder", name)
    }

    @Test
    fun `capitalises words that are already capitalised`() {
        val name = AccountSelectionViewModel.displayName(phrase("Apple", "Elder"))
        assertEquals("Apple ... Elder", name)
    }

    @Test
    fun `truncates an owner address in the middle`() {
        val truncated = AccountSelectionViewModel.truncateAddress("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        assertEquals("ABCD...WXYZ", truncated)
    }

    @Test
    fun `leaves a short address alone`() {
        assertEquals("ABCD", AccountSelectionViewModel.truncateAddress("ABCD"))
    }
}
```

`MnemonicPhrase`'s constructor takes a list of words — confirm the parameter shape with `grep -rn "class MnemonicPhrase" libs/crypto` before writing, and adjust the `phrase` helper if it differs.

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :apps:flipcash:features:login:testDebugUnitTest --tests '*AccountSelectionViewModelTest*'`
Expected: FAIL — `Unresolved reference: AccountSelectionViewModel`.

- [ ] **Step 4: Write the view model**

```kotlin
package com.flipcash.app.login.internal.accounts

import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.auth.internal.accounts.AccountRecord
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.sum
import com.getcode.opencode.model.financial.usdf
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountSelectionViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val mnemonicManager: MnemonicManager,
    private val tokenController: TokenController,
    dispatchers: DispatcherProvider,
) : BaseViewModel<AccountSelectionViewModel.State, AccountSelectionViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    /**
     * One row. [balance] is null while the fetch is in flight; [notFound] is the terminal state
     * for an account the backend does not know, matching iOS's "Not Found" badge.
     */
    data class AccountUiModel(
        val entropy: String,
        val name: String,
        val ownerAddress: String,
        val creationDate: Long,
        val balance: Fiat? = null,
        val notFound: Boolean = false,
    )

    data class State(
        val accounts: List<AccountUiModel> = emptyList(),
        val loading: Boolean = true,
        val currentEntropy: String? = null,
    )

    sealed interface Event {
        data class OnAccountsLoaded(val accounts: List<AccountUiModel>) : Event
        data class OnBalanceResolved(val entropy: String, val balance: Fiat) : Event
        data class OnBalanceNotFound(val entropy: String) : Event
        data class OnAccountSelected(val entropy: String) : Event
        data class OnAccountRemoved(val entropy: String) : Event
    }

    init {
        viewModelScope.launch { load() }

        eventFlow
            .filterIsInstance<Event.OnAccountRemoved>()
            .onEach { event ->
                authManager.accounts.setDeleted(event.entropy, deleted = true)
                load()
            }
            .launchIn(viewModelScope)
    }

    private suspend fun load() {
        val records = authManager.accounts.all()
        dispatchEvent(Event.OnAccountsLoaded(records.map { it.toUiModel() }))
        fetchBalances(records)
    }

    private fun AccountRecord.toUiModel(): AccountUiModel {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropy)
        return AccountUiModel(
            entropy = entropy,
            name = displayName(mnemonic),
            ownerAddress = truncateAddress(clusterFor(entropy).authorityPublicKey.base58()),
            creationDate = creationDate,
        )
    }

    private fun clusterFor(entropy: String): AccountCluster {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropy)
        val authority = DerivedKey.derive(DerivePath.primary, mnemonic)
        return AccountCluster.newInstance(authority = authority, token = Token.usdf)
    }

    /**
     * Each account is fetched with its own owner key as both the account owner and the requesting
     * owner — a self-signed request, which is what makes this work while logged out. Rows fill in
     * as results land; one account failing must not blank the others.
     */
    private fun fetchBalances(records: List<AccountRecord>) {
        viewModelScope.launch {
            records.map { record ->
                async {
                    val cluster = clusterFor(record.entropy)
                    tokenController.fetchTokenAccounts(cluster, tokenController)
                        .onSuccess { tokens ->
                            if (tokens.isEmpty()) {
                                dispatchEvent(Event.OnBalanceNotFound(record.entropy))
                            } else {
                                dispatchEvent(
                                    Event.OnBalanceResolved(
                                        entropy = record.entropy,
                                        balance = tokens.map { it.balance }.sum(),
                                    )
                                )
                            }
                        }
                        .onFailure { dispatchEvent(Event.OnBalanceNotFound(record.entropy)) }
                }
            }.awaitAll()
        }
    }

    companion object {
        /** iOS's `MnemonicPhrase.name`: first word, ellipsis, last word, both capitalised. */
        fun displayName(mnemonic: MnemonicPhrase): String =
            listOf(mnemonic.words.first(), mnemonic.words.last())
                .joinToString(" ... ") { word ->
                    word.lowercase().replaceFirstChar { it.titlecase() }
                }

        fun truncateAddress(address: String): String =
            if (address.length <= 8) address
            else "${address.take(4)}...${address.takeLast(4)}"

        private val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnAccountsLoaded -> { state ->
                    state.copy(accounts = event.accounts, loading = false)
                }

                is Event.OnBalanceResolved -> { state ->
                    state.copy(
                        accounts = state.accounts.map {
                            if (it.entropy == event.entropy) it.copy(balance = event.balance) else it
                        }
                    )
                }

                is Event.OnBalanceNotFound -> { state ->
                    state.copy(
                        accounts = state.accounts.map {
                            if (it.entropy == event.entropy) it.copy(notFound = true) else it
                        }
                    )
                }

                is Event.OnAccountSelected,
                is Event.OnAccountRemoved -> { state -> state }
            }
        }
    }
}
```

`tokens.map { it.balance }.sum()` uses the same `Iterable<Fiat>.sum()` that `SelectTokenViewModel.totalBalance` uses — summing unrounded per-token values so the total rounds once, matching iOS. Confirm `AccountCluster`'s authority-public-key property name against `UserManager.kt:133-136` and `AccountCluster` itself; the derivation there is the canonical one.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :apps:flipcash:features:login:testDebugUnitTest --tests '*AccountSelectionViewModelTest*'`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/flipcash/features/login
git commit -m "feat(login): add the account selection view model"
```

---

## Task 9: The selection screen

**Files:**
- Create: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/internal/accounts/AccountRow.kt`
- Create: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/internal/accounts/AccountSelectionContent.kt`
- Modify: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/internal/accounts/AccountSelectionViewModel.kt`
- Modify: `ui/resources/src/main/res/values/strings-localized.xml`

- [ ] **Step 1: Add the strings**

In `ui/resources/src/main/res/values/strings-localized.xml`, alongside the existing `title_*` and `action_*` entries:

```xml
<string name="title_selectAccount">Select Account</string>
<string name="action_enterDifferentAccessKey">Enter a Different Access Key</string>
<string name="subtitle_accountNotFound">Not Found</string>
<string name="subtitle_accountCreated">Created %1$s</string>
<string name="action_removeAccount">Remove Account</string>
<string name="prompt_title_removeAccount">Remove Account?</string>
<string name="prompt_description_removeAccount">Make sure you have a backup of your Access Key before removing this account.</string>
```

`title_switchAccounts` already exists in this file; reuse it for the in-app switcher rather than adding another.

- [ ] **Step 2: Move the removal confirmation into the view model**

The confirmation dialog needs `ResourceHelper`, which composables in this codebase do not carry — `AdvancedFeaturesScreenViewModel` shows the established shape. Add to `AccountSelectionViewModel`'s constructor:

```kotlin
    private val resources: ResourceHelper,
```

with `import com.getcode.util.resources.ResourceHelper`, and add to `init`, alongside the other collectors:

```kotlin
        eventFlow
            .filterIsInstance<Event.OnRemoveRequested>()
            .onEach { event ->
                // Removal is soft -- the record keeps its creation date, so logging back into the
                // same account restores it rather than starting over. The warning is iOS's.
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_removeAccount),
                        subtitle = resources.getString(R.string.prompt_description_removeAccount),
                        positiveText = resources.getString(R.string.action_removeAccount),
                        negativeText = resources.getString(R.string.action_cancel),
                        onPositive = { dispatchEvent(Event.OnAccountRemoved(event.entropy)) },
                    )
                )
            }
            .launchIn(viewModelScope)
```

Add the event:

```kotlin
        data class OnRemoveRequested(val entropy: String) : Event
```

and its no-op branch next to `OnAccountSelected` in the `when`.

Copy the exact `BottomBarMessage` shape and the `R` import from `AdvancedFeaturesScreenViewModel` — the field names above follow that pattern but must match the real constructor, and the strings resolve through whichever `R` that file uses.

- [ ] **Step 3: Write the row**

```kotlin
package com.flipcash.app.login.internal.accounts

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.features.login.R
import com.getcode.theme.CodeTheme

@Composable
internal fun AccountRow(
    account: AccountSelectionViewModel.AccountUiModel,
    isCurrent: Boolean,
    relativeCreationDate: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !isCurrent,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Icon(
            modifier = Modifier.size(CodeTheme.dimens.grid.x4),
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = if (isCurrent) CodeTheme.colors.textMain else Color.Transparent,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Text(
                text = account.name,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.subtitle_accountCreated, relativeCreationDate),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
            Text(
                text = account.ownerAddress,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        when {
            account.notFound -> Text(
                text = stringResource(R.string.subtitle_accountNotFound),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )

            account.balance != null -> Text(
                text = account.balance.formatted(),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}
```

`combinedClickable` is experimental — add `@OptIn(ExperimentalFoundationApi::class)` if the compiler asks for it. Check the `R` import other files in this module use (`SeedInputContent.kt` uses `com.flipcash.features.login.R`) and whether the new strings resolve through it.

- [ ] **Step 4: Write the screen content**

```kotlin
package com.flipcash.app.login.internal.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.features.login.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun AccountSelectionContent(
    state: AccountSelectionViewModel.State,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEnterAccessKey: () -> Unit,
    modifier: Modifier = Modifier,
    /** The in-app switcher has no onboarding flow to fall through to, so it hides the footer. */
    showEnterAccessKey: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CodeTheme.dimens.inset),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CodeTheme.dimens.grid.x4),
            text = stringResource(R.string.title_selectAccount),
            style = CodeTheme.typography.displaySmall,
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.accounts, key = { it.entropy }) { account ->
                AccountRow(
                    account = account,
                    isCurrent = account.entropy == state.currentEntropy,
                    relativeCreationDate = formatRelative(account.creationDate),
                    onClick = { onSelect(account.entropy) },
                    onLongClick = { onRemove(account.entropy) },
                )
            }
        }

        if (showEnterAccessKey) {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CodeTheme.dimens.grid.x4),
                onClick = onEnterAccessKey,
                text = stringResource(R.string.action_enterDifferentAccessKey),
                buttonState = ButtonState.Subtle,
            )
        }
    }
}

private fun formatRelative(epochMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(epochMillis).toString()
```

Check `CodeTheme.typography` for the right display style name and `ButtonState` for the right subtle variant before compiling — `displaySmall` and `Subtle` follow the codebase's naming but confirm them. If the repo already has a relative-date helper, prefer it over the local `formatRelative`: `grep -rn "getRelativeTimeSpanString" --include=*.kt libs ui apps`.

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :apps:flipcash:features:login:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add apps/flipcash/features/login ui/resources
git commit -m "feat(login): add the account selection screen"
```

---

## Task 10: Route it into the login flow

**Files:**
- Modify: `apps/flipcash/core/src/main/kotlin/com/flipcash/app/core/onboarding/OnboardingStep.kt`
- Modify: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/router/LoginViewModel.kt`
- Modify: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/OnboardingFlowScreen.kt`
- Test: `apps/flipcash/features/login/src/test/kotlin/com/flipcash/app/login/router/LoginViewModelAccountsTest.kt`

- [ ] **Step 1: Add the step**

In `OnboardingStep.kt`, directly after `SeedInput`:

```kotlin
    @Parcelize
    @Serializable
    data object AccountSelection : OnboardingStep
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.flipcash.app.login.router

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginViewModelAccountsTest {

    @Test
    fun `defaults to no stored accounts so the router falls through to seed entry`() {
        assertFalse(LoginViewModel.State().hasStoredAccounts)
    }

    @Test
    fun `records that the store has accounts`() {
        val state = LoginViewModel.updateStateForEvent(
            LoginViewModel.Event.OnStoredAccountsChanged(hasAccounts = true)
        )(LoginViewModel.State())

        assertTrue(state.hasStoredAccounts)
    }
}
```

`updateStateForEvent` lives in an `internal companion object` and the test is in the same module, so it is visible.

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :apps:flipcash:features:login:testDebugUnitTest --tests '*LoginViewModelAccountsTest*'`
Expected: FAIL — `Unresolved reference: hasStoredAccounts`.

- [ ] **Step 4: Add the state and the event**

In `LoginViewModel.State` (line 42), add a field:

```kotlin
        val hasStoredAccounts: Boolean = false,
```

In `Event` (line 50), add:

```kotlin
        data class OnStoredAccountsChanged(val hasAccounts: Boolean) : Event
```

In the `when` inside `updateStateForEvent` (line 171), add:

```kotlin
                is Event.OnStoredAccountsChanged -> { state ->
                    state.copy(hasStoredAccounts = event.hasAccounts)
                }
```

At the end of the `init` block (after line 165), add:

```kotlin
        viewModelScope.launch {
            dispatchEvent(
                Event.OnStoredAccountsChanged(authManager.accounts.all().isNotEmpty())
            )
        }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :apps:flipcash:features:login:testDebugUnitTest --tests '*LoginViewModelAccountsTest*'`
Expected: PASS, 2 tests.

- [ ] **Step 6: Branch the router's login action**

In `OnboardingFlowScreen.kt`, replace line 380:

```kotlin
            login = { flowNavigator.navigateTo(OnboardingStep.SeedInput) },
```

with:

```kotlin
            // Mirrors iOS's OnboardingViewModel.loginAction: the list when we have one, the
            // access key field when we do not.
            login = {
                if (state.hasStoredAccounts) {
                    flowNavigator.navigateTo(OnboardingStep.AccountSelection)
                } else {
                    flowNavigator.navigateTo(OnboardingStep.SeedInput)
                }
            },
```

- [ ] **Step 7: Register the step**

In `onboardingEntryProvider`, after the `OnboardingStep.SeedInput` entry:

```kotlin
    annotatedEntry<OnboardingStep.AccountSelection>(testTag = "account_selection_screen") {
        AccountSelectionStepContent()
    }
```

Match the exact `annotatedEntry` argument shape the neighbouring entries use — some pass a test tag, some do not.

Then add the step content next to `SeedInputStepContent`:

```kotlin
@Composable
private fun AccountSelectionStepContent() {
    val viewModel: AccountSelectionViewModel = hiltViewModel()
    val loginViewModel: LoginViewModel = hiltViewModel()
    val flowNavigator = LocalFlowNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    AccountSelectionContent(
        state = state,
        // The store holds base64 entropy, and LogIn defaults to fromDeeplink = false, which is
        // the base64 path. Do not pass fromDeeplink = true -- that branch base58-decodes.
        onSelect = { entropy ->
            loginViewModel.dispatchEvent(LoginViewModel.Event.LogIn(entropy))
        },
        onRemove = { entropy ->
            viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnRemoveRequested(entropy))
        },
        onEnterAccessKey = { flowNavigator.navigateTo(OnboardingStep.SeedInput) },
    )
}
```

Read `SeedInputStepContent` and `LoginStepContent` first: copy how they obtain `flowNavigator` and the `LoginViewModel`, and reuse the same `LoggedInSuccessfully` / `LoggedInRequiresPayment` collectors so a successful selection advances the flow, rather than writing new ones.

- [ ] **Step 8: Update the flow diagram**

The KDoc at the top of the file (around line 89) enumerates the onboarding paths and is the only map of this flow anyone reads. Path 2 currently starts:

```
 * 2. Seed restore (ResumePoint.Login → LoggedIn via SeedInput)
 *    Start → SeedInput ──┬────────────→ Name² → Contacts¹ → Notifications → Home³
```

Change it to show the branch:

```
 * 2. Seed restore (ResumePoint.Login → LoggedIn via SeedInput or AccountSelection)
 *    Start ─┬─ AccountSelection ─┬─────→ Name² → Contacts¹ → Notifications → Home³
 *           │   (stored accounts)│
 *           └─ SeedInput ────────┘
 *    AccountSelection also falls through to SeedInput via "Enter a Different Access Key".
```

Read the surrounding diagram first and match its alignment and footnote markers.

- [ ] **Step 9: Build**

Run: `./gradlew :apps:flipcash:app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add apps/flipcash
git commit -m "feat(login): route Log In to the account list when there is one"
```

Commit body:

```
Mirrors iOS's loginAction. An empty store keeps today's behaviour and goes
straight to the access key field.
```

---

## Task 11: The in-app switcher

The switch machinery already works: `logoutAndSwitchAccount` parks the entropy, and `App.kt:319` consumes it after logout and restarts onboarding with it as the seed. Only the picker in front of it is new.

**There is a latent encoding bug to fix here.** `App.kt` passes the parked entropy as `OnboardingFlow(seed = ...)`, which reaches `LoginViewModel.Event.LogIn(seed, fromDeeplink = true)` and is **base58-decoded** (`LoginViewModel.kt:102-130`). The deleted staff switcher passed `mnemonicManager.getEncodedBase64(it)`. That mismatch never fired because the feature was never enabled. Pass base58.

**Files:**
- Modify: `apps/flipcash/core/src/main/kotlin/com/flipcash/app/core/AppRoute.kt:297-315`
- Modify: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/internal/accounts/AccountSelectionViewModel.kt`
- Create: `apps/flipcash/features/login/src/main/kotlin/com/flipcash/app/login/AccountSelectionScreen.kt`
- Modify: `apps/flipcash/app/src/main/kotlin/com/flipcash/app/internal/ui/navigation/AppScreenContent.kt:150`
- Modify: `apps/flipcash/features/advanced/src/main/kotlin/com/flipcash/app/advanced/internal/AdvancedFeatureMenuItems.kt`
- Modify: `apps/flipcash/features/advanced/src/main/kotlin/com/flipcash/app/advanced/internal/AdvancedFeaturesScreenViewModel.kt`

- [ ] **Step 1: Add the route**

In `AppRoute.Menu`, after `DeviceLogs`:

```kotlin
        @Serializable
        data object AccountSelection : Menu
```

Match the annotations the neighbouring members carry — some routes in that file are `@Parcelize` as well.

- [ ] **Step 2: Handle selection in the view model**

In `AccountSelectionViewModel.init`, alongside the other collectors:

```kotlin
        eventFlow
            .filterIsInstance<Event.OnAccountSelected>()
            .onEach { event ->
                val mnemonic = mnemonicManager.fromEntropyBase64(event.entropy)
                // App.kt feeds this to OnboardingFlow(seed = ...), which base58-decodes it.
                authManager.logoutAndSwitchAccount(mnemonicManager.getEncodedBase58(mnemonic))
            }
            .launchIn(viewModelScope)
```

Confirm `getEncodedBase58`'s exact name and parameter on `MnemonicManager` before writing it.

This handler only runs for the in-app switcher: the onboarding step content calls `LoginViewModel.Event.LogIn` directly and never dispatches `OnAccountSelected`, so the two paths stay separate.

- [ ] **Step 3: Write the screen entry point**

```kotlin
package com.flipcash.app.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.login.internal.accounts.AccountSelectionContent
import com.flipcash.app.login.internal.accounts.AccountSelectionViewModel

/**
 * The logged-in switcher. Selecting a row logs out and parks the entropy; `App.kt` picks it up
 * once the logout lands and restarts onboarding with it.
 *
 * The "Enter a Different Access Key" footer is hidden here: from inside the app there is no
 * onboarding flow to fall through to, and a logged-in user reaching for a new access key is
 * logging out, which the menu already offers.
 */
@Composable
fun AccountSelectionScreen() {
    val viewModel: AccountSelectionViewModel = hiltViewModel()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    AccountSelectionContent(
        state = state,
        onSelect = { entropy ->
            viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnAccountSelected(entropy))
        },
        onRemove = { entropy ->
            viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnRemoveRequested(entropy))
        },
        onEnterAccessKey = {},
        showEnterAccessKey = false,
    )
}
```

`AccountSelectionContent` is `internal`, and this file is in the same module, so it is visible.

- [ ] **Step 4: Register the route**

In `AppScreenContent.kt`, after line 150:

```kotlin
    annotatedEntry<AppRoute.Menu.AccountSelection> { AccountSelectionScreen() }
```

Add the `com.flipcash.app.login.AccountSelectionScreen` import, and check the app module already depends on the login feature:

```bash
grep -n "features:login" apps/flipcash/app/build.gradle.kts
```

- [ ] **Step 5: Add the menu entry**

In `AdvancedFeatureMenuItems.kt`, add a replacement for the item Task 7 deleted — a full menu item this time, with no feature flag:

```kotlin
internal data object SwitchAccount : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_switchaccounts)
    override val name: String
        @Composable get() = stringResource(R.string.title_switchAccounts)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OpenScreen(AppRoute.Menu.AccountSelection)
}
```

In `AdvancedFeaturesScreenViewModel.kt`, add `add(SwitchAccount)` back to `FullMenuList`, above `add(LogOut)`. This reuses the existing `OpenScreen` event, so no new view model plumbing is needed.

- [ ] **Step 6: Build**

Run: `./gradlew :apps:flipcash:app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run the tests for every touched module**

```bash
./gradlew :apps:flipcash:shared:authentication:testDebugUnitTest :apps:flipcash:features:login:testDebugUnitTest :apps:flipcash:features:advanced:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/flipcash
git commit -m "feat(menu): switch accounts from the account list"
```

Commit body:

```
The switch machinery already worked; only the picker in front of it is new.
Passes base58 entropy -- App.kt hands the parked value to OnboardingFlow as a
seed, which base58-decodes it, and the removed staff switcher passed base64.
```

---

## Task 12: Prove the premise on a device

The unit tests cover the codec and the store's semantics. They cannot show that Block Store actually survives an uninstall, which is the entire reason for this work.

- [ ] **Step 1: Install and create two accounts**

```bash
./gradlew :apps:flipcash:app:installDebug
```

Log in to two different accounts so both are upserted.

- [ ] **Step 2: Confirm the list shows both**

Menu → Switch Account. Expected: both accounts, each with a name, a created date, a truncated owner address, and either a balance or "Not Found".

- [ ] **Step 3: Confirm Backup services are on**

On the device: Settings → Google → Backup. Expected: on. If it is off, turn it on before the next step — this is the condition the whole mechanism depends on.

- [ ] **Step 4: Uninstall and reinstall**

```bash
adb uninstall com.flipcash.app.android
```

```bash
./gradlew :apps:flipcash:app:installDebug
```

- [ ] **Step 5: Confirm the list survived**

Open the app and tap Log In. Expected: the account list with both accounts, **not** the access key field. This is the check the whole design exists for. If the list is empty here, stop and diagnose before shipping — nothing else in this plan matters if this fails.

- [ ] **Step 6: Confirm the documented degraded behaviour**

Turn Backup services off and repeat Steps 4 and 5. Expected: the list is empty and Log In goes to the access key field, which is the documented behaviour rather than a bug. Confirm the app still logs in normally from there.

- [ ] **Step 7: Open the PR**

Write the body to a file first — inline `--body` gets mangled by the shell:

```bash
cat > /tmp/blockstore-pr-body.md <<'BODY'
iOS keeps its account list in the keychain, which survives an app uninstall.
Android had no equivalent: the list lived in a DataStore that went with app
data, so reinstalling meant re-entering an access key. Block Store keeps bytes
in Play services' own directory rather than the app sandbox, which is the one
mechanism that gets the same outcome.

The whole list is one 4KB Block Store entry -- 40 bytes per account, storing
only the entropy and three timestamps and deriving the owner key and display
name on read. Capped at 50 with least-recently-seen eviction, which uses about
half the budget.

The selection screen mirrors iOS: per-account live balances fetched
concurrently, each request signed by that account's own owner key, a "Not
Found" badge when the backend does not know the account, and soft delete so a
later login restores the record rather than starting it over. It appears in
both places iOS puts it -- "Log In" routes to it when the store is non-empty,
and the menu opens it for a logged-in switch.

Google Credential Manager is removed. It could restore an account but never
enumerate them, so it could not have backed a list, and it never shipped -- the
flag gating it stayed off and its only entry point was staff-only. Nothing to
migrate out of it; the one migration is local, seeding Block Store from the
existing DataStore entropy.

Two fixes ride along:

- `credentials.preferences_pb` holds base64 seeds in cleartext and was included
  in Auto Backup and device transfer, because the rules only excluded the three
  feature-flag datastores. Now excluded from both.
- The switch-account handoff passed base64 entropy to a path that base58-decodes
  it. Never fired, because the feature was never enabled. Now passes base58.
BODY
```

```bash
gh pr create --base code/cash --assignee @me --title "feat(auth): back the account list with Block Store" --body-file /tmp/blockstore-pr-body.md
```

---

## Notes for the implementer

**Where the spec's claims come from.** The 40-bytes-per-account figure and the 50-account cap are worked out in the design doc's "What gets stored". The balance approach reuses `TokenController.fetchTokenAccounts(cluster, metadataProvider)`, which already takes an arbitrary cluster and passes it as both owner and requester — that is the Android shape of iOS's `fetchPrimaryAccounts(owner:)`, and it is why Task 8 is short.

**Things this plan asks you to confirm rather than assume.** Each is marked at the step where it matters: the Block Store artifact version, whether `isEndToEndEncryptionAvailable` is a property or a method, the `trace` signature, `MnemonicPhrase`'s constructor, `AccountCluster`'s authority-key property, `MnemonicManager.getEncodedBase58`, the `BottomBarMessage` field names, `CodeTheme.typography.displaySmall`, `ButtonState.Subtle`, the `annotatedEntry` argument shape, and the `DataStore` key accessors in `PassphraseCredentialManager`. These are small API confirmations against files the plan names, not open design questions.

**Order matters in one place.** Task 3 leaves the authentication module uncompilable until Task 6 removes the `androidx.credentials` imports. Tasks 3 through 6 are one unit; do not stop between them expecting a green build.
