package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.GetAccountsError
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.network.NetworkConnectivityListener
import io.mockk.every
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
    fun `ensureCoreAccount is a no-op when getAccounts already returns a USDF primary`() = runTest {
        val repo = FakeAccountRepository(
            onGetAccounts = { Result.success(AccountResponse(accounts = accountsOf(usdfPrimary()))) },
        )
        val controller = AccountController(repo, networkObserver)

        val result = controller.ensureCoreAccount(owner)

        assertTrue(result.isSuccess)
        assertEquals(0, repo.createCount)
    }

    @Test
    fun `ensureCoreAccount creates USDF when getAccounts succeeds without a USDF primary`() = runTest {
        // Regression: a freshly-onboarded owner whose getAccounts responds OK but does
        // not yet contain a USDF core-mint PRIMARY (e.g. only a non-primary or other-mint
        // account, or a create still racing the reactive bootstrap). The server recognizes
        // an OCP user — and can auto-open currency destinations for direct-send tips — only
        // once a USDF primary exists, so onboarding must provision it rather than pass the
        // gate on any successful response.
        val repo = FakeAccountRepository(
            onGetAccounts = {
                Result.success(AccountResponse(accounts = accountsOf(usdfPool(), otherMintPrimary())))
            },
        )
        val controller = AccountController(repo, networkObserver)

        val result = controller.ensureCoreAccount(owner)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.createCount)
    }

    @Test
    fun `ensureCoreAccount creates USDF when getAccounts succeeds with no accounts`() = runTest {
        val repo = FakeAccountRepository(
            onGetAccounts = { Result.success(AccountResponse(accounts = emptyMap())) },
        )
        val controller = AccountController(repo, networkObserver)

        val result = controller.ensureCoreAccount(owner)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.createCount)
    }

    @Test
    fun `ensureCoreAccount tolerates a concurrent provision that already opened the core account`() = runTest {
        // The create loses a race to the reactive bootstrap (or a duplicate open) and is
        // rejected, but a re-fetch shows the USDF primary now exists — onboarding should
        // NOT be blocked in that case.
        var call = 0
        val repo = FakeAccountRepository(
            onCreate = { Result.failure(SubmitIntentError.Denied(listOf("account already exists"))) },
            onGetAccounts = {
                call++
                if (call == 1) {
                    Result.success(AccountResponse(accounts = emptyMap()))
                } else {
                    Result.success(AccountResponse(accounts = accountsOf(usdfPrimary())))
                }
            },
        )
        val controller = AccountController(repo, networkObserver)

        val result = controller.ensureCoreAccount(owner)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.createCount)
    }

    @Test
    fun `ensureCoreAccount ignores a stale cached USDF primary from a prior account`() = runTest {
        // Cross-account bleed regression: the controller cached a prior account's USDF
        // primary, then a new account onboards in the same process. The server (source of
        // truth) has no accounts for the new owner, so the gate must provision rather than
        // short-circuit on the stale cache — otherwise the fresh account is released to the
        // scanner with no core account and can't receive a direct-send tip until restart.
        val repo = FakeAccountRepository(
            onGetAccounts = { Result.success(AccountResponse(accounts = accountsOf(usdfPrimary()))) },
        )
        val controller = AccountController(repo, networkObserver)

        // Seed the cache as if a prior account's accounts had been fetched.
        controller.ensureCoreAccount(owner)
        assertEquals(0, repo.createCount)

        // New account: server reports NotFound for this owner.
        repo.onGetAccounts = { Result.failure(GetAccountsError.NotFound()) }
        val result = controller.ensureCoreAccount(owner)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.createCount)
    }

    @Test
    fun `onUserLoggedIn clears cached accounts when the account changes`() = runTest {
        val repo = FakeAccountRepository(
            onGetAccounts = { Result.success(AccountResponse(accounts = accountsOf(usdfPrimary()))) },
        )
        val controller = AccountController(repo, networkObserver)
        controller.ensureCoreAccount(owner)
        assertTrue(controller.hasAccountFor(Mint.usdf))

        // A different account signs in; server has nothing for it, so nothing repopulates.
        repo.onGetAccounts = { Result.failure(GetAccountsError.NotFound()) }
        controller.onUserLoggedIn(mockk(relaxed = true))

        assertTrue(!controller.hasAccountFor(Mint.usdf))
    }

    private fun accountsOf(vararg infos: AccountInfo): Map<PublicKey, AccountInfo> =
        infos.associateBy { it.address }

    private fun usdfPrimary() = accountInfo(Mint.usdf, AccountType.Primary)
    private fun usdfPool() = accountInfo(Mint.usdf, AccountType.Pool)
    private fun otherMintPrimary() = accountInfo(Mint.usdc, AccountType.Primary)

    private fun accountInfo(accountMint: Mint, type: AccountType): AccountInfo {
        val addr = mockk<PublicKey>()
        return mockk<AccountInfo> {
            every { address } returns addr
            every { mint } returns accountMint
            every { accountType } returns type
        }
    }
}
