package com.flipcash.app.login.internal.accounts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.auth.internal.accounts.AccountProfileCache
import com.flipcash.app.auth.internal.accounts.AccountProfileFetcher
import com.flipcash.app.auth.internal.accounts.CachedAccountProfile
import com.flipcash.app.auth.internal.accounts.AccountProfileName
import com.flipcash.app.auth.internal.accounts.AccountRecord
import com.flipcash.app.auth.internal.accounts.AccountStore
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import com.getcode.util.resources.FakeResourceHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The state the screen's safety guard reads. `currentEntropy` being declared but never populated
 * would leave `isCurrent` permanently false, which makes the signed-in account removable — so it is
 * asserted here rather than left to the UI.
 *
 * Every record's derivation is made to fail, which keeps the test off real PBKDF2/SLIP-10 work and
 * covers the other half of the contract at the same time: a record we cannot derive from still
 * produces a row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSelectionViewModelStateTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val authManager: AuthManager = mock()
    private val mnemonicManager: MnemonicManager = mock()
    private val tokenController: TokenController = mock()
    private val resources = FakeResourceHelper()
    private val profileFetcher: AccountProfileFetcher = mock()
    private val profiles: AccountProfileCache = mock {
        onBlocking { all() } doReturn emptyMap()
    }

    /** Records only what was asked of it; the real store's semantics are covered by its own tests. */
    private class RecordingAccountStore(private var records: List<AccountRecord>) : AccountStore {
        val deleted = mutableListOf<String>()
        override suspend fun all(): List<AccountRecord> = records
        override suspend fun allIncludingDeleted(): List<AccountRecord> = records
        override suspend fun upsert(entropy: String) = Unit
        override suspend fun setDeleted(entropy: String, deleted: Boolean) {
            this.deleted += entropy
            records = records.filterNot { it.entropy == entropy }
        }
        override suspend fun clear() = Unit
    }

    private fun record(entropy: String, creationDate: Long) =
        AccountRecord(entropy = entropy, creationDate = creationDate, lastSeen = creationDate)

    private fun viewModel(store: AccountStore, current: String?, dispatchers: TestDispatchers):
        AccountSelectionViewModel {
        whenever(authManager.accounts).thenReturn(store)
        whenever(authManager.accountProfiles).thenReturn(profiles)
        whenever(authManager.currentEntropy).thenReturn(current)
        whenever(mnemonicManager.fromEntropyBase64(any()))
            .thenThrow(IllegalArgumentException("not a real seed"))
        return AccountSelectionViewModel(
            authManager = authManager,
            mnemonicManager = mnemonicManager,
            tokenController = tokenController,
            profileFetcher = profileFetcher,
            resources = resources,
            dispatchers = dispatchers,
        )
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `load marks which account is signed in`() = runTest(mainCoroutineRule.dispatcher) {
        val store = RecordingAccountStore(listOf(record("a", 2_000L), record("b", 1_000L)))
        val viewModel = viewModel(store, current = "b", TestDispatchers(testScheduler))

        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals("b", state.currentEntropy)
        assertEquals(listOf("a", "b"), state.accounts.map { it.entropy })
        assertTrue(state.accounts.all { it.balanceUnavailable })
        assertTrue(state.accounts.none { it.notFound })
    }

    @Test
    fun `removal refuses the account the user is signed into`() = runTest(mainCoroutineRule.dispatcher) {
        val store = RecordingAccountStore(listOf(record("a", 2_000L), record("b", 1_000L)))
        val viewModel = viewModel(store, current = "b", TestDispatchers(testScheduler))
        advanceUntilIdle()

        viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnAccountRemoved("b"))
        advanceUntilIdle()

        assertEquals(emptyList(), store.deleted)
        assertEquals(listOf("a", "b"), viewModel.stateFlow.value.accounts.map { it.entropy })
    }

    /**
     * Selecting a row is the whole point of the screen, and it hands the account on in base58
     * while the store holds base64 — the one place a silent encoding swap would log the user into
     * nothing. Asserted on the exact string the encoder returned.
     */
    @Test
    fun `selecting an account switches to its base58 seed`() = runTest(mainCoroutineRule.dispatcher) {
        val phrase = MnemonicPhrase(MnemonicPhrase.Kind.L12, List(12) { "abandon" })
        whenever(authManager.accounts).thenReturn(RecordingAccountStore(listOf(record("a", 1_000L))))
        whenever(authManager.accountProfiles).thenReturn(profiles)
        whenever(authManager.currentEntropy).thenReturn(null)
        whenever(mnemonicManager.fromEntropyBase64(any())).thenReturn(phrase)
        whenever(mnemonicManager.getEncodedBase58(phrase)).thenReturn("base58-of-a")
        val viewModel = AccountSelectionViewModel(
            authManager = authManager,
            mnemonicManager = mnemonicManager,
            tokenController = tokenController,
            profileFetcher = profileFetcher,
            resources = resources,
            dispatchers = TestDispatchers(testScheduler),
        )
        advanceUntilIdle()

        viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnAccountSelected("a"))
        advanceUntilIdle()

        verify(authManager).logoutAndSwitchAccount("base58-of-a")
    }

    /** Selection logs the user out, so no other event on the screen may reach it. */
    @Test
    fun `removal does not switch accounts`() = runTest(mainCoroutineRule.dispatcher) {
        val store = RecordingAccountStore(listOf(record("a", 2_000L), record("b", 1_000L)))
        val viewModel = viewModel(store, current = "b", TestDispatchers(testScheduler))
        advanceUntilIdle()

        viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnAccountRemoved("a"))
        advanceUntilIdle()

        verify(authManager, never()).logoutAndSwitchAccount(any())
    }

    @Test
    fun `removal drops any other account`() = runTest(mainCoroutineRule.dispatcher) {
        val store = RecordingAccountStore(listOf(record("a", 2_000L), record("b", 1_000L)))
        val viewModel = viewModel(store, current = "b", TestDispatchers(testScheduler))
        advanceUntilIdle()

        viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnAccountRemoved("a"))
        advanceUntilIdle()

        assertEquals(listOf("a"), store.deleted)
        assertEquals(listOf("b"), viewModel.stateFlow.value.accounts.map { it.entropy })
    }

    /**
     * A single non-active, derivable row with [cached] as its cache entry. Real derivation reaches
     * android.util.Base64, a stub on the JVM, so the cluster is faked; the row only needs an owner
     * key to look up the cache and fetch with.
     */
    private fun TestScope.loadNonActiveRow(
        cached: CachedAccountProfile,
        fetcher: AccountProfileFetcher = profileFetcher,
        tokens: TokenController = offlineTokens(),
    ): AccountSelectionViewModel {
        val phrase = MnemonicPhrase(MnemonicPhrase.Kind.L12, List(12) { "abandon" })
        val ownerKey = PublicKey(List(32) { 7 })
        mockkObject(DerivedKey)
        every { DerivedKey.derive(any(), phrase) } returns mockk(relaxed = true)
        mockkObject(AccountCluster)
        every { AccountCluster.newInstance(any(), any()) } returns
            mockk(relaxed = true) { every { authorityPublicKey } returns ownerKey }
        val cache: AccountProfileCache = mock {
            onBlocking { all() } doReturn mapOf(ownerKey.base58() to cached)
        }
        whenever(authManager.accounts).thenReturn(RecordingAccountStore(listOf(record("a", 1_000L))))
        whenever(authManager.accountProfiles).thenReturn(cache)
        whenever(authManager.currentEntropy).thenReturn("b")
        whenever(mnemonicManager.fromEntropyBase64("a")).thenReturn(phrase)
        return AccountSelectionViewModel(
            authManager = authManager,
            mnemonicManager = mnemonicManager,
            tokenController = tokens,
            profileFetcher = fetcher,
            resources = resources,
            dispatchers = TestDispatchers(testScheduler),
        )
    }

    private fun offlineTokens(): TokenController = mock {
        onBlocking { fetchTokenBalances(any()) } doReturn Result.failure(IllegalStateException("offline"))
    }

    /**
     * A row with no stored user id asks the fetcher to resolve one (the fetcher's Login fallback).
     * The switcher itself never switches the session: [AuthManager.login] is not called.
     */
    @Test
    fun `a row with no stored user id is fetched without a user id`() =
        runTest(mainCoroutineRule.dispatcher) {
            val fetcher: AccountProfileFetcher = mock {
                onBlocking { fetch(any(), any(), isNull()) } doReturn
                    Result.success(AccountProfileName(username = "sally_streamer", displayName = "Sally"))
            }
            val viewModel = loadNonActiveRow(
                cached = CachedAccountProfile(userId = null, name = null),
                fetcher = fetcher,
            )
            advanceUntilIdle()

            assertEquals("@sally_streamer", viewModel.stateFlow.value.accounts.single().name)
            verify(fetcher).fetch(any(), any(), isNull())
            verify(authManager, never()).login(any(), any(), any(), any())
        }

    /** A failed fetch, Login included, keeps whatever name the cache gave the row. */
    @Test
    fun `a failed fetch keeps the cached name`() =
        runTest(mainCoroutineRule.dispatcher) {
            val fetcher: AccountProfileFetcher = mock {
                onBlocking { fetch(any(), any(), anyOrNull()) } doReturn
                    Result.failure(IllegalStateException("offline"))
            }
            val viewModel = loadNonActiveRow(
                cached = CachedAccountProfile(
                    userId = null,
                    name = AccountProfileName(username = "sally_streamer", displayName = "Sally"),
                ),
                fetcher = fetcher,
            )
            advanceUntilIdle()

            val row = viewModel.stateFlow.value.accounts.single()
            assertEquals("@sally_streamer", row.name)
            assertFalse(row.notFound)
        }

    /** A stored user id goes straight to the fetch; the fetched names replace the cached. */
    @Test
    fun `a row with a stored user id is retitled by a profile fetch`() =
        runTest(mainCoroutineRule.dispatcher) {
            val userId = listOf<Byte>(1, 2, 3)
            val fetcher: AccountProfileFetcher = mock {
                onBlocking { fetch(any(), any(), eq(userId)) } doReturn
                    Result.success(AccountProfileName(username = null, displayName = "Sally"))
            }
            val viewModel = loadNonActiveRow(
                cached = CachedAccountProfile(userId = userId, name = null),
                fetcher = fetcher,
            )
            advanceUntilIdle()

            assertEquals("Sally", viewModel.stateFlow.value.accounts.single().name)
            verify(fetcher).fetch(any(), any(), eq(userId))
        }

    private val mnemonicName = "Apple ... Elder"

    @Test
    fun `title prefers the username, as a handle`() {
        assertEquals(
            "@sally_streamer",
            AccountSelectionViewModel.title(username = "sally_streamer", displayName = "Sally", mnemonicName = mnemonicName),
        )
    }

    @Test
    fun `title falls back to the display name without a username`() {
        assertEquals("Sally", AccountSelectionViewModel.title(username = null, displayName = "Sally", mnemonicName = mnemonicName))
        assertEquals("Sally", AccountSelectionViewModel.title(username = " ", displayName = "Sally", mnemonicName = mnemonicName))
    }

    @Test
    fun `title falls back to the mnemonic name without either`() {
        assertEquals(mnemonicName, AccountSelectionViewModel.title(username = null, displayName = null, mnemonicName = mnemonicName))
        assertEquals(mnemonicName, AccountSelectionViewModel.title(username = "", displayName = "", mnemonicName = mnemonicName))
    }
}
