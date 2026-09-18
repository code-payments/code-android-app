package com.flipcash.app.login.internal.accounts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.auth.internal.accounts.AccountRecord
import com.flipcash.app.auth.internal.accounts.AccountStore
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.FakeResourceHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
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
}
