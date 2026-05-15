package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.deriveCoinbasePoolAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseTokenVaultAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseVaultTokenAccountAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseWhitelistAddress
import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram
import com.getcode.opencode.internal.solana.programs.MemoProgram
import com.getcode.opencode.internal.solana.programs.SystemProgram
import com.getcode.opencode.internal.solana.programs.TokenProgram
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CoinbaseStablecoinSwapperInstructionsTest {

    private fun testKey(seed: Int): PublicKey =
        PublicKey(ByteArray(32) { seed.toByte() }.toList())

    private fun testMint(seed: Int): Mint =
        Mint(ByteArray(32) { seed.toByte() }.toList())

    private fun mintMetadata(
        address: Mint,
        vmAuthority: PublicKey,
        vm: PublicKey,
    ): MintMetadata = MintMetadata(
        address = address,
        decimals = 6,
        name = "Test",
        symbol = "TST",
        description = "",
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        imageUrl = "",
        vmMetadata = VmMetadata(
            vm = vm,
            authority = vmAuthority,
            lockDurationInDays = 21,
        ),
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private val payer = testKey(1)
    private val nonce = testKey(2)
    private val blockhash = testKey(3)
    private val authority = testKey(4)
    private val swapAuthority = testKey(5)
    private val destinationOwner = testKey(6)
    private val feeDestination = testKey(7)
    private val poolFeeRecipient = testKey(8)

    private val fromMintAddress = testMint(10)
    private val fromVmAuthority = testKey(11)
    private val fromVm = testKey(12)
    private val toMintAddress = testMint(20)
    private val toVmAuthority = testKey(21)
    private val toVm = testKey(22)

    private val fromMintMetadata = mintMetadata(fromMintAddress, fromVmAuthority, fromVm)
    private val toMintMetadata = mintMetadata(toMintAddress, toVmAuthority, toVm)

    private val serverParameters = SwapResponseServerParameters.Stablecoin(
        payer = payer,
        nonce = nonce,
        blockhash = blockhash,
        alts = emptyList(),
        computeUnitLimit = 150_000,
        computeUnitPrice = 10_000,
        memoValue = "coinbase_stable_swapper_v0",
        feeDestination = feeDestination,
        poolFeeRecipient = poolFeeRecipient,
    )

    private fun buildInstructions(
        amount: Long = 1_000_000L,
        feeAmount: Long = 50_000L,
        minOutput: Long = 990_000L,
    ) = buildStablecoinSwapperInstructions(
        serverParameters = serverParameters,
        nonce = nonce,
        authority = authority,
        swapAuthority = swapAuthority,
        destinationOwner = destinationOwner,
        fromMintMetadata = fromMintMetadata,
        toMintMetadata = toMintMetadata,
        amount = amount,
        feeAmount = feeAmount,
        minOutput = minOutput,
    )

    // --- Instruction count ---

    @Test
    fun `produces exactly 9 instructions`() {
        val instructions = buildInstructions()
        assertEquals(9, instructions.size)
    }

    // --- Instruction order and programs ---

    @Test
    fun `instruction 0 is SystemProgram AdvanceNonce`() {
        val ix = buildInstructions()[0]
        assertEquals(SystemProgram.address, ix.program)
    }

    @Test
    fun `instruction 1 is ComputeBudget SetComputeUnitLimit`() {
        val ix = buildInstructions()[1]
        assertEquals(ComputeBudgetProgram.address, ix.program)
    }

    @Test
    fun `instruction 2 is ComputeBudget SetComputeUnitPrice`() {
        val ix = buildInstructions()[2]
        assertEquals(ComputeBudgetProgram.address, ix.program)
    }

    @Test
    fun `instruction 3 is Memo`() {
        val ix = buildInstructions()[3]
        assertEquals(MemoProgram.address, ix.program)
    }

    @Test
    fun `instruction 4 is AssociatedTokenProgram CreateIdempotent for swap authority from_mint ATA`() {
        val ix = buildInstructions()[4]
        assertEquals(AssociatedTokenProgram.address, ix.program)
        // The owner of this ATA should be the swapAuthority
        assertEquals(swapAuthority, ix.accounts[2].publicKey)
        // The mint should be the from_mint
        assertEquals(fromMintAddress, ix.accounts[3].publicKey)
    }

    @Test
    fun `instruction 5 is AssociatedTokenProgram CreateIdempotent for destination owner to_mint ATA`() {
        val ix = buildInstructions()[5]
        assertEquals(AssociatedTokenProgram.address, ix.program)
        // The owner of this ATA should be the destinationOwner
        assertEquals(destinationOwner, ix.accounts[2].publicKey)
        // The mint should be the to_mint
        assertEquals(toMintAddress, ix.accounts[3].publicKey)
    }

    @Test
    fun `instruction 6 is VirtualMachineProgram TransferForSwapWithFee`() {
        val ix = buildInstructions()[6]
        assertEquals(VirtualMachineProgram.address, ix.program)
        // vmAuthority is the from_mint's VM authority
        assertEquals(fromVmAuthority, ix.accounts[0].publicKey)
        // vm is the from_mint's VM
        assertEquals(fromVm, ix.accounts[1].publicKey)
        // swapper is the authority (owner)
        assertEquals(authority, ix.accounts[2].publicKey)
        // feeDestination is at index 6
        assertEquals(feeDestination, ix.accounts[6].publicKey)
    }

    @Test
    fun `instruction 7 is CoinbaseStableSwapper Swap`() {
        val ix = buildInstructions()[7]
        assertEquals(CoinbaseStableSwapperProgram.address, ix.program)
    }

    @Test
    fun `instruction 8 is TokenProgram CloseAccount`() {
        val ix = buildInstructions()[8]
        assertEquals(TokenProgram.address, ix.program)
    }

    // --- CoinbaseStableSwapper::Swap instruction account verification ---

    @Test
    fun `swap instruction has correct pool PDA`() {
        val ix = buildInstructions()[7]
        val expectedPool = PublicKey.deriveCoinbasePoolAddress().publicKey
        assertEquals(expectedPool, ix.accounts[0].publicKey)
    }

    @Test
    fun `swap instruction has correct in and out vaults`() {
        val ix = buildInstructions()[7]
        val pool = PublicKey.deriveCoinbasePoolAddress().publicKey
        val expectedInVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, fromMintAddress).publicKey
        val expectedOutVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, toMintAddress).publicKey
        assertEquals(expectedInVault, ix.accounts[1].publicKey)
        assertEquals(expectedOutVault, ix.accounts[2].publicKey)
    }

    @Test
    fun `swap instruction has correct vault token accounts`() {
        val ix = buildInstructions()[7]
        val pool = PublicKey.deriveCoinbasePoolAddress().publicKey
        val inVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, fromMintAddress).publicKey
        val outVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, toMintAddress).publicKey
        val expectedInVaultTA = PublicKey.deriveCoinbaseVaultTokenAccountAddress(inVault).publicKey
        val expectedOutVaultTA = PublicKey.deriveCoinbaseVaultTokenAccountAddress(outVault).publicKey
        assertEquals(expectedInVaultTA, ix.accounts[3].publicKey)
        assertEquals(expectedOutVaultTA, ix.accounts[4].publicKey)
    }

    @Test
    fun `swap instruction userFromTokenAccount is swap authority's from_mint ATA`() {
        val ix = buildInstructions()[7]
        val expected = PublicKey.deriveAssociatedAccount(owner = swapAuthority, mint = fromMintAddress).publicKey
        assertEquals(expected, ix.accounts[5].publicKey)
    }

    @Test
    fun `swap instruction toTokenAccount is destination owner's to_mint ATA`() {
        val ix = buildInstructions()[7]
        val expected = PublicKey.deriveAssociatedAccount(owner = destinationOwner, mint = toMintAddress).publicKey
        assertEquals(expected, ix.accounts[6].publicKey)
    }

    @Test
    fun `swap instruction feeRecipientTokenAccount is poolFeeRecipient's from_mint ATA`() {
        val ix = buildInstructions()[7]
        val expected = PublicKey.deriveAssociatedAccount(owner = poolFeeRecipient, mint = fromMintAddress).publicKey
        assertEquals(expected, ix.accounts[7].publicKey)
    }

    @Test
    fun `swap instruction feeRecipient matches server parameter`() {
        val ix = buildInstructions()[7]
        assertEquals(poolFeeRecipient, ix.accounts[8].publicKey)
    }

    @Test
    fun `swap instruction has correct mints`() {
        val ix = buildInstructions()[7]
        assertEquals(fromMintAddress, ix.accounts[9].publicKey)
        assertEquals(toMintAddress, ix.accounts[10].publicKey)
    }

    @Test
    fun `swap instruction user is swapAuthority`() {
        val ix = buildInstructions()[7]
        assertEquals(swapAuthority, ix.accounts[11].publicKey)
        assertTrue(ix.accounts[11].isSigner)
    }

    @Test
    fun `swap instruction has correct whitelist PDA`() {
        val ix = buildInstructions()[7]
        val expectedWhitelist = PublicKey.deriveCoinbaseWhitelistAddress().publicKey
        assertEquals(expectedWhitelist, ix.accounts[12].publicKey)
    }

    // --- CloseAccount verification ---

    @Test
    fun `close account closes swap authority's from_mint ATA`() {
        val ix = buildInstructions()[8]
        val expectedAta = PublicKey.deriveAssociatedAccount(owner = swapAuthority, mint = fromMintAddress).publicKey
        // account being closed
        assertEquals(expectedAta, ix.accounts[0].publicKey)
        // rent destination is payer
        assertEquals(payer, ix.accounts[1].publicKey)
        // owner is swapAuthority
        assertEquals(swapAuthority, ix.accounts[2].publicKey)
    }

    // --- TransferForSwapWithFee destination links to swap instruction source ---

    @Test
    fun `TransferForSwapWithFee destination matches swap instruction userFromTokenAccount`() {
        val instructions = buildInstructions()
        val transferIx = instructions[6]
        val swapIx = instructions[7]
        // TransferForSwapWithFee destination is account[5]
        assertEquals(transferIx.accounts[5].publicKey, swapIx.accounts[5].publicKey)
    }
}
