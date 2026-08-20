package com.getcode.opencode.solana.swap

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.IntentStatefulSwap
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapProgram
import com.getcode.opencode.model.transactions.SwapRoute
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The buy fee has to be carried all the way from the initiate request into the transaction the
 * client signs. The server builds *its* half from the fee quoted in the initiate request, so a
 * client that quotes a fee and then builds `VM::TransferForSwap` (no fee) instead of
 * `VM::TransferForSwapWithFee` produces a transaction the server cannot match — every fee-bearing
 * "Get" failed that way.
 *
 * These exercise the [TransactionBuilder.swap] seam rather than the instruction builder directly,
 * because the defect was in what the seam forwarded, not in how the instruction was encoded.
 */
class ExistingCurrencyBuyFeeTest {

    private fun key(seed: Int): PublicKey = PublicKey(ByteArray(32) { seed.toByte() }.toList())
    private fun mint(seed: Int): Mint = Mint(ByteArray(32) { seed.toByte() }.toList())

    private val feeDestination = key(9)

    private val targetMint = MintMetadata(
        address = mint(7),
        decimals = 6,
        name = "Target",
        symbol = "TGT",
        description = "",
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        imageUrl = "",
        vmMetadata = VmMetadata(vm = key(5), authority = key(6), lockDurationInDays = 21),
        launchpadMetadata = LaunchpadMetadata(
            currencyConfig = key(10),
            liquidityPool = key(11),
            seed = key(12),
            authority = key(13),
            mintVault = key(14),
            coreMintVault = key(15),
            currentCirculatingSupplyQuarks = 0,
            sellFeeBps = 0,
            price = Fiat.MIN_VALUE,
            marketCap = Fiat.MIN_VALUE,
        ),
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private fun serverParams(feeDestination: PublicKey?) =
        StatefulSwapResponseServerParameters.ExistingCurrency(
            payer = key(1),
            nonce = key(2),
            blockhash = key(3),
            alts = emptyList(),
            computeUnitLimit = 500_000,
            computeUnitPrice = 1_000,
            memoValue = "buy_v0",
            memoryAccount = key(4),
            memoryIndex = 1,
            feeDestination = feeDestination,
        )

    private fun buy(feeDestination: PublicKey?, feeAmount: Long) = TransactionBuilder.swap(
        response = serverParams(feeDestination),
        authority = key(20),
        swapAuthority = key(21),
        route = SwapRoute.Buy(targetMint),
        amount = SWAP_AMOUNT,
        feeAmount = feeAmount,
        minOutput = 1L,
    )

    /** The VM instruction is the one the two sides have to agree on. */
    private fun vmTransferData(transaction: com.getcode.opencode.solana.SolanaTransaction): List<Byte> =
        transaction.message.instructions
            .first { it.data.firstOrNull() == VirtualMachineProgram.Command.transferForSwap.value ||
                     it.data.firstOrNull() == VirtualMachineProgram.Command.transferForSwapWithFee.value }
            .data

    @Test
    fun `buy with a fee uses the with-fee VM transfer`() {
        val data = vmTransferData(buy(feeDestination, FEE_AMOUNT))

        assertEquals(
            VirtualMachineProgram.Command.transferForSwapWithFee.value,
            data[0],
            "a quoted fee must produce VM::TransferForSwapWithFee",
        )
        // command(1) + swapAmount(8) + feeAmount(8) + bump(1)
        assertEquals(18, data.size)
    }

    @Test
    fun `buy with a fee encodes the swap amount and the fee separately`() {
        val data = buy(feeDestination, FEE_AMOUNT).let(::vmTransferData)

        assertEquals(SWAP_AMOUNT, data.subList(1, 9).toLong())
        assertEquals(FEE_AMOUNT, data.subList(9, 17).toLong())
    }

    @Test
    fun `buy with a fee routes it to the server's fee destination`() {
        val message = buy(feeDestination, FEE_AMOUNT).message
        val instruction = message.instructions
            .first { it.data.firstOrNull() == VirtualMachineProgram.Command.transferForSwapWithFee.value }

        // vmAuthority, vm, swapper, swapPda, swapAta, destination, feeDestination, tokenProgram
        assertEquals(8, instruction.accountIndexes.size)
        assertEquals(
            feeDestination,
            message.accountKeys[instruction.accountIndexes[6].toInt()],
        )
    }

    @Test
    fun `buy without a fee stays on the plain VM transfer`() {
        val data = vmTransferData(buy(feeDestination = null, feeAmount = 0))

        assertEquals(VirtualMachineProgram.Command.transferForSwap.value, data[0])
        // command(1) + amount(8) + bump(1)
        assertEquals(10, data.size)
    }

    /**
     * The server only sends a fee destination when a fee actually applies, so a fee without one is
     * a contradiction. Falling back to the plain transfer keeps us from inventing a destination.
     */
    @Test
    fun `a fee with no destination falls back to the plain VM transfer`() {
        val data = vmTransferData(buy(feeDestination = null, feeAmount = FEE_AMOUNT))

        assertEquals(VirtualMachineProgram.Command.transferForSwap.value, data[0])
    }

    // region the defect site: IntentStatefulSwap.transaction()

    private fun localFiat(quarks: Long) = LocalFiat(
        underlyingTokenAmount = Fiat(quarks = quarks),
        nativeAmount = Fiat(quarks = quarks),
        rate = Rate.oneToOne,
        mint = Mint.usdf,
    )

    private fun intent(feeAmount: Long?): IntentStatefulSwap {
        val fundingSource = SwapFundingSource.SubmitIntent(key(30).bytes)
        val request = StatefulSwapRequest(
            owner = mockk<AccountCluster>(relaxed = true) {
                every { authorityPublicKey } returns key(20)
            },
            swapAuthority = mockk<KeyPair> {
                every { publicKeyBytes } returns ByteArray(32) { 21 }
            },
            program = SwapProgram.Reserve(
                fromMint = Mint.usdf,
                toMint = targetMint.address,
                fundingSource = fundingSource,
            ),
            route = SwapRoute.Buy(targetMint),
            swapAmount = localFiat(SWAP_AMOUNT),
            feeAmount = feeAmount?.let(::localFiat),
            swapId = SwapId(key(31).bytes),
            verifiedState = mockk<VerifiedState>(relaxed = true),
        )

        return IntentStatefulSwap(
            request = request,
            metadata = VerifiedSwapMetadata.Reserve(
                id = request.swapId,
                fromMint = Mint.usdf,
                toMint = targetMint.address,
                swapAmount = Fiat(quarks = SWAP_AMOUNT),
                feeAmount = feeAmount?.let { Fiat(quarks = it) },
                fundingSource = fundingSource,
            ),
        )
    }

    /**
     * The regression. The `ExistingCurrency` branch used to drop `request.feeAmount` on the floor
     * and take [TransactionBuilder.swap]'s old `feeAmount = 0` default, so the client built the
     * no-fee transfer for a swap it had already quoted a fee on.
     */
    @Test
    fun `a fee-bearing buy carries the quoted fee into the signed transaction`() {
        val transaction = intent(FEE_AMOUNT).transaction(serverParams(feeDestination))
        val data = vmTransferData(transaction)

        assertEquals(
            VirtualMachineProgram.Command.transferForSwapWithFee.value,
            data[0],
            "the fee quoted in the initiate request must reach the transaction we sign",
        )
        assertEquals(SWAP_AMOUNT, data.subList(1, 9).toLong())
        assertEquals(FEE_AMOUNT, data.subList(9, 17).toLong())
    }

    @Test
    fun `a fee-less buy still builds the plain transfer`() {
        val transaction = intent(feeAmount = null).transaction(serverParams(feeDestination))

        assertEquals(
            VirtualMachineProgram.Command.transferForSwap.value,
            vmTransferData(transaction)[0],
        )
    }

    // endregion

    private fun List<Byte>.toLong(): Long =
        foldIndexed(0L) { index, acc, byte -> acc or ((byte.toLong() and 0xFF) shl (8 * index)) }

    private companion object {
        const val SWAP_AMOUNT = 500_000L
        const val FEE_AMOUNT = 5_000L
    }
}
