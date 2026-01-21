package com.getcode.opencode.solana.swap

import com.codeinc.opencode.gen.common.v1.blockhash
import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram
import com.getcode.opencode.internal.solana.programs.MemoProgram
import com.getcode.opencode.internal.solana.programs.SystemProgram
import com.getcode.opencode.internal.solana.programs.TokenProgram
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.opencode.tests.generateRandomPublicKeyForTest
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock

class SwapInstructionsTest {

    // --- Mocks & Constants ---
    private val mockPayer = generateRandomPublicKeyForTest()
    private val mockNonce = generateRandomPublicKeyForTest()
    private val mockAuthority = generateRandomPublicKeyForTest() // The user/owner
    private val mockSwapAuthority = generateRandomPublicKeyForTest() // The temporary swap authority
    private val mockRecentBlockhash = generateRandomPublicKeyForTest()

    // Mock VMs
    private val coreVmMetadata = VmMetadata(
        authority = vmAuthority,
        vm = generateRandomPublicKeyForTest(),
        lockDurationInDays = 21,
    )
    private val targetVmMetadata = VmMetadata(
        authority = generateRandomPublicKeyForTest(),
        vm = generateRandomPublicKeyForTest(),
        lockDurationInDays = 21,
    )

    // Mock Launchpad (Required for Target/Source Mints)
    private val mockLaunchpadMetadata = LaunchpadMetadata(
        currencyConfig = generateRandomPublicKeyForTest(),
        liquidityPool = generateRandomPublicKeyForTest(),
        seed = generateRandomPublicKeyForTest(),
        authority = vmAuthority,
        mintVault = generateRandomPublicKeyForTest(),
        coreMintVault = generateRandomPublicKeyForTest(),
        currentCirculatingSupplyQuarks = 0,
        sellFeeBps = 0
    )

    // Mock Mints
    private val coreMint = MintMetadata.usdf

    private val targetMint = MintMetadata(
        address = PublicKey.generate(),
        vmMetadata = targetVmMetadata,
        launchpadMetadata = mockLaunchpadMetadata,
        decimals = 6,
        name = "Test Token",
        symbol = "TTOK",
        description = "",
        imageUrl = "",
        billCustomizations = null,
        createdAt = Clock.System.now(),
    )

    // Mock Server Response (Stateless for simplicity)
    private val mockServerParams = SwapResponseServerParameters(
        payer = mockPayer,
        blockhash = mockRecentBlockhash,
        nonce = mockNonce,
        computeUnitLimit = 500_000,
        computeUnitPrice = 1_000,
        memoValue = "test_swap_memo",
        memoryIndex = 1,
        memoryAccount = PublicKey.generate(),
        alts = emptyList()
    )

    @Test
    fun testBuildBuyInstructionsSequence() {
        val amount = 100_000L
        val minOutput = 95_000L

        val instructions = buildBuyInstructions(
            serverParameters = mockServerParams,
            nonce = mockNonce,
            authority = mockAuthority,
            swapAuthority = mockSwapAuthority,
            coreMintMetadata = coreMint,
            targetMintMetadata = targetMint,
            amount = amount,
            minOutput = minOutput
        )

        // Expected Sequence:
        // 1. Advance Nonce
        // 2. Compute Limit
        // 3. Compute Price
        // 4. Memo
        // 5. Create Temp Core ATA (Idempotent)
        // 6. VM Transfer (Core VM -> Temp Core ATA)
        // 7. Currency Creator Buy
        // 8. Close Temp Core ATA
        // 9. Close Swap Account If Empty

        assertEquals("Should generate 9 instructions", 9, instructions.size)

        // 1. Advance Nonce
        assertEquals(SystemProgram.address, instructions[0].program)

        // 2. Compute Budget (Limit)
        assertEquals(
            ComputeBudgetProgram.address,
            instructions[1].program
        )

        // 3. Compute Budget (Price)
        assertEquals(
            ComputeBudgetProgram.address,
            instructions[2].program,
        )

        // 4. Memo
        assertEquals(
            MemoProgram.address,
            instructions[3].program,
        )

        // Basic check for memo content encoding (length > 0)
        assertTrue(instructions[3].data.isNotEmpty())

        // 5. Associated Token Create (Idempotent)
        assertEquals(
            AssociatedTokenProgram.address,
            instructions[4].program,
        )

        // 6. VM Transfer
        // VM Program ID check (mock check assuming your implementation details, verifying generic order)
        // We verify the authority matches the input authority
        assertEquals(coreVmMetadata.authority, instructions[5].accounts[0].publicKey)

        // 7. Currency Creator Buy
        // Check input accounts logic
        // Index 0 in Buy is the buyer (swapAuthority)
        assertEquals(mockSwapAuthority, instructions[6].accounts[0].publicKey)
        // Index 3 is Target Mint
        assertEquals(targetMint.address, instructions[6].accounts[3].publicKey)

        // 8. Close Temp Account
        assertEquals(
            TokenProgram.address,
            instructions[7].program,
        )
        // The account being closed should be the temp ATA.
        // We can re-derive the expected address to verify
        val expectedTempAta =
            PublicKey.deriveAssociatedAccount(mockSwapAuthority, coreMint.address).publicKey
        assertEquals(expectedTempAta, instructions[7].accounts[0].publicKey)

        // 9. Close Swap Account If Empty
        // VM Program Check
        val coreTimelock = coreMint.timelockSwapAccounts(mockAuthority)
        assertEquals(coreTimelock.ata.publicKey, instructions[8].accounts[4].publicKey)
    }

    @Test
    fun testBuildSellInstructionsSequence() {
        val amount = 500L
        val minOutput = 450L

        // For sell, "targetMint" in the variable name acts as the source being sold
        // "coreMint" is what we are buying back into.

        val instructions = buildSellInstructions(
            serverParameters = mockServerParams,
            nonce = mockNonce,
            authority = mockAuthority,
            swapAuthority = mockSwapAuthority,
            sourceMintMetadata = targetMint, // Selling the target mint
            coreMintMetadata = coreMint,     // Receiving core mint
            amount = amount,
            minOutput = minOutput
        )

        // Expected Sequence:
        // 1. Advance Nonce
        // 2. Compute Limit
        // 3. Compute Price
        // 4. Memo
        // 5. Create Temp Source ATA (Idempotent)
        // 6. VM Transfer (Source VM -> Temp Source ATA)
        // 7. Currency Creator Sell
        // 8. Close Temp Source ATA
        // 9. Close Source Swap Account If Empty

        assertEquals("Should generate 9 instructions", 9, instructions.size)

        // 1. Advance Nonce
        assertEquals(SystemProgram.address, instructions[0].program)

        // 2. Compute Budget (Limit)
        assertEquals(
            ComputeBudgetProgram.address,
            instructions[1].program
        )

        // 3. Compute Budget (Price)
        assertEquals(
            ComputeBudgetProgram.address,
            instructions[2].program,
        )

        // 4. Memo
        assertEquals(
            MemoProgram.address,
            instructions[3].program,
        )

        // 5. Associated Token Create (Idempotent) - For Source Mint
        assertEquals(
            AssociatedTokenProgram.address,
            instructions[4].program,
        )
        // Verify it is creating account for Source Mint
        // Index 1 in CreateIdempotent is the Associated Account address, Index 3 is Mint
        assertEquals(targetMint.address, instructions[4].accounts[3].publicKey)

        // 6. VM Transfer
        // Should be transferring FROM Source VM
        assertEquals(targetVmMetadata.authority, instructions[5].accounts[0].publicKey)

        // 7. Currency Creator Sell
        // Check program specific inputs
        // Index 0 is Seller (swapAuthority)
        assertEquals(mockSwapAuthority, instructions[6].accounts[0].publicKey)
        // Index 3 is Target Mint (the one being sold)
        assertEquals(targetMint.address, instructions[6].accounts[3].publicKey)

        // 8. Close Temp Account
        // Token Program. Should close Source Mint Temp ATA
        val expectedTempSourceAta =
            PublicKey.deriveAssociatedAccount(mockSwapAuthority, targetMint.address).publicKey
        assertEquals(expectedTempSourceAta, instructions[7].accounts[0].publicKey)

        // 9. Close Swap Account If Empty
        // Should close Source VM Swap ATA
        val sourceTimelock = targetMint.timelockSwapAccounts(mockAuthority)
        assertEquals(sourceTimelock.ata.publicKey, instructions[8].accounts[4].publicKey)
    }

    @Test(expected = IllegalStateException::class)
    fun testBuildBuyInstructionsThrowsIfMissingLaunchpad() {
        val badTargetMint = MintMetadata(
            address = PublicKey.generate(),
            vmMetadata = targetVmMetadata,
            decimals = 6,
            launchpadMetadata = null, // missing
            name = "Test Token",
            symbol = "TTOK",
            description = "",
            imageUrl = "",
            billCustomizations = null,
            createdAt = Clock.System.now(),
        )

        buildBuyInstructions(
            serverParameters = mockServerParams,
            nonce = mockNonce,
            authority = mockAuthority,
            swapAuthority = mockSwapAuthority,
            coreMintMetadata = coreMint,
            targetMintMetadata = badTargetMint,
            amount = 100,
            minOutput = 90
        )
    }

    @Test(expected = IllegalStateException::class)
    fun testBuildSellInstructionsThrowsIfMissingLaunchpad() {
        val badSourceMint = MintMetadata(
            address = PublicKey.generate(),
            vmMetadata = targetVmMetadata,
            launchpadMetadata = null, // Missing
            name = "Test Token",
            symbol = "TTOK",
            description = "",
            imageUrl = "",
            billCustomizations = null,
            createdAt = Clock.System.now(),
            decimals = 6,
        )

        buildSellInstructions(
            serverParameters = mockServerParams,
            nonce = mockNonce,
            authority = mockAuthority,
            swapAuthority = mockSwapAuthority,
            sourceMintMetadata = badSourceMint,
            coreMintMetadata = coreMint,
            amount = 100,
            minOutput = 90
        )
    }
}
