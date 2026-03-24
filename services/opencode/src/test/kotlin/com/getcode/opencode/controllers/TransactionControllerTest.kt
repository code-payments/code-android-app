package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.Swap
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.opencode.repositories.SwapRepository
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.Key32
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.base64
import com.getcode.utils.network.NetworkObserverStub
import com.hoc081098.channeleventbus.ChannelEventBus
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionControllerTest {

    private val transactionRepository = FakeTransactionRepository()
    private val swapRepository = FakeSwapRepository()
    private val accountRepository = FakeTransactionAccountRepository()
    private val networkObserver = NetworkObserverStub(isConnected = false)
    private val accountController = AccountController(accountRepository, networkObserver)
    private val eventBus = ChannelEventBus()
    private val verifiedStateManager = mockk<VerifiedProtoManager>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit
        mockkStatic("com.getcode.utils.ExtensionsKt")
        every { any<List<Byte>>().base64 } returns ""
        every { any<ByteArray>().base64 } returns ""
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.utils.LoggingKt")
        unmockkStatic("com.getcode.utils.ExtensionsKt")
    }

    private val controller = TransactionController(
        repository = transactionRepository,
        swapRepository = swapRepository,
        accountController = accountController,
        eventBus = eventBus,
        verifiedStateManager = verifiedStateManager,
    )

    // region limits

    @Test
    fun `limits starts with Empty`() {
        assertEquals(Limits.Empty, controller.limits.value)
    }

    @Test
    fun `areLimitsStale returns true for empty limits`() {
        assertTrue(controller.areLimitsStale)
    }

    // endregion

    // region checkWithdrawalAvailability

    @Test
    fun `checkWithdrawalAvailability fails for invalid base58 address`() = runTest {
        val result = controller.checkWithdrawalAvailability("not-valid-base58!!!", Mint.usdf)

        assertTrue(result.isFailure)
    }

    @Test
    fun `checkWithdrawalAvailability delegates to repository for valid address`() = runTest {
        val availability = mockk<WithdrawalAvailability>(relaxed = true)
        transactionRepository.withdrawalAvailabilityResult = Result.success(availability)

        // Valid 32-byte base58 string (a known Mint address)
        val address = Mint.usdf.base58()
        val result = controller.checkWithdrawalAvailability(address, Mint.usdf)

        assertTrue(result.isSuccess)
    }

    // endregion

    // region cancelRemoteSend

    @Test
    fun `cancelRemoteSend delegates to repository voidGiftCard`() = runTest {
        transactionRepository.voidGiftCardResult = Result.success(Unit)

        val owner = mockk<AccountCluster>(relaxed = true)
        val vault = Key32.mock
        val result = controller.cancelRemoteSend(owner, vault)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `cancelRemoteSend propagates failure`() = runTest {
        val error = RuntimeException("denied")
        transactionRepository.voidGiftCardResult = Result.failure(error)

        val owner = mockk<AccountCluster>(relaxed = true)
        val vault = Key32.mock
        val result = controller.cancelRemoteSend(owner, vault)

        assertTrue(result.isFailure)
        assertEquals("denied", result.exceptionOrNull()?.message)
    }

    // endregion

    // region pollSwapForState

    @Test
    fun `pollSwapForState delegates to swapRepository`() = runTest {
        val swapMetadata = mockk<SwapMetadata>(relaxed = true)
        swapRepository.pollForStateResult = Result.success(swapMetadata)

        val owner = mockk<AccountCluster>(relaxed = true)
        val swapId = SwapId(ByteArray(32) { 0x01 }.toList())
        val result = controller.pollSwapForState(swapId, owner, SwapState.FINALIZED)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `pollSwapForState propagates failure`() = runTest {
        val error = RuntimeException("timeout")
        swapRepository.pollForStateResult = Result.failure(error)

        val owner = mockk<AccountCluster>(relaxed = true)
        val swapId = SwapId(ByteArray(32) { 0x01 }.toList())
        val result = controller.pollSwapForState(swapId, owner, SwapState.FINALIZED)

        assertTrue(result.isFailure)
    }

    // endregion

    // region getIntentMetadata

    @Test
    fun `getIntentMetadata delegates to repository`() = runTest {
        val metadata = mockk<TransactionMetadata>(relaxed = true)
        transactionRepository.getIntentMetadataResult = Result.success(metadata)

        val owner = mockk<Ed25519.KeyPair>(relaxed = true)
        val intentId = Key32.mock
        val result = controller.getIntentMetadata(intentId, owner)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `getIntentMetadata propagates failure`() = runTest {
        val error = RuntimeException("not found")
        transactionRepository.getIntentMetadataResult = Result.failure(error)

        val owner = mockk<Ed25519.KeyPair>(relaxed = true)
        val intentId = Key32.mock
        val result = controller.getIntentMetadata(intentId, owner)

        assertTrue(result.isFailure)
    }

    // endregion

    // region submitIntent

    @Test
    fun `submitIntent delegates to repository`() = runTest {
        val intent = mockk<IntentType>(relaxed = true)
        transactionRepository.submitIntentResult = Result.success(intent)

        val authority = mockk<Ed25519.KeyPair>(relaxed = true)
        val result = controller.submitIntent(controller.scope, intent, authority)

        assertTrue(result.isSuccess)
    }

    // endregion
}

// region Fakes

private class FakeTransactionRepository : TransactionRepository {
    var submitIntentResult: Result<IntentType> = Result.failure(RuntimeException("not configured"))
    var getIntentMetadataResult: Result<TransactionMetadata> = Result.failure(RuntimeException("not configured"))
    var getLimitsResult: Result<Limits> = Result.success(Limits.Empty)
    var withdrawalAvailabilityResult: Result<WithdrawalAvailability> = Result.failure(RuntimeException("not configured"))
    var voidGiftCardResult: Result<Unit> = Result.success(Unit)
    var buyResult: Result<SwapId> = Result.failure(RuntimeException("not configured"))
    var sellResult: Result<SwapId> = Result.failure(RuntimeException("not configured"))

    override suspend fun submitIntent(
        scope: CoroutineScope, intent: IntentType, owner: Ed25519.KeyPair,
    ): Result<IntentType> = submitIntentResult

    override suspend fun getIntentMetadata(
        intentId: PublicKey, owner: Ed25519.KeyPair,
    ): Result<TransactionMetadata> = getIntentMetadataResult

    override suspend fun getLimits(
        owner: Ed25519.KeyPair, consumedSince: Instant,
    ): Result<Limits> = getLimitsResult

    override suspend fun withdrawalAvailability(
        destination: PublicKey, mint: Mint,
    ): Result<WithdrawalAvailability> = withdrawalAvailabilityResult

    override suspend fun voidGiftCard(
        owner: Ed25519.KeyPair, giftCardVault: PublicKey,
    ): Result<Unit> = voidGiftCardResult

    override suspend fun buy(
        scope: CoroutineScope, owner: AccountCluster, amount: LocalFiat,
        of: MintMetadata, swapId: SwapId?, verifiedState: VerifiedState,
        source: SwapFundingSource, fund: (suspend (SwapRequest) -> Result<Unit>)?,
    ): Result<SwapId> = buyResult

    override suspend fun sell(
        scope: CoroutineScope, owner: AccountCluster, amount: LocalFiat,
        of: MintMetadata, verifiedState: VerifiedState,
    ): Result<SwapId> = sellResult
}

private class FakeSwapRepository : SwapRepository {
    var pollForStateResult: Result<SwapMetadata> = Result.failure(RuntimeException("not configured"))

    override suspend fun getSwap(
        swapId: SwapId, owner: Ed25519.KeyPair,
    ): Result<Swap> = Result.failure(RuntimeException("not configured"))

    override suspend fun getPendingSwaps(
        owner: Ed25519.KeyPair,
    ): Result<List<Swap>> = Result.success(emptyList())

    override suspend fun pollForState(
        swapId: SwapId, owner: Ed25519.KeyPair, targetState: SwapState,
    ): Result<SwapMetadata> = pollForStateResult
}

private class FakeTransactionAccountRepository : AccountRepository {
    override suspend fun isValidAccount(owner: Ed25519.KeyPair) = Result.success(true)
    override suspend fun createUserAccount(
        scope: CoroutineScope, ownerForMint: AccountCluster, mint: Mint,
    ): Result<ID> = Result.success(emptyList())
    override suspend fun getAccounts(
        accountOwner: Ed25519.KeyPair, requestingOwner: Ed25519.KeyPair, filter: AccountFilter?,
    ): Result<AccountResponse> = Result.success(AccountResponse(emptyMap()))
    override suspend fun getAccount(
        accountOwner: Ed25519.KeyPair, requestingOwner: Ed25519.KeyPair, filter: AccountFilter,
    ): Result<AccountInfo> = Result.failure(RuntimeException("not configured"))
}

// endregion
