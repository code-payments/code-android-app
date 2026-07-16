package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapRoute
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.transactions.FundSwapPool
import com.getcode.opencode.solana.swap.buildExistingCurrencyBuyInstructions
import com.getcode.opencode.solana.swap.buildNewCurrencyBuyInstructions
import com.getcode.opencode.solana.swap.buildSellInstructions
import com.getcode.opencode.solana.swap.buildTreasuryFundedNewCurrencyBuyInstructions
import com.getcode.opencode.solana.swap.buildStablecoinSwapperInstructions
import com.getcode.opencode.solana.swap.buildStatelessSwapInstructions
import com.getcode.opencode.solana.swap.buildUsdcDepositInstructions
import com.getcode.opencode.solana.swap.buildUsdcToUsdfSwapInstructions
import com.getcode.opencode.solana.swap.buildUsdfDepositInstructions
import com.getcode.opencode.model.transactions.StatelessSwapServerParameters
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey

object TransactionBuilder {
    /**
     * Constructs a Solana transaction for performing a token swap.
     *
     * This function generates a V0 transaction that executes a swap between USDC and another
     * supported token. It handles both "Buy" (USDC -> Token) and "Sell" (Token -> USDC) directions
     * by generating the appropriate instruction sets based on the [route] parameter.
     *
     * @param response The server parameters required for the swap, containing payer information,
     *                 lookup tables (ALTs), nonce, and a blockhash.
     * @param authority The public key of the user authorizing the swap (the wallet owner).
     * @param swapAuthority The public key of the temporary swap authority derived from the nonce.
     * @param route The route of the swap (Buy or Sell) and the target/source mint involved.
     * @param amount The amount of tokens to swap (in the source currency's smallest unit).
     * @param minOutput The minimum acceptable amount of output tokens to receive (slippage protection).
     *                  Defaults to 0.
     * @return A constructed [SolanaTransaction] (V0) ready to be signed and submitted to the network.
     */
    fun swap(
        response: StatefulSwapResponseServerParameters.ExistingCurrency,
        authority: PublicKey,
        swapAuthority: PublicKey,
        route: SwapRoute,
        amount: Long,
        minOutput: Long = 0,
    ): SolanaTransaction {
        val coreMint = Token.usdf

        val instructions = when (route) {
            is SwapRoute.Buy -> buildExistingCurrencyBuyInstructions(
                serverParameters = response,
                nonce = response.nonce,
                authority = authority,
                swapAuthority = swapAuthority,
                coreMintMetadata = coreMint,
                targetMintMetadata = route.mint,
                amount = amount,
                minOutput = minOutput,
            )

            is SwapRoute.Sell -> buildSellInstructions(
                serverParameters = response,
                nonce = response.nonce,
                authority = authority,
                swapAuthority = swapAuthority,
                sourceMintMetadata = route.mint,
                coreMintMetadata = coreMint,
                amount = amount,
                minOutput = minOutput,
            )

            SwapRoute.WithdrawUsdc -> throw IllegalArgumentException("Withdraw USDC should not be used with ExistingCurrency params")

            // Cross-currency (currency -> currency) swaps between two existing currencies map to the
            // server's buy+sell handler, whose client-side instruction builder is not implemented yet.
            is SwapRoute.CrossCurrency -> throw NotImplementedError("Cross-currency existing currency swaps are not supported client-side yet")
        }

        return SolanaTransaction.newV0Instance(
            payer = response.payer,
            recentBlockhash = response.blockhash,
            addressLookupTables = response.alts,
            instructions = instructions,
        )
    }

    /**
     * Constructs a Solana transaction for performing a stablecoin swap against the Coinbase
     * Stable Swapper program.
     *
     * This function generates a V0 transaction that swaps between the core mint and a stablecoin
     * (e.g., USDC) via the Coinbase Stable Swapper liquidity pool. The transaction opens the
     * necessary ATAs, transfers tokens from the VM swap ATA to the swap authority, executes
     * the swap through the Coinbase Stable Swapper program, and closes temporary accounts.
     *
     * @param response The server parameters for the stablecoin swap, containing payer, nonce,
     *                 blockhash, ALTs, fee destination, and the pool fee recipient.
     * @param authority The public key of the user authorizing the swap (the wallet owner).
     * @param swapAuthority The public key of a random one-time use account that signs the swap.
     * @param route The route of the swap (Buy or Sell) and the target/source mint involved.
     * @param amount The amount of tokens to swap from the source mint (in quarks).
     * @param minOutput The minimum acceptable amount of output tokens to receive (slippage protection).
     *                  Defaults to 0.
     * @return A constructed [SolanaTransaction] (V0) ready to be signed and submitted to the network.
     */
    fun stablecoinSwap(
        response: StatefulSwapResponseServerParameters.Stablecoin,
        authority: PublicKey,
        swapAuthority: PublicKey,
        destinationOwner: PublicKey,
        route: SwapRoute,
        amount: Long,
        feeAmount: Long = 0,
        minOutput: Long = 0,
    ): SolanaTransaction {
        val instructions = buildStablecoinSwapperInstructions(
            serverParameters = response,
            nonce = response.nonce,
            authority = authority,
            swapAuthority = swapAuthority,
            destinationOwner = destinationOwner,
            fromMintMetadata = route.sourceMint,
            toMintMetadata = route.destinationMint,
            amount = amount,
            feeAmount = feeAmount,
            minOutput = minOutput,
        )

        return SolanaTransaction.newV0Instance(
            payer = response.payer,
            recentBlockhash = response.blockhash,
            addressLookupTables = response.alts,
            instructions = instructions,
        )
    }

    /**
     * Constructs a Solana transaction for buying a new currency against the Reserve contract.
     *
     * This is used exclusively by currency creators to initialize a new currency and perform
     * the initial buy. The transaction initializes the currency, its liquidity pool, and VM,
     * then transfers the core mint from the VM swap ATA to the owner and executes a limited
     * buy depositing the new currency tokens into the VM.
     *
     * @param response The server parameters for the new currency flow, containing payer, nonce,
     *                 blockhash, ALTs, currency metadata (name, symbol, seed), and fee destination.
     * @param authority The public key of the currency creator (owner account) authorizing the buy.
     * @param coreMintMetadata Metadata for the core mint used as the source currency for the buy.
     * @param amount The amount of core mint tokens to spend on the buy (in quarks).
     * @param feeAmount The fee amount to pay for this swap, or null if no fee (defaults to 0).
     * @return A constructed [SolanaTransaction] (V0) ready to be signed and submitted to the network.
     */
    fun buyNewCurrency(
        response: StatefulSwapResponseServerParameters.NewCurrency,
        authority: PublicKey,
        sourceMintMetadata: MintMetadata,
        coreMintMetadata: MintMetadata,
        amount: Long,
        feeAmount: Long?,
    ): SolanaTransaction {
        // When the server provides a treasury the initial buy is funded from a non-core source mint
        // (cross-currency), which uses a distinct instruction layout. Otherwise the buy is paid for
        // directly with the core mint.
        val instructions = if (response.treasury != null) {
            buildTreasuryFundedNewCurrencyBuyInstructions(
                serverParameters = response,
                nonce = response.nonce,
                authority = authority,
                sourceMintMetadata = sourceMintMetadata,
                coreMintMetadata = coreMintMetadata,
                swapAmount = amount,
                feeAmount = feeAmount ?: 0,
            )
        } else {
            buildNewCurrencyBuyInstructions(
                serverParameters = response,
                nonce = response.nonce,
                authority = authority,
                coreMintMetadata = coreMintMetadata,
                amount = amount,
                feeAmount = feeAmount ?: 0,
            )
        }

        return SolanaTransaction.newV0Instance(
            payer = response.payer,
            recentBlockhash = response.blockhash,
            addressLookupTables = response.alts,
            instructions = instructions,
        )
    }

    /**
     * Constructs a Solana transaction that swaps USDC into USDF and deposits the result into
     * the owner's USDF VM swap PDA.
     *
     * This is used to fund a swap by converting externally-held USDC into USDF (the core mint)
     * via the USDF liquidity pool. The transaction opens the necessary ATAs for USDF and USDC,
     * executes the USDF program swap (USDC -> USDF), then transfers the resulting USDF into the
     * owner's USDF swap PDA for use in a subsequent stateful swap.
     *
     * @param owner The public key of the wallet owner whose USDF swap PDA will receive the funds.
     * @param sender The public key of the account paying for and signing the transaction.
     * @param amount The amount of USDC to swap into USDF (in quarks).
     * @param pool The pool to use for the swap — either USDF liquidity pool or CoinbaseStableSwapper.
     * @param swapId A unique identifier for this swap, included as a memo in the transaction.
     * @param blockhash A recent blockhash for the transaction, or null.
     * @return A constructed [SolanaTransaction] (V0) ready to be signed and submitted to the network.
     */
    fun usdcFundSwap(
        owner: PublicKey,
        sender: PublicKey,
        amount: Long,
        pool: FundSwapPool,
        swapId: SwapId,
        blockhash: Hash?,
    ): SolanaTransaction {
        val instructions = buildUsdcToUsdfSwapInstructions(
            sender = sender,
            owner = owner,
            amount = amount,
            pool = pool,
            swapId = swapId,
        )

        return SolanaTransaction.newV0Instance(
            payer = sender,
            recentBlockhash = blockhash,
            addressLookupTables = emptyList(),
            instructions = instructions,
        )
    }

    /**
     * Constructs a Solana transaction that transfers USDC from an external wallet
     * (e.g. Phantom) to the owner's USDC ATA. The server's auto-sweep will detect
     * the deposit and convert it to USDF.
     *
     * @param owner The authority public key of the app wallet owner.
     * @param sender The public key of the external wallet (Phantom).
     * @param amount The amount of USDC to transfer (in quarks).
     * @param blockhash A recent blockhash for the transaction.
     * @return A constructed [SolanaTransaction] (V0) ready to be signed.
     */
    fun usdcDeposit(
        owner: PublicKey,
        sender: PublicKey,
        amount: Long,
        blockhash: Hash?,
    ): SolanaTransaction {
        val instructions = buildUsdcDepositInstructions(
            sender = sender,
            owner = owner,
            amount = amount,
        )

        return SolanaTransaction.newV0Instance(
            payer = sender,
            recentBlockhash = blockhash,
            addressLookupTables = emptyList(),
            instructions = instructions,
        )
    }

    /**
     * Constructs a Solana transaction that swaps USDC→USDF via the Coinbase
     * Stable Swapper and deposits the USDF directly into the owner's USDF VM
     * deposit PDA ATA.
     *
     * Designed for Phantom-signed deposits: the sender (Phantom wallet) pays
     * for compute and ATA rent. The Geyser watcher detects USDF in the deposit
     * PDA ATA and sweeps it into the VM — no server-side USDC sweep needed.
     *
     * @param owner The public key of the app wallet owner.
     * @param sender The public key of the external wallet (Phantom).
     * @param amount The amount of USDC to swap into USDF (in quarks).
     * @param feeRecipient The Coinbase pool fee recipient address.
     * @param blockhash A recent blockhash for the transaction.
     * @return A constructed [SolanaTransaction] (V0) ready to be signed.
     */
    fun usdfDeposit(
        owner: PublicKey,
        sender: PublicKey,
        amount: Long,
        feeRecipient: PublicKey,
        blockhash: Hash?,
    ): SolanaTransaction {
        val instructions = buildUsdfDepositInstructions(
            sender = sender,
            owner = owner,
            amount = amount,
            feeRecipient = feeRecipient,
        )

        return SolanaTransaction.newV0Instance(
            payer = sender,
            recentBlockhash = blockhash,
            addressLookupTables = emptyList(),
            instructions = instructions,
        )
    }

    /**
     * Constructs a Solana transaction for a stateless USDC deposit sweep via the
     * Coinbase Stable Swapper program.
     *
     * Swaps USDC from the owner's ATA into USDF, depositing the result into
     * the owner's USDF VM Deposit ATA (monitored by Geyser).
     *
     * @param response Server parameters for the stateless swap.
     * @param owner The public key of the wallet owner.
     * @param fromMint Metadata for the source mint (USDC).
     * @param toMint Metadata for the destination mint (USDF).
     * @param amount The amount to swap (in quarks).
     * @return A constructed [SolanaTransaction] (V0) ready to be signed.
     */
    fun statelessSwap(
        response: StatelessSwapServerParameters,
        owner: PublicKey,
        fromMint: MintMetadata,
        toMint: MintMetadata,
        amount: Long,
    ): SolanaTransaction {
        val instructions = buildStatelessSwapInstructions(
            serverParameters = response,
            owner = owner,
            fromMint = fromMint,
            toMint = toMint,
            amount = amount,
        )

        return SolanaTransaction.newV0Instance(
            payer = response.payer,
            recentBlockhash = response.blockhash,
            addressLookupTables = response.alts,
            instructions = instructions,
        )
    }
}