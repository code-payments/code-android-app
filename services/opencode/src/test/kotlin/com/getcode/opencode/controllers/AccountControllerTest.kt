package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.GetAccountsError
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.solana.keys.Mint
import com.getcode.utils.network.NetworkConnectivityListener
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountControllerTest {

    // Hand-written fake: MockK has a known bug returning Result<T> from suspend
    // fns (see GrabBillTransactorTest.kt:82), so we return real Result values here.
    private class FakeAccountRepository(
        var onGetAccounts: () -> Result<AccountResponse> = { Result.failure(GetAccountsError.NotFound()) },
        var onCreate: () -> Result<ID> = { Result.success(listOf<Byte>(1)) },
    ) : AccountRepository {
        var createCount = 0
        override suspend fun isValidAccount(owner: KeyPair): Result<Boolean> = Result.success(true)
        override suspend fun createUserAccount(scope: CoroutineScope, ownerForMint: AccountCluster, mint: Mint): Result<ID> {
            createCount++
            return onCreate()
        }
        override suspend fun getAccounts(accountOwner: KeyPair, requestingOwner: KeyPair, filter: AccountFilter?): Result<AccountResponse> = onGetAccounts()
        override suspend fun getAccount(accountOwner: KeyPair, requestingOwner: KeyPair, filter: AccountFilter): Result<AccountInfo> = Result.failure(GetAccountsError.NotFound())
    }

    private val networkObserver = mockk<NetworkConnectivityListener>(relaxed = true)
    private val owner = mockk<AccountCluster>(relaxed = true)

    @Test
    fun `ensureCoreAccount creates USDF when server reports NotFound`() = runTest {
        val repo = FakeAccountRepository(
            onGetAccounts = { Result.failure(GetAccountsError.NotFound()) },
            onCreate = { Result.success(listOf<Byte>(1)) },
        )
        val controller = AccountController(repo, networkObserver)

        val result = controller.ensureCoreAccount(owner)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.createCount)
    }

    @Test
    fun `ensureCoreAccount fails when create is denied by antispam`() = runTest {
        val repo = FakeAccountRepository(
            onGetAccounts = { Result.failure(GetAccountsError.NotFound()) },
            onCreate = { Result.failure(SubmitIntentError.Denied(listOf("antispam guard denied account creation"))) },
        )
        val controller = AccountController(repo, networkObserver)

        val result = controller.ensureCoreAccount(owner)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SubmitIntentError.Denied)
    }

    @Test
    fun `ensureCoreAccount is a no-op when getAccounts already returns accounts`() = runTest {
        val repo = FakeAccountRepository(
            onGetAccounts = { Result.success(AccountResponse(accounts = emptyMap())) },
        )
        val controller = AccountController(repo, networkObserver)

        val result = controller.ensureCoreAccount(owner)

        assertTrue(result.isSuccess)
        assertEquals(0, repo.createCount)
    }
}
