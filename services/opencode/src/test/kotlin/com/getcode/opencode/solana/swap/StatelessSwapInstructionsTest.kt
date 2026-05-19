package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.extensions.deriveCoinbasePoolAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseTokenVaultAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseVaultTokenAccountAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseWhitelistAddress
import com.getcode.opencode.internal.solana.extensions.deriveDepositAccount
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram
import com.getcode.opencode.internal.solana.programs.MemoProgram
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.transactions.StatelessSwapServerParameters
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class StatelessSwapInstructionsTest {

    private fun testKey(seed: Int): PublicKey =
        PublicKey(ByteArray(32) { seed.toByte() }.toList())

    private fun testMint(seed: Int): Mint =
        Mint(ByteArray(32) { seed.toByte() }.toList())

    private fun mintMetadata(
        address: Mint,
        vmAuthority: PublicKey,
        hasVm: Boolean = true,
    ): MintMetadata = MintMetadata(
        address = address,
        decimals = 6,
        name = "Test",
        symbol = "TST",
        description = "",
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        imageUrl = "",
        vmMetadata = if (hasVm) {
            val vm = PublicKey.deriveVirtualMachineAccount(
                mint = address,
                authority = vmAuthority,
                lockout = 21u,
            )
            VmMetadata(
                vm = vm.publicKey,
                authority = vmAuthority,
                lockDurationInDays = 21,
            )
        } else {
            VmMetadata(
                vm = testKey(99),
                authority = vmAuthority,
                lockDurationInDays = 21,
            )
        },
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private val payer = testKey(1)
    private val blockhash = testKey(3)
    private val owner = testKey(4)
    private val poolFeeRecipient = testKey(8)

    private val fromMintAddress = testMint(10)
    private val fromVmAuthority = testKey(11)
    private val toMintAddress = testMint(20)
    private val toVmAuthority = testKey(21)

    private val fromMintMetadata = mintMetadata(fromMintAddress, fromVmAuthority, hasVm = false)
    private val toMintMetadata = mintMetadata(toMintAddress, toVmAuthority, hasVm = true)

    private val serverParameters = StatelessSwapServerParameters(
        payer = payer,
        blockhash = blockhash,
        alts = emptyList(),
        computeUnitLimit = 150_000,
        computeUnitPrice = 10_000,
        memoValue = "stateless_swap_v0",
        poolFeeRecipient = poolFeeRecipient,
    )

    private fun buildInstructions(
        amount: Long = 1_000_000L,
        params: StatelessSwapServerParameters = serverParameters,
    ) = buildStatelessSwapInstructions(
        serverParameters = params,
        owner = owner,
        fromMint = fromMintMetadata,
        toMint = toMintMetadata,
        amount = amount,
    )

    // --- Instruction count ---

    @Test
    fun `produces exactly 5 instructions with all optional fields present`() {
        val instructions = buildInstructions()
        assertEquals(5, instructions.size)
    }

    @Test
    fun `omits ComputeUnitLimit when value is 0`() {
        val params = serverParameters.copy(computeUnitLimit = 0)
        val instructions = buildInstructions(params = params)
        // Should be 4: no SetComputeUnitLimit
        assertEquals(4, instructions.size)
        // First instruction should be ComputeUnitPrice (not ComputeUnitLimit)
        assertEquals(ComputeBudgetProgram.address, instructions[0].program)
    }

    @Test
    fun `omits ComputeUnitPrice when value is 0`() {
        val params = serverParameters.copy(computeUnitPrice = 0)
        val instructions = buildInstructions(params = params)
        assertEquals(4, instructions.size)
    }

    @Test
    fun `omits Memo when value is empty`() {
        val params = serverParameters.copy(memoValue = "")
        val instructions = buildInstructions(params = params)
        assertEquals(4, instructions.size)
    }

    @Test
    fun `omits all optional instructions when all values are zero or empty`() {
        val params = serverParameters.copy(
            computeUnitLimit = 0,
            computeUnitPrice = 0,
            memoValue = "",
        )
        val instructions = buildInstructions(params = params)
        // Only CreateIdempotent + Swap
        assertEquals(2, instructions.size)
        assertEquals(AssociatedTokenProgram.address, instructions[0].program)
        assertEquals(CoinbaseStableSwapperProgram.address, instructions[1].program)
    }

    // --- Instruction order and programs ---

    @Test
    fun `instruction 0 is ComputeBudget SetComputeUnitLimit`() {
        val ix = buildInstructions()[0]
        assertEquals(ComputeBudgetProgram.address, ix.program)
    }

    @Test
    fun `instruction 1 is ComputeBudget SetComputeUnitPrice`() {
        val ix = buildInstructions()[1]
        assertEquals(ComputeBudgetProgram.address, ix.program)
    }

    @Test
    fun `instruction 2 is Memo`() {
        val ix = buildInstructions()[2]
        assertEquals(MemoProgram.address, ix.program)
    }

    @Test
    fun `instruction 3 is AssociatedTokenProgram CreateIdempotent`() {
        val ix = buildInstructions()[3]
        assertEquals(AssociatedTokenProgram.address, ix.program)
    }

    @Test
    fun `instruction 4 is CoinbaseStableSwapper Swap`() {
        val ix = buildInstructions()[4]
        assertEquals(CoinbaseStableSwapperProgram.address, ix.program)
    }

    // --- CreateIdempotent account verification ---

    @Test
    fun `CreateIdempotent payer is server payer`() {
        val ix = buildInstructions()[3]
        assertEquals(payer, ix.accounts[0].publicKey)
    }

    @Test
    fun `CreateIdempotent owner is owner's to_mint VM Deposit PDA`() {
        val ix = buildInstructions()[3]
        val toVm = toMintMetadata.vmMetadata
        val vmAccount = PublicKey.deriveVirtualMachineAccount(
            mint = toMintAddress,
            authority = toVm.authority,
            lockout = toVm.lockDurationInDays.toUByte(),
        )
        val expectedDepositPda = PublicKey.deriveDepositAccount(
            vm = vmAccount.publicKey,
            depositor = owner,
        )
        assertEquals(expectedDepositPda.publicKey, ix.accounts[2].publicKey)
    }

    @Test
    fun `CreateIdempotent mint is to_mint`() {
        val ix = buildInstructions()[3]
        assertEquals(toMintAddress, ix.accounts[3].publicKey)
    }

    // --- CoinbaseStableSwapper::Swap account verification ---

    @Test
    fun `swap instruction has correct pool PDA`() {
        val ix = buildInstructions()[4]
        val expectedPool = PublicKey.deriveCoinbasePoolAddress().publicKey
        assertEquals(expectedPool, ix.accounts[0].publicKey)
    }

    @Test
    fun `swap instruction has correct in and out vaults`() {
        val ix = buildInstructions()[4]
        val pool = PublicKey.deriveCoinbasePoolAddress().publicKey
        val expectedInVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, fromMintAddress).publicKey
        val expectedOutVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, toMintAddress).publicKey
        assertEquals(expectedInVault, ix.accounts[1].publicKey)
        assertEquals(expectedOutVault, ix.accounts[2].publicKey)
    }

    @Test
    fun `swap instruction has correct vault token accounts`() {
        val ix = buildInstructions()[4]
        val pool = PublicKey.deriveCoinbasePoolAddress().publicKey
        val inVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, fromMintAddress).publicKey
        val outVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, toMintAddress).publicKey
        val expectedInVaultTA = PublicKey.deriveCoinbaseVaultTokenAccountAddress(inVault).publicKey
        val expectedOutVaultTA = PublicKey.deriveCoinbaseVaultTokenAccountAddress(outVault).publicKey
        assertEquals(expectedInVaultTA, ix.accounts[3].publicKey)
        assertEquals(expectedOutVaultTA, ix.accounts[4].publicKey)
    }

    @Test
    fun `swap instruction userFromTokenAccount is owner's from_mint ATA`() {
        val ix = buildInstructions()[4]
        val expected = PublicKey.deriveAssociatedAccount(owner = owner, mint = fromMintAddress).publicKey
        assertEquals(expected, ix.accounts[5].publicKey)
    }

    @Test
    fun `swap instruction toTokenAccount is owner's to_mint VM Deposit ATA`() {
        val ix = buildInstructions()[4]
        val toVm = toMintMetadata.vmMetadata
        val vmAccount = PublicKey.deriveVirtualMachineAccount(
            mint = toMintAddress,
            authority = toVm.authority,
            lockout = toVm.lockDurationInDays.toUByte(),
        )
        val depositPda = PublicKey.deriveDepositAccount(
            vm = vmAccount.publicKey,
            depositor = owner,
        )
        val expected = PublicKey.deriveAssociatedAccount(
            owner = depositPda.publicKey,
            mint = toMintAddress,
        ).publicKey
        assertEquals(expected, ix.accounts[6].publicKey)
    }

    @Test
    fun `swap instruction feeRecipientTokenAccount is poolFeeRecipient's from_mint ATA`() {
        val ix = buildInstructions()[4]
        val expected = PublicKey.deriveAssociatedAccount(
            owner = poolFeeRecipient,
            mint = fromMintAddress,
        ).publicKey
        assertEquals(expected, ix.accounts[7].publicKey)
    }

    @Test
    fun `swap instruction feeRecipient matches server parameter`() {
        val ix = buildInstructions()[4]
        assertEquals(poolFeeRecipient, ix.accounts[8].publicKey)
    }

    @Test
    fun `swap instruction has correct mints`() {
        val ix = buildInstructions()[4]
        assertEquals(fromMintAddress, ix.accounts[9].publicKey)
        assertEquals(toMintAddress, ix.accounts[10].publicKey)
    }

    @Test
    fun `swap instruction user is owner and is signer`() {
        val ix = buildInstructions()[4]
        assertEquals(owner, ix.accounts[11].publicKey)
        assertTrue(ix.accounts[11].isSigner)
    }

    @Test
    fun `swap instruction has correct whitelist PDA`() {
        val ix = buildInstructions()[4]
        val expectedWhitelist = PublicKey.deriveCoinbaseWhitelistAddress().publicKey
        assertEquals(expectedWhitelist, ix.accounts[12].publicKey)
    }

    // --- CreateIdempotent and Swap destination linkage ---

    @Test
    fun `CreateIdempotent ATA matches swap instruction toTokenAccount`() {
        val instructions = buildInstructions()
        val createIx = instructions[3]
        val swapIx = instructions[4]
        // CreateIdempotent creates ATA for (owner=depositPda, mint=toMint)
        // Swap toTokenAccount should be that same ATA
        // CreateIdempotent account[1] is the ATA address
        assertEquals(createIx.accounts[1].publicKey, swapIx.accounts[6].publicKey)
    }
}
