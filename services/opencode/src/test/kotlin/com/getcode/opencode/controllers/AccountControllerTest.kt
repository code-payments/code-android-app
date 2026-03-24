package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.GetAccountsError
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.Key32
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.network.NetworkObserverStub
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountControllerTest {

    private val repository = FakeAccountRepository()
    private val networkObserver = NetworkObserverStub(isConnected = false)
    private val controller = AccountController(repository, networkObserver)

    // region hasAccountFor

    @Test
    fun `hasAccountFor returns false when no accounts exist`() {
        assertFalse(controller.hasAccountFor(Mint.usdf))
    }

    // endregion

    // region createUserAccount

    @Test
    fun `createUserAccount delegates to repository`() = runTest {
        val id: ID = listOf(1, 2, 3)
        repository.createUserAccountResult = Result.success(id)

        val cluster = mockk<AccountCluster>(relaxed = true)
        val result = controller.createUserAccount(cluster, Mint.usdf)

        assertTrue(result.isSuccess)
        assertEquals(id, result.getOrThrow())
    }

    @Test
    fun `createUserAccount propagates failure`() = runTest {
        val error = RuntimeException("creation failed")
        repository.createUserAccountResult = Result.failure(error)

        val cluster = mockk<AccountCluster>(relaxed = true)
        val result = controller.createUserAccount(cluster, Mint.usdf)

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    // endregion

    // region getAccounts

    @Test
    fun `getAccounts returns accounts from repository`() = runTest {
        val accountInfo = stubAccountInfo(Mint.usdf)
        val response = AccountResponse(
            accounts = mapOf(accountInfo.address to accountInfo)
        )
        repository.getAccountsResult = Result.success(response)

        val cluster = mockk<AccountCluster>(relaxed = true)
        val result = controller.getAccounts(cluster, cluster)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().accounts.size)
    }

    @Test
    fun `getAccounts propagates repository failure`() = runTest {
        val error = GetAccountsError.NotFound()
        repository.getAccountsResult = Result.failure(error)

        val cluster = mockk<AccountCluster>(relaxed = true)
        val result = controller.getAccounts(cluster, cluster)

        assertTrue(result.isFailure)
        assertIs<GetAccountsError.NotFound>(result.exceptionOrNull())
    }

    // endregion

    // region getAccount

    @Test
    fun `getAccount returns single account from repository`() = runTest {
        val accountInfo = stubAccountInfo(Mint.usdf)
        repository.getAccountResult = Result.success(accountInfo)

        val cluster = mockk<AccountCluster>(relaxed = true)
        val filter = AccountFilter.AccountType(AccountType.Primary)
        val result = controller.getAccount(cluster, cluster, filter)

        assertTrue(result.isSuccess)
        assertEquals(accountInfo.mint, result.getOrThrow().mint)
    }

    @Test
    fun `getAccount propagates repository failure`() = runTest {
        val error = GetAccountsError.NotFound()
        repository.getAccountResult = Result.failure(error)

        val cluster = mockk<AccountCluster>(relaxed = true)
        val filter = AccountFilter.AccountType(AccountType.Primary)
        val result = controller.getAccount(cluster, cluster, filter)

        assertTrue(result.isFailure)
        assertIs<GetAccountsError.NotFound>(result.exceptionOrNull())
    }

    // endregion

    // region helpers

    private fun stubAccountInfo(mint: Mint): AccountInfo {
        return AccountInfo(
            address = Key32.mock,
            owner = null,
            authority = null,
            accountType = AccountType.Primary,
            index = 0,
            balanceSource = AccountInfo.BalanceSource.Cache,
            balance = 1000L,
            managementState = AccountInfo.ManagementState.Locked,
            blockchainState = AccountInfo.BlockchainState.Exists,
            claimState = AccountInfo.ClaimState.Unknown,
            originalExchangeData = mockk(relaxed = true),
            mint = mint,
            createdAt = null,
            isGiftCardIssuer = false,
            usdCostBasis = 0.0,
        )
    }

    // endregion
}

/**
 * Fake [AccountRepository]. MockK cannot return [kotlin.Result] from mocked interfaces.
 */
private class FakeAccountRepository : AccountRepository {
    var isValidAccountResult: Result<Boolean> = Result.success(true)
    var createUserAccountResult: Result<ID> = Result.success(emptyList())
    var getAccountsResult: Result<AccountResponse> = Result.success(AccountResponse(emptyMap()))
    var getAccountResult: Result<AccountInfo> = Result.failure(RuntimeException("not configured"))

    override suspend fun isValidAccount(owner: Ed25519.KeyPair): Result<Boolean> =
        isValidAccountResult

    override suspend fun createUserAccount(
        scope: CoroutineScope,
        ownerForMint: AccountCluster,
        mint: Mint,
    ): Result<ID> = createUserAccountResult

    override suspend fun getAccounts(
        accountOwner: Ed25519.KeyPair,
        requestingOwner: Ed25519.KeyPair,
        filter: AccountFilter?,
    ): Result<AccountResponse> = getAccountsResult

    override suspend fun getAccount(
        accountOwner: Ed25519.KeyPair,
        requestingOwner: Ed25519.KeyPair,
        filter: AccountFilter,
    ): Result<AccountInfo> = getAccountResult
}

