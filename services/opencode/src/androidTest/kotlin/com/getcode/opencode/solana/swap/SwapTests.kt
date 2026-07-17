package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.extensions.deriveCurrencyConfigAddress
import com.getcode.opencode.internal.solana.extensions.deriveCurrencyMintAddress
import com.getcode.opencode.internal.solana.extensions.deriveDepositAccount
import com.getcode.opencode.internal.solana.extensions.deriveLiquidityPoolAddress
import com.getcode.opencode.internal.solana.extensions.deriveVaultAddress
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.extensions.deriveVmOmnibusAddress
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram
import com.getcode.opencode.internal.solana.programs.MemoProgram
import com.getcode.opencode.internal.solana.programs.SystemProgram
import com.getcode.opencode.internal.solana.programs.TokenProgram
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.opencode.tests.generateRandomPublicKeyForTest
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
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

    private val mockFeeDestination = generateRandomPublicKeyForTest()

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
        sellFeeBps = 0,
        price = Fiat.MIN_VALUE,
        marketCap = Fiat.MIN_VALUE,
    )

    // Mock Mints
    private val coreMint = MintMetadata.usdf

    private val targetMint = MintMetadata(
        address = Mint(PublicKey.generate().bytes),
        vmMetadata = targetVmMetadata,
        launchpadMetadata = mockLaunchpadMetadata,
        decimals = 6,
        name = "Test Token",
        symbol = "TTOK",
        description = "",
        imageUrl = "",
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
        createdAt = Clock.System.now(),
    )

    private val destinationVmMetadata = VmMetadata(
        authority = generateRandomPublicKeyForTest(),
        vm = generateRandomPublicKeyForTest(),
        lockDurationInDays = 21,
    )

    private val destinationLaunchpadMetadata = LaunchpadMetadata(
        currencyConfig = generateRandomPublicKeyForTest(),
        liquidityPool = generateRandomPublicKeyForTest(),
        seed = generateRandomPublicKeyForTest(),
        authority = generateRandomPublicKeyForTest(),
        mintVault = generateRandomPublicKeyForTest(),
        coreMintVault = generateRandomPublicKeyForTest(),
        currentCirculatingSupplyQuarks = 0,
        sellFeeBps = 0,
        price = Fiat.MIN_VALUE,
        marketCap = Fiat.MIN_VALUE,
    )

    private val destinationMint = MintMetadata(
        address = Mint(PublicKey.generate().bytes),
        vmMetadata = destinationVmMetadata,
        launchpadMetadata = destinationLaunchpadMetadata,
        decimals = 6,
        name = "Dest Token",
        symbol = "DTOK",
        description = "",
        imageUrl = "",
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
        createdAt = Clock.System.now(),
    )

    // Mock Server Response (Stateless for simplicity)
    private val mockServerParams = StatefulSwapResponseServerParameters.ExistingCurrency(
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

        val instructions = buildExistingCurrencyBuyInstructions(
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
            address = Mint(PublicKey.generate().bytes),
            vmMetadata = targetVmMetadata,
            decimals = 6,
            launchpadMetadata = null, // missing
            name = "Test Token",
            symbol = "TTOK",
            description = "",
            imageUrl = "",
            billCustomizations = null,
            socialLinks = emptyList(),
            holderMetrics = HolderMetrics.None,
            createdAt = Clock.System.now(),
        )

        buildExistingCurrencyBuyInstructions(
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
            address = Mint(PublicKey.generate().bytes),
            vmMetadata = targetVmMetadata,
            launchpadMetadata = null, // Missing
            name = "Test Token",
            symbol = "TTOK",
            description = "",
            imageUrl = "",
            billCustomizations = null,
            socialLinks = emptyList(),
            holderMetrics = HolderMetrics.None,
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

    // --- New Currency Swap Tests ---

    private val mockNewCurrencyAuthority = generateRandomPublicKeyForTest()
    private val mockSeed = generateRandomPublicKeyForTest()

    private val mockNewCurrencyServerParams = StatefulSwapResponseServerParameters.NewCurrency(
        payer = mockPayer,
        blockhash = mockRecentBlockhash,
        nonce = mockNonce,
        computeUnitLimit = 800_000,
        computeUnitPrice = 2_000,
        memoValue = "",
        authority = mockNewCurrencyAuthority,
        name = "TestCoin",
        symbol = "TC",
        seed = mockSeed,
        sellFeeBps = 100,
        vmLockDurationInDays = 21,
        alts = emptyList(),
        feeDestination = mockFeeDestination,
        treasury = null,
        treasuryPurchaseAmount = 0,
    )

    private val mockTreasury = generateRandomPublicKeyForTest()

    private val mockTreasuryNewCurrencyServerParams = mockNewCurrencyServerParams.copy(
        treasury = mockTreasury,
        treasuryPurchaseAmount = 250_000L,
    )

    @Test
    fun testBuildNewCurrencyBuyInstructionsCount() {
        val totalAmount = 100_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // Expected Sequence (11 instructions):
        // 1. System::AdvanceNonce
        // 2. ComputeBudget::SetComputeUnitLimit
        // 3. ComputeBudget::SetComputeUnitPrice
        // 4. Reserve::InitializeCurrency
        // 5. Reserve::InitializePool
        // 6. VM::InitializeVm
        // 7. AssociatedTokenAccount::CreateIdempotent (Core Mint ATA)
        // 8. AssociatedTokenAccount::CreateIdempotent (target mint VM deposit ATA)
        // 9. VM::TransferForSwap
        // 10. Reserve::BuyTokens
        // 11. Token::CloseAccount

        assertEquals("Should generate 11 instructions", 11, instructions.size)
    }

    @Test
    fun testBuildNewCurrencyBuyInstructionPrograms() {
        val totalAmount = 100_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // 1. System::AdvanceNonce
        assertEquals(SystemProgram.address, instructions[0].program)

        // 2. ComputeBudget::SetComputeUnitLimit
        assertEquals(ComputeBudgetProgram.address, instructions[1].program)

        // 3. ComputeBudget::SetComputeUnitPrice
        assertEquals(ComputeBudgetProgram.address, instructions[2].program)

        // 4. Reserve::InitializeCurrency
        assertEquals(CurrencyCreatorProgram.address, instructions[3].program)

        // 5. Reserve::InitializePool
        assertEquals(CurrencyCreatorProgram.address, instructions[4].program)

        // 6. VM::InitializeVm
        assertEquals(VirtualMachineProgram.address, instructions[5].program)

        // 7. AssociatedTokenAccount::CreateIdempotent (Core Mint ATA)
        assertEquals(AssociatedTokenProgram.address, instructions[6].program)

        // 8. AssociatedTokenAccount::CreateIdempotent (VM deposit ATA)
        assertEquals(AssociatedTokenProgram.address, instructions[7].program)

        // 9. VM::TransferForSwap
        assertEquals(VirtualMachineProgram.address, instructions[8].program)

        // 10. Reserve::BuyTokens
        assertEquals(CurrencyCreatorProgram.address, instructions[9].program)

        // 11. Token::CloseAccount
        assertEquals(TokenProgram.address, instructions[10].program)
    }

    @Test
    fun testBuildNewCurrencyBuyInstructionAccounts() {
        val totalAmount = 100_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // Derive expected PDAs
        val mintDerived = PublicKey.deriveCurrencyMintAddress(
            mockNewCurrencyAuthority, "TestCoin", mockSeed
        )
        val derivedTargetMint = mintDerived.publicKey

        val currencyConfigDerived = PublicKey.deriveCurrencyConfigAddress(derivedTargetMint)
        val currencyConfig = currencyConfigDerived.publicKey

        val poolDerived = PublicKey.deriveLiquidityPoolAddress(currencyConfig)
        val pool = poolDerived.publicKey

        val vaultTargetDerived = PublicKey.deriveVaultAddress(pool, derivedTargetMint)
        val vaultBaseDerived = PublicKey.deriveVaultAddress(pool, coreMint.address)

        val vmDerived = PublicKey.deriveVirtualMachineAccount(
            derivedTargetMint, mockNewCurrencyAuthority, 21.toUByte()
        )
        val vm = vmDerived.publicKey

        val vmOmnibusDerived = PublicKey.deriveVmOmnibusAddress(vm)

        val depositPda = PublicKey.deriveDepositAccount(vm, mockNewCurrencyAuthority)

        // 4. InitializeCurrency: authority at index 0, mint at index 1, currency at index 2
        assertEquals(mockNewCurrencyAuthority, instructions[3].accounts[0].publicKey)
        assertTrue(instructions[3].accounts[0].isSigner)
        assertTrue(instructions[3].accounts[0].isWritable)
        assertEquals(derivedTargetMint, instructions[3].accounts[1].publicKey)
        assertEquals(currencyConfig, instructions[3].accounts[2].publicKey)

        // 5. InitializePool: authority at 0, currency at 1, targetMint at 2, baseMint at 3, pool at 4
        assertEquals(mockNewCurrencyAuthority, instructions[4].accounts[0].publicKey)
        assertEquals(currencyConfig, instructions[4].accounts[1].publicKey)
        assertEquals(derivedTargetMint, instructions[4].accounts[2].publicKey)
        assertEquals(coreMint.address, instructions[4].accounts[3].publicKey)
        assertEquals(pool, instructions[4].accounts[4].publicKey)
        assertEquals(vaultTargetDerived.publicKey, instructions[4].accounts[5].publicKey)
        assertEquals(vaultBaseDerived.publicKey, instructions[4].accounts[6].publicKey)

        // 6. InitializeVm: vmAuthority at 0, vm at 1, vmOmnibus at 2, mint at 3
        assertEquals(mockNewCurrencyAuthority, instructions[5].accounts[0].publicKey)
        assertEquals(vm, instructions[5].accounts[1].publicKey)
        assertEquals(vmOmnibusDerived.publicKey, instructions[5].accounts[2].publicKey)
        assertEquals(derivedTargetMint, instructions[5].accounts[3].publicKey)

        // 7. CreateIdempotent for core mint ATA: subsidizer=authority(0), owner mint(3)=coreMint
        assertEquals(mockNewCurrencyAuthority, instructions[6].accounts[0].publicKey)
        assertEquals(coreMint.address, instructions[6].accounts[3].publicKey)

        // 8. CreateIdempotent for VM deposit ATA: owner=depositPda, mint=targetMint
        val expectedDepositAta = PublicKey.deriveAssociatedAccount(
            depositPda.publicKey, derivedTargetMint
        ).publicKey
        assertEquals(expectedDepositAta, instructions[7].accounts[1].publicKey)
        assertEquals(derivedTargetMint, instructions[7].accounts[3].publicKey)

        // 9. VM::TransferForSwapWithFee: vmAuthority at 0 matches core VM authority
        assertEquals(coreMint.vmMetadata.authority, instructions[8].accounts[0].publicKey)
        assertEquals(coreMint.vmMetadata.vm, instructions[8].accounts[1].publicKey)
        assertEquals(8, instructions[8].accounts.size)
        // feeDestination at index 6
        assertEquals(mockFeeDestination, instructions[8].accounts[6].publicKey)
        assertTrue(instructions[8].accounts[6].isWritable)

        // 10. Reserve::BuyTokens: buyer at 0 is authority
        assertEquals(mockNewCurrencyAuthority, instructions[9].accounts[0].publicKey)
        assertTrue(instructions[9].accounts[0].isSigner)
        // pool at 1
        assertEquals(pool, instructions[9].accounts[1].publicKey)
        // targetMint at 2
        assertEquals(derivedTargetMint, instructions[9].accounts[2].publicKey)
        // baseMint at 3
        assertEquals(coreMint.address, instructions[9].accounts[3].publicKey)
        // vaultTarget at 4
        assertEquals(vaultTargetDerived.publicKey, instructions[9].accounts[4].publicKey)
        // vaultBase at 5
        assertEquals(vaultBaseDerived.publicKey, instructions[9].accounts[5].publicKey)

        // 11. Token::CloseAccount: account is the temp core ATA, destination and owner are authority
        val expectedCoreMintAta = PublicKey.deriveAssociatedAccount(
            mockNewCurrencyAuthority, coreMint.address
        ).publicKey
        assertEquals(expectedCoreMintAta, instructions[10].accounts[0].publicKey)
        assertEquals(mockNewCurrencyAuthority, instructions[10].accounts[1].publicKey)
        assertEquals(mockNewCurrencyAuthority, instructions[10].accounts[2].publicKey)
    }

    @Test
    fun testBuildTreasuryFundedNewCurrencyBuyInstructionsCount() {
        val instructions = buildTreasuryFundedNewCurrencyBuyInstructions(
            serverParameters = mockTreasuryNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            sourceMintMetadata = targetMint,
            coreMintMetadata = coreMint,
            swapAmount = 95_000L,
            feeAmount = 5_000L,
        )

        // Expected sequence (7 instructions):
        // 1. System::AdvanceNonce
        // 2. ComputeBudget::SetComputeUnitLimit
        // 3. ComputeBudget::SetComputeUnitPrice
        // 4. AssociatedTokenAccount::CreateIdempotent (treasury from_mint ATA)
        // 5. VM::TransferForSwapWithFee
        // 6. Reserve::SellTokens
        // 7. Reserve::BuyTokens
        assertEquals("Should generate 7 instructions", 7, instructions.size)
    }

    @Test
    fun testBuildTreasuryFundedNewCurrencyBuyInstructionPrograms() {
        val instructions = buildTreasuryFundedNewCurrencyBuyInstructions(
            serverParameters = mockTreasuryNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            sourceMintMetadata = targetMint,
            coreMintMetadata = coreMint,
            swapAmount = 95_000L,
            feeAmount = 5_000L,
        )

        assertEquals(SystemProgram.address, instructions[0].program)
        assertEquals(ComputeBudgetProgram.address, instructions[1].program)
        assertEquals(ComputeBudgetProgram.address, instructions[2].program)
        assertEquals(AssociatedTokenProgram.address, instructions[3].program)
        assertEquals(VirtualMachineProgram.address, instructions[4].program)
        assertEquals(CurrencyCreatorProgram.address, instructions[5].program)
        assertEquals(CurrencyCreatorProgram.address, instructions[6].program)
    }

    @Test
    fun testBuildTreasuryFundedNewCurrencyBuyInstructionAccounts() {
        val instructions = buildTreasuryFundedNewCurrencyBuyInstructions(
            serverParameters = mockTreasuryNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            sourceMintMetadata = targetMint,
            coreMintMetadata = coreMint,
            swapAmount = 95_000L,
            feeAmount = 5_000L,
        )

        val treasuryFromMintAta = PublicKey.deriveAssociatedAccount(
            mockTreasury, targetMint.address
        ).publicKey
        val treasuryCoreMintAta = PublicKey.deriveAssociatedAccount(
            mockTreasury, coreMint.address
        ).publicKey

        // 4. CreateIdempotent for the treasury's from_mint ATA: owner=treasury(1), mint=from(3)
        assertEquals(mockTreasury, instructions[3].accounts[1].publicKey)
        assertEquals(targetMint.address, instructions[3].accounts[3].publicKey)

        // 5. TransferForSwapWithFee: source VM authority(0)/vm(1); both destination(5) and
        //    feeDestination(6) route the full swap + fee into the treasury's from_mint ATA
        assertEquals(targetMint.vmMetadata.authority, instructions[4].accounts[0].publicKey)
        assertEquals(targetMint.vmMetadata.vm, instructions[4].accounts[1].publicKey)
        assertEquals(treasuryFromMintAta, instructions[4].accounts[5].publicKey)
        assertEquals(treasuryFromMintAta, instructions[4].accounts[6].publicKey)

        // 6. SellTokens: the treasury is the seller/signer; core mint proceeds route to the fee
        //    destination (seller=0, sellerTarget=6, sellerBase=7)
        assertEquals(mockTreasury, instructions[5].accounts[0].publicKey)
        assertTrue(instructions[5].accounts[0].isSigner)
        assertEquals(treasuryFromMintAta, instructions[5].accounts[6].publicKey)
        assertEquals(mockFeeDestination, instructions[5].accounts[7].publicKey)

        // 7. BuyTokens: the treasury funds the buy from its own core mint ATA (buyer=0, buyerBase=7)
        assertEquals(mockTreasury, instructions[6].accounts[0].publicKey)
        assertTrue(instructions[6].accounts[0].isSigner)
        assertEquals(treasuryCoreMintAta, instructions[6].accounts[7].publicKey)
    }

    @Test
    fun testBuildCrossCurrencyExistingSwapInstructionsCount() {
        val instructions = buildCrossCurrencyExistingSwapInstructions(
            serverParameters = mockServerParams,
            nonce = mockNonce,
            authority = mockAuthority,
            swapAuthority = mockSwapAuthority,
            fromMintMetadata = targetMint,
            toMintMetadata = destinationMint,
            coreMintMetadata = coreMint,
            amount = 100_000L,
        )

        // Expected sequence (12 instructions):
        // 1. System::AdvanceNonce
        // 2. ComputeBudget::SetComputeUnitLimit
        // 3. ComputeBudget::SetComputeUnitPrice
        // 4. Memo::Memo
        // 5. AssociatedTokenAccount::CreateIdempotent (temp core mint ATA)
        // 6. AssociatedTokenAccount::CreateIdempotent (temp source mint ATA)
        // 7. VM::TransferForSwap
        // 8. Reserve::SellTokens
        // 9. Reserve::BuyAndDepositIntoVm
        // 10. Token::CloseAccount (temp core)
        // 11. Token::CloseAccount (temp source)
        // 12. VM::CloseSwapAccountIfEmpty
        assertEquals("Should generate 12 instructions", 12, instructions.size)
    }

    @Test
    fun testBuildCrossCurrencyExistingSwapInstructionPrograms() {
        val instructions = buildCrossCurrencyExistingSwapInstructions(
            serverParameters = mockServerParams,
            nonce = mockNonce,
            authority = mockAuthority,
            swapAuthority = mockSwapAuthority,
            fromMintMetadata = targetMint,
            toMintMetadata = destinationMint,
            coreMintMetadata = coreMint,
            amount = 100_000L,
        )

        assertEquals(SystemProgram.address, instructions[0].program)
        assertEquals(ComputeBudgetProgram.address, instructions[1].program)
        assertEquals(ComputeBudgetProgram.address, instructions[2].program)
        assertEquals(MemoProgram.address, instructions[3].program)
        assertEquals(AssociatedTokenProgram.address, instructions[4].program)
        assertEquals(AssociatedTokenProgram.address, instructions[5].program)
        assertEquals(VirtualMachineProgram.address, instructions[6].program)
        assertEquals(CurrencyCreatorProgram.address, instructions[7].program)
        assertEquals(CurrencyCreatorProgram.address, instructions[8].program)
        assertEquals(TokenProgram.address, instructions[9].program)
        assertEquals(TokenProgram.address, instructions[10].program)
        assertEquals(VirtualMachineProgram.address, instructions[11].program)
    }

    @Test
    fun testBuildCrossCurrencyExistingSwapInstructionAccounts() {
        val instructions = buildCrossCurrencyExistingSwapInstructions(
            serverParameters = mockServerParams,
            nonce = mockNonce,
            authority = mockAuthority,
            swapAuthority = mockSwapAuthority,
            fromMintMetadata = targetMint,
            toMintMetadata = destinationMint,
            coreMintMetadata = coreMint,
            amount = 100_000L,
        )

        val tempCoreMintAta = PublicKey.deriveAssociatedAccount(
            mockSwapAuthority, coreMint.address
        ).publicKey
        val tempSourceMintAta = PublicKey.deriveAssociatedAccount(
            mockSwapAuthority, targetMint.address
        ).publicKey

        // 7. TransferForSwap: source VM authority(0)/vm(1); swapper(2)=owner; destination(5)=temp source ATA
        assertEquals(targetMint.vmMetadata.authority, instructions[6].accounts[0].publicKey)
        assertEquals(targetMint.vmMetadata.vm, instructions[6].accounts[1].publicKey)
        assertEquals(mockAuthority, instructions[6].accounts[2].publicKey)
        assertEquals(tempSourceMintAta, instructions[6].accounts[5].publicKey)

        // 8. SellTokens: the swap authority is the seller/signer; core proceeds land in the temp core ATA
        //    (seller=0, sellerTarget=6, sellerBase=7)
        assertEquals(mockSwapAuthority, instructions[7].accounts[0].publicKey)
        assertTrue(instructions[7].accounts[0].isSigner)
        assertEquals(targetMint.launchpadMetadata!!.liquidityPool, instructions[7].accounts[1].publicKey)
        assertEquals(tempSourceMintAta, instructions[7].accounts[6].publicKey)
        assertEquals(tempCoreMintAta, instructions[7].accounts[7].publicKey)

        // 9. BuyAndDepositIntoVm: buyer(0)=swap authority, buyerBase(6)=temp core ATA (no buyerTarget,
        //    since it deposits straight into the destination VM); vtaOwner is the owner
        assertEquals(mockSwapAuthority, instructions[8].accounts[0].publicKey)
        assertEquals(destinationMint.launchpadMetadata!!.liquidityPool, instructions[8].accounts[1].publicKey)
        assertEquals(tempCoreMintAta, instructions[8].accounts[6].publicKey)
    }

    @Test
    fun testBuildNewCurrencyBuyInstructionInitializeCurrencyData() {
        val totalAmount = 100_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // InitializeCurrency data: command(1) + name(32) + symbol(8) + seed(32) + bump(1) + mintBump(1) + padding(6) = 81 bytes
        val initCurrencyData = instructions[3].data
        assertEquals(81, initCurrencyData.size)
        assertEquals(CurrencyCreatorProgram.Command.initializeCurrency.value, initCurrencyData[0])

        // Verify name bytes (first 8 bytes of name field should be "TestCoin")
        val nameBytes = "TestCoin".toByteArray(Charsets.UTF_8)
        for (i in nameBytes.indices) {
            assertEquals(nameBytes[i], initCurrencyData[1 + i])
        }
        // Remaining name bytes should be zero-padded
        for (i in nameBytes.size until 32) {
            assertEquals(0.toByte(), initCurrencyData[1 + i])
        }

        // Verify symbol bytes (first 2 bytes should be "TC")
        val symbolBytes = "TC".toByteArray(Charsets.UTF_8)
        for (i in symbolBytes.indices) {
            assertEquals(symbolBytes[i], initCurrencyData[33 + i])
        }
        // Remaining symbol bytes should be zero-padded
        for (i in symbolBytes.size until 8) {
            assertEquals(0.toByte(), initCurrencyData[33 + i])
        }
    }

    @Test
    fun testBuildNewCurrencyBuyInstructionInitializePoolData() {
        val totalAmount = 100_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // InitializePool data: command(1) + sellFee(2) + bump(1) + vaultTargetBump(1) + vaultBaseBump(1) + padding(1) = 7 bytes
        val initPoolData = instructions[4].data
        assertEquals(7, initPoolData.size)
        assertEquals(CurrencyCreatorProgram.Command.initializePool.value, initPoolData[0])

        // sellFee = 100 bps = 0x64, 0x00 in LE
        assertEquals(0x64.toByte(), initPoolData[1])
        assertEquals(0x00.toByte(), initPoolData[2])
    }

    @Test
    fun testBuildNewCurrencyBuyInstructionInitVmData() {
        val totalAmount = 100_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // InitVm data: command(1) + lockDuration(1) + vmBump(1) + vmOmnibusBump(1) = 4 bytes
        val initVmData = instructions[5].data
        assertEquals(4, initVmData.size)
        assertEquals(VirtualMachineProgram.Command.initVm.value, initVmData[0])
        assertEquals(21.toByte(), initVmData[1]) // lockDuration = 21 days
    }

    @Test
    fun testBuildNewCurrencyBuyInstructionBuyTokensData() {
        val totalAmount = 100_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // BuyTokens data: command(1) + inAmount(8) + minOutAmount(8) = 17 bytes
        val buyData = instructions[9].data
        assertEquals(17, buyData.size)
        assertEquals(CurrencyCreatorProgram.Command.buyTokens.value, buyData[0])

        // inAmount = 95_000 = 0x18730100_00000000 in LE
        assertEquals(0x18.toByte(), buyData[1])
        assertEquals(0x73.toByte(), buyData[2])
        assertEquals(0x01.toByte(), buyData[3])
        assertEquals(0x00.toByte(), buyData[4])

        // minOutAmount = 0
        for (i in 9..16) {
            assertEquals(0.toByte(), buyData[i])
        }
    }

    @Test
    fun testBuildNewCurrencyBuyTransferForSwapWithFeeData() {
        val totalAmount = 100_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // TransferForSwapWithFee data: command(1) + swapAmount(8) + feeAmount(8) + bump(1) = 18 bytes
        val transferData = instructions[8].data
        assertEquals(18, transferData.size)

        // Command byte is transferForSwap (17)
        assertEquals(VirtualMachineProgram.Command.transferForSwap.value, transferData[0])

        // swapAmount = 95_000 at bytes [1..8] in LE
        assertEquals(0x18.toByte(), transferData[1])
        assertEquals(0x73.toByte(), transferData[2])
        assertEquals(0x01.toByte(), transferData[3])
        for (i in 4..8) assertEquals(0x00.toByte(), transferData[i])

        // feeAmount = 5_000 at bytes [9..16] in LE
        assertEquals(0x88.toByte(), transferData[9])
        assertEquals(0x13.toByte(), transferData[10])
        for (i in 11..16) assertEquals(0x00.toByte(), transferData[i])

        // bump is last byte
        assertTrue(transferData[17] in Byte.MIN_VALUE..Byte.MAX_VALUE)
    }

    @Test
    fun testNewCurrencyBuyInstructionAuthorityIsBuyer() {
        val totalAmount = 50_000L
        val fee = 5_000L
        val amount = totalAmount - fee

        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = mockNewCurrencyServerParams,
            nonce = mockNonce,
            authority = mockNewCurrencyAuthority,
            coreMintMetadata = coreMint,
            amount = amount,
            feeAmount = fee,
        )

        // In the new currency flow, authority == buyer == swapAuthority
        // InitializeCurrency authority
        assertEquals(mockNewCurrencyAuthority, instructions[3].accounts[0].publicKey)
        // InitializePool authority
        assertEquals(mockNewCurrencyAuthority, instructions[4].accounts[0].publicKey)
        // InitVm vmAuthority
        assertEquals(mockNewCurrencyAuthority, instructions[5].accounts[0].publicKey)
        // BuyTokens buyer
        assertEquals(mockNewCurrencyAuthority, instructions[9].accounts[0].publicKey)
        // CloseAccount owner
        assertEquals(mockNewCurrencyAuthority, instructions[10].accounts[2].publicKey)
    }

    @Test
    fun testNewCurrencyPdaDerivationsAreConsistent() {
        // Verify that PDA derivations chain correctly
        val mint = PublicKey.deriveCurrencyMintAddress(
            mockNewCurrencyAuthority, "TestCoin", mockSeed
        )
        val config = PublicKey.deriveCurrencyConfigAddress(mint.publicKey)
        val pool = PublicKey.deriveLiquidityPoolAddress(config.publicKey)
        val vaultTarget = PublicKey.deriveVaultAddress(pool.publicKey, mint.publicKey)
        val vaultBase = PublicKey.deriveVaultAddress(pool.publicKey, coreMint.address)

        // All bumps should be in valid range [0, 255]
        assertTrue(mint.bump in 0..255)
        assertTrue(config.bump in 0..255)
        assertTrue(pool.bump in 0..255)
        assertTrue(vaultTarget.bump in 0..255)
        assertTrue(vaultBase.bump in 0..255)

        // Vault target and vault base should be different addresses
        assertTrue(vaultTarget.publicKey != vaultBase.publicKey)

        // Deriving the same inputs should produce the same output (deterministic)
        val mint2 = PublicKey.deriveCurrencyMintAddress(
            mockNewCurrencyAuthority, "TestCoin", mockSeed
        )
        assertEquals(mint.publicKey, mint2.publicKey)
        assertEquals(mint.bump, mint2.bump)
    }
}
