package com.flipcash.app.login.internal.accounts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.auth.internal.accounts.AccountProfileCache
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
import com.getcode.util.resources.ResourceHelper
import com.flipcash.libs.coroutines.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
     * The product rule: the switcher never signs in as an account the user has not switched to.
     * A row that is not the signed-in account takes its name from the local cache, and loading it
     * makes one network call, the balance lookup — never the Login RPC, which is the only way from
     * an owner key to the user id that GetProfile needs.
     */
    @Test
    fun `a non-active row is titled from the cache without logging in`() =
        runTest(mainCoroutineRule.dispatcher) {
            // Real derivation reaches android.util.Base64, which is a stub on the JVM; the row only
            // needs a cluster with an owner key to look up the cache and fetch a balance with.
            val phrase = MnemonicPhrase(MnemonicPhrase.Kind.L12, List(12) { "abandon" })
            val ownerKey = PublicKey(List(32) { 7 })
            mockkObject(DerivedKey)
            every { DerivedKey.derive(any(), phrase) } returns mockk(relaxed = true)
            mockkObject(AccountCluster)
            every { AccountCluster.newInstance(any(), any()) } returns
                mockk(relaxed = true) { every { authorityPublicKey } returns ownerKey }
            val owner = ownerKey.base58()
            val cache: AccountProfileCache = mock {
                onBlocking { all() } doReturn mapOf(
                    owner to AccountProfileName(username = "sally_streamer", displayName = "Sally"),
                )
            }
            whenever(authManager.accounts).thenReturn(RecordingAccountStore(listOf(record("a", 1_000L))))
            whenever(authManager.accountProfiles).thenReturn(cache)
            whenever(authManager.currentEntropy).thenReturn("b")
            whenever(mnemonicManager.fromEntropyBase64("a")).thenReturn(phrase)
            val tokenController: TokenController = mock {
                onBlocking { fetchTokenBalances(any()) } doReturn Result.failure(IllegalStateException("offline"))
            }
            val viewModel = AccountSelectionViewModel(
                authManager = authManager,
                mnemonicManager = mnemonicManager,
                tokenController = tokenController,
                resources = resources,
                dispatchers = TestDispatchers(testScheduler),
            )
            advanceUntilIdle()

            val row = viewModel.stateFlow.value.accounts.single()
            assertEquals("@sally_streamer", row.name)
            assertFalse(row.notFound)
            verify(authManager, never()).login(any(), any(), any(), any())
            verify(tokenController).fetchTokenBalances(any())
            verifyNoMoreInteractions(tokenController)
        }

    /**
     * The row pipeline cannot call Login if it cannot reach it. The profile fetch this screen once
     * had came in as one more constructor dependency and logged in as every listed account, so any
     * new dependency fails here until someone confirms it cannot sign in as a non-active account.
     * [AuthManager]'s own login is covered by the test above.
     */
    @Test
    fun `the view model depends on nothing that can log in as another account`() {
        val allowed = listOf(
            AuthManager::class.java,
            MnemonicManager::class.java,
            TokenController::class.java,
            ResourceHelper::class.java,
            DispatcherProvider::class.java,
        )
        val injected = AccountSelectionViewModel::class.java.constructors
            .single { it.parameterCount > 0 }
            .parameterTypes.toList()
        assertEquals(allowed, injected)
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
