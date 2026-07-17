package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.deriveCurrencyConfigAddress
import com.getcode.opencode.internal.solana.extensions.deriveCurrencyMintAddress
import com.getcode.opencode.internal.solana.extensions.deriveDepositAccount
import com.getcode.opencode.internal.solana.extensions.deriveLiquidityPoolAddress
import com.getcode.opencode.internal.solana.extensions.deriveVaultAddress
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.extensions.deriveVmOmnibusAddress
import com.getcode.opencode.internal.solana.extensions.extractServerParameters
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram_BuyTokens
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram_InitializeCurrency
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram_InitializePool
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram_SellTokens
import com.getcode.opencode.internal.solana.programs.SystemProgram_AdvanceNonce
import com.getcode.opencode.internal.solana.programs.TokenProgram_CloseAccount
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram_InitVm
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram_TransferForSwapWithFee
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.PublicKey

/**
 * Builds the list of instructions required to create a new currency and buy initial tokens.
 *
 * This transaction flow performs the following:
 * 1.  Advances the nonce for transaction validity.
 * 2.  Sets compute unit limits and price.
 * 3.  Initializes the currency via the Currency Creator program.
 * 4.  Initializes the liquidity pool for the new currency.
 * 5.  Initializes the VM for the new currency's mint.
 * 6.  Creates an ATA for the core mint owned by the authority.
 * 7.  Creates an ATA for the new mint on the VM deposit PDA.
 * 8.  Transfers core currency from the user's VM swap account to the temporary ATA.
 * 9.  Buys tokens on the new currency's pool, depositing into the VM deposit ATA.
 * 10. Closes the temporary core mint ATA.
 *
 * @param serverParameters Parameters provided by the server for the new currency swap.
 * @param nonce The nonce account to use for the transaction.
 * @param authority The owner/buyer/swapAuthority (all the same for new currency flow).
 * @param coreMintMetadata Metadata for the core currency (source).
 * @param amount The amount of core currency to spend.
 * @return A list of [Instruction]s to execute the new currency creation and initial buy.
 */
internal fun buildNewCurrencyBuyInstructions(
    serverParameters: StatefulSwapResponseServerParameters.NewCurrency,
    nonce: PublicKey,
    authority: PublicKey,
    coreMintMetadata: MintMetadata,
    amount: Long,
    feeAmount: Long,
): List<Instruction> {
    val serverParams = extractServerParameters(serverParameters)

    // Derive all PDAs for the new currency
    val mintDerived = PublicKey.deriveCurrencyMintAddress(
        authority = serverParams.authority,
        name = serverParams.name,
        seed = serverParams.seed,
    )
    val targetMint = mintDerived.publicKey
    val mintBump = mintDerived.bump

    val currencyConfigDerived = PublicKey.deriveCurrencyConfigAddress(targetMint)
    val currencyConfig = currencyConfigDerived.publicKey
    val configBump = currencyConfigDerived.bump

    val poolDerived = PublicKey.deriveLiquidityPoolAddress(currencyConfig)
    val pool = poolDerived.publicKey
    val poolBump = poolDerived.bump

    val vaultTargetDerived = PublicKey.deriveVaultAddress(pool, targetMint)
    val vaultTarget = vaultTargetDerived.publicKey
    val vaultTargetBump = vaultTargetDerived.bump

    val vaultBaseDerived = PublicKey.deriveVaultAddress(pool, coreMintMetadata.address)
    val vaultBase = vaultBaseDerived.publicKey
    val vaultBaseBump = vaultBaseDerived.bump

    val lockDuration = serverParams.vmLockDurationInDays
    val vmDerived = PublicKey.deriveVirtualMachineAccount(
        mint = targetMint,
        authority = serverParams.authority,
        lockout = lockDuration.toUByte(),
    )
    val vm = vmDerived.publicKey
    val vmBump = vmDerived.bump

    val vmOmnibusDerived = PublicKey.deriveVmOmnibusAddress(vm)
    val vmOmnibus = vmOmnibusDerived.publicKey
    val vmOmnibusBump = vmOmnibusDerived.bump

    val depositPda = PublicKey.deriveDepositAccount(vm, authority)

    val coreTimelockAccounts = coreMintMetadata.timelockSwapAccounts(authority)

    // ATAs
    val createTemporaryCoreMintAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.authority,
        owner = authority,
        mint = coreMintMetadata.address,
    )

    val createVmDepositAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.authority,
        owner = depositPda.publicKey,
        mint = targetMint,
    )

    return buildList {
        // 1. System::AdvanceNonce
        add(SystemProgram_AdvanceNonce(nonce, serverParams.payer).instruction())

        // 2. ComputeBudget::SetComputeUnitLimit
        add(ComputeBudgetProgram_SetComputeUnitLimit(units = serverParams.computeUnitLimit).instruction())

        // 3. ComputeBudget::SetComputeUnitPrice
        add(ComputeBudgetProgram_SetComputeUnitPrice(microLamports = serverParams.computeUnitPrice).instruction())

        // 4. Reserve::InitializeCurrency
        add(
            CurrencyCreatorProgram_InitializeCurrency(
                authority = serverParams.authority,
                mint = targetMint,
                currency = currencyConfig,
                name = serverParams.name,
                symbol = serverParams.symbol,
                seed = serverParams.seed,
                bump = configBump,
                mintBump = mintBump,
            ).instruction()
        )

        // 5. Reserve::InitializePool
        add(
            CurrencyCreatorProgram_InitializePool(
                authority = serverParams.authority,
                currency = currencyConfig,
                targetMint = targetMint,
                baseMint = coreMintMetadata.address,
                pool = pool,
                vaultTarget = vaultTarget,
                vaultBase = vaultBase,
                sellFee = serverParams.sellFeeBps,
                bump = poolBump,
                vaultTargetBump = vaultTargetBump,
                vaultBaseBump = vaultBaseBump,
            ).instruction()
        )

        // 6. VM::InitializeVm
        add(
            VirtualMachineProgram_InitVm(
                vmAuthority = serverParams.authority,
                vm = vm,
                vmOmnibus = vmOmnibus,
                mint = targetMint,
                lockDuration = lockDuration,
                vmBump = vmBump,
                vmOmnibusBump = vmOmnibusBump,
            ).instruction()
        )

        // 7. AssociatedTokenAccount::CreateIdempotent (open Core Mint ATA owned by authority)
        add(createTemporaryCoreMintAta.instruction())

        // 8. AssociatedTokenAccount::CreateIdempotent (open target mint ATA on VM deposit PDA)
        add(createVmDepositAta.instruction())

        // 9. VM::TransferForSwap (Core Mint VM swap ATA -> temporary Core Mint ATA)
        add(
            VirtualMachineProgram_TransferForSwapWithFee(
                vmAuthority = coreMintMetadata.vmMetadata.authority,
                vm = coreMintMetadata.vmMetadata.vm,
                swapper = authority,
                swapPda = coreTimelockAccounts.pda.publicKey,
                swapAta = coreTimelockAccounts.ata.publicKey,
                destination = createTemporaryCoreMintAta.address,
                feeDestination = serverParams.feeDestination,
                swapAmount = amount,
                feeAmount = feeAmount,
                bump = coreTimelockAccounts.pda.bump,
            ).instruction()
        )

        // 10. Reserve::BuyTokens
        add(
            CurrencyCreatorProgram_BuyTokens(
                inAmount = amount,
                minOutAmount = 0,
                buyer = authority,
                pool = pool,
                targetMint = targetMint,
                baseMint = coreMintMetadata.address,
                vaultTarget = vaultTarget,
                vaultBase = vaultBase,
                buyerTarget = createVmDepositAta.address,
                buyerBase = createTemporaryCoreMintAta.address,
            ).instruction()
        )

        // 11. Token::CloseAccount (closes temporary Core Mint ATA)
        add(
            TokenProgram_CloseAccount(
                account = createTemporaryCoreMintAta.address,
                destination = serverParams.authority,
                owner = authority,
            ).instruction()
        )
    }
}

/**
 * Builds the list of instructions for the treasury-funded new currency purchase flow, used when
 * the initial buy for a new currency is funded from a source mint other than the core mint (i.e.
 * a cross-currency purchase). This mirrors the server's
 * `ReserveTreasuryFundedCreateAndBuySwapHandler`.
 *
 * Unlike [buildNewCurrencyBuyInstructions], the destination currency, its VM, and the buyer's
 * destination VM deposit ATA are initialized in a prior transaction, so they are only referenced
 * here. The full swap + fee amount of the source mint is collected into a server-controlled
 * treasury, sold for the core mint (proceeds routed to the fee destination), and the new currency
 * is then bought using the treasury's own core mint holdings for a pre-coordinated
 * [StatefulSwapResponseServerParameters.NewCurrency.treasuryPurchaseAmount]. The treasury signs the
 * sell/buy instructions server-side; the client only signs as the swapper.
 *
 * Instruction format (matches the server's "all other cases" reserve new currency layout):
 * 1. System::AdvanceNonce
 * 2. ComputeBudget::SetComputeUnitLimit
 * 3. ComputeBudget::SetComputeUnitPrice
 * 4. AssociatedTokenAccount::CreateIdempotent (open treasury's from_mint ATA)
 * 5. VM::TransferForSwapWithFee (from_mint VM swap ATA -> treasury's from_mint ATA (swap + fee))
 * 6. Reserve::SellTokens (sell the full swap + fee amount, core mint proceeds -> fee destination)
 * 7. Reserve::BuyTokens (buy funded by the treasury's core mint ATA -> to_mint VM deposit ATA)
 *
 * @param serverParameters Parameters provided by the server for the new currency flow. Must carry a
 *                         non-null [StatefulSwapResponseServerParameters.NewCurrency.treasury].
 * @param nonce The nonce account to use for the transaction.
 * @param authority The currency creator / buyer (owner == swapAuthority for the new currency flow).
 * @param sourceMintMetadata Metadata for the source (from) mint being sold to fund the purchase.
 * @param coreMintMetadata Metadata for the core mint (the reserve base currency).
 * @param swapAmount The amount of the source mint to swap (in quarks).
 * @param feeAmount The fee amount of the source mint to collect (in quarks).
 * @return A list of [Instruction]s to execute the treasury-funded new currency buy.
 */
internal fun buildTreasuryFundedNewCurrencyBuyInstructions(
    serverParameters: StatefulSwapResponseServerParameters.NewCurrency,
    nonce: PublicKey,
    authority: PublicKey,
    sourceMintMetadata: MintMetadata,
    coreMintMetadata: MintMetadata,
    swapAmount: Long,
    feeAmount: Long,
): List<Instruction> {
    val serverParams = extractServerParameters(serverParameters)

    val treasury = serverParams.treasury
        ?: throw IllegalStateException("treasury is required for the treasury-funded new currency buy")
    val purchaseCoreQuarks = serverParams.treasuryPurchaseAmount

    val sourceLaunchpad = sourceMintMetadata.launchpadMetadata
        ?: throw IllegalStateException("source mint has no launchpad metadata")
    val sourceVm = sourceMintMetadata.vmMetadata

    // Derive the destination (new) currency accounts deterministically from the server params.
    // The currency, pool, VM and deposit ATA are initialized in a prior transaction and only
    // referenced here.
    val targetMint = PublicKey.deriveCurrencyMintAddress(
        authority = serverParams.authority,
        name = serverParams.name,
        seed = serverParams.seed,
    ).publicKey

    val currencyConfig = PublicKey.deriveCurrencyConfigAddress(targetMint).publicKey
    val pool = PublicKey.deriveLiquidityPoolAddress(currencyConfig).publicKey
    val vaultTarget = PublicKey.deriveVaultAddress(pool, targetMint).publicKey
    val vaultBase = PublicKey.deriveVaultAddress(pool, coreMintMetadata.address).publicKey

    val vm = PublicKey.deriveVirtualMachineAccount(
        mint = targetMint,
        authority = serverParams.authority,
        lockout = serverParams.vmLockDurationInDays.toUByte(),
    ).publicKey
    val depositPda = PublicKey.deriveDepositAccount(vm, authority)
    val vmDepositAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.payer,
        owner = depositPda.publicKey,
        mint = targetMint,
    ).address

    // Treasury accounts. The from_mint ATA is opened by this transaction; the core mint ATA is
    // pre-funded server-side and only referenced.
    val createTreasuryFromMintAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.payer,
        owner = treasury,
        mint = sourceMintMetadata.address,
    )
    val treasuryCoreMintAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.payer,
        owner = treasury,
        mint = coreMintMetadata.address,
    ).address

    val sourceTimelockAccounts = sourceMintMetadata.timelockSwapAccounts(authority)

    return buildList {
        // 1. System::AdvanceNonce
        add(SystemProgram_AdvanceNonce(nonce, serverParams.payer).instruction())

        // 2. ComputeBudget::SetComputeUnitLimit
        add(ComputeBudgetProgram_SetComputeUnitLimit(units = serverParams.computeUnitLimit).instruction())

        // 3. ComputeBudget::SetComputeUnitPrice
        add(ComputeBudgetProgram_SetComputeUnitPrice(microLamports = serverParams.computeUnitPrice).instruction())

        // 4. AssociatedTokenAccount::CreateIdempotent (open treasury's from_mint ATA)
        add(createTreasuryFromMintAta.instruction())

        // 5. VM::TransferForSwapWithFee (from_mint VM swap ATA -> treasury's from_mint ATA (swap + fee))
        add(
            VirtualMachineProgram_TransferForSwapWithFee(
                vmAuthority = sourceVm.authority,
                vm = sourceVm.vm,
                swapper = authority,
                swapPda = sourceTimelockAccounts.pda.publicKey,
                swapAta = sourceTimelockAccounts.ata.publicKey,
                destination = createTreasuryFromMintAta.address,
                feeDestination = createTreasuryFromMintAta.address,
                swapAmount = swapAmount,
                feeAmount = feeAmount,
                bump = sourceTimelockAccounts.pda.bump,
            ).instruction()
        )

        // 6. Reserve::SellTokens (sell full swap + fee of from_mint; core mint proceeds -> fee destination)
        add(
            CurrencyCreatorProgram_SellTokens(
                inAmount = swapAmount + feeAmount,
                minOutAmount = 0,
                seller = treasury,
                pool = sourceLaunchpad.liquidityPool,
                targetMint = sourceMintMetadata.address,
                baseMint = coreMintMetadata.address,
                vaultTarget = sourceLaunchpad.mintVault,
                vaultBase = sourceLaunchpad.coreMintVault,
                sellerTarget = createTreasuryFromMintAta.address,
                sellerBase = serverParams.feeDestination,
            ).instruction()
        )

        // 7. Reserve::BuyTokens (buy funded by the treasury's core mint ATA -> to_mint VM deposit ATA)
        add(
            CurrencyCreatorProgram_BuyTokens(
                inAmount = purchaseCoreQuarks,
                minOutAmount = 0,
                buyer = treasury,
                pool = pool,
                targetMint = targetMint,
                baseMint = coreMintMetadata.address,
                vaultTarget = vaultTarget,
                vaultBase = vaultBase,
                buyerTarget = vmDepositAta,
                buyerBase = treasuryCoreMintAta,
            ).instruction()
        )
    }
}
