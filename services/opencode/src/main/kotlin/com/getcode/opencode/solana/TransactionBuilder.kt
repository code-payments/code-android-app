package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.model.LiquidityPool
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapDirection
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.solana.swap.buildExistingCurrencyBuyInstructions
import com.getcode.opencode.solana.swap.buildNewCurrencyBuyInstructions
import com.getcode.opencode.solana.swap.buildSellInstructions
import com.getcode.opencode.solana.swap.buildUsdcToUsdfSwapInstructions
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey

object TransactionBuilder {
    /**
     * Constructs a Solana transaction for performing a token swap.
     *
     * This function generates a V0 transaction that executes a swap between USDC and another
     * supported token. It handles both "Buy" (USDC -> Token) and "Sell" (Token -> USDC) directions
     * by generating the appropriate instruction sets based on the [direction] parameter.
     *
     * @param response The server parameters required for the swap, containing payer information,
     *                 lookup tables (ALTs), nonce, and a blockhash.
     * @param authority The public key of the user authorizing the swap (the wallet owner).
     * @param swapAuthority The public key of the temporary swap authority derived from the nonce.
     * @param direction The direction of the swap (Buy or Sell) and the target/source mint involved.
     * @param amount The amount of tokens to swap (in the source currency's smallest unit).
     * @param minOutput The minimum acceptable amount of output tokens to receive (slippage protection).
     *                  Defaults to 0.
     * @return A constructed [SolanaTransaction] (V0) ready to be signed and submitted to the network.
     */
    fun swap(
        response: SwapResponseServerParameters.ExistingCurrency,
        authority: PublicKey,
        swapAuthority: PublicKey,
        direction: SwapDirection,
        amount: Long,
        minOutput: Long = 0,
    ): SolanaTransaction {
        val coreMint = Token.usdf

        val instructions = when (direction) {
            is SwapDirection.Buy -> buildExistingCurrencyBuyInstructions(
                serverParameters = response,
                nonce = response.nonce,
                authority = authority,
                swapAuthority = swapAuthority,
                coreMintMetadata = coreMint,
                targetMintMetadata = direction.mint,
                amount = amount,
                minOutput = minOutput,
            )

            is SwapDirection.Sell -> buildSellInstructions(
                serverParameters = response,
                nonce = response.nonce,
                authority = authority,
                swapAuthority = swapAuthority,
                sourceMintMetadata = direction.mint,
                coreMintMetadata = coreMint,
                amount = amount,
                minOutput = minOutput,
            )
        }

        return SolanaTransaction.newV0Instance(
            payer = response.payer,
            recentBlockhash = response.blockhash,
            addressLookupTables = response.alts,
            instructions = instructions,
        )
    }

    fun buyNewCurrency(
        response: SwapResponseServerParameters.NewCurrency,
        authority: PublicKey,
        coreMintMetadata: MintMetadata,
        amount: Long,
    ): SolanaTransaction {
        val instructions = buildNewCurrencyBuyInstructions(
            serverParameters = response,
            nonce = response.nonce,
            authority = authority,
            coreMintMetadata = coreMintMetadata,
            amount = amount,
        )

        return SolanaTransaction.newV0Instance(
            payer = response.payer,
            recentBlockhash = response.blockhash,
            addressLookupTables = response.alts,
            instructions = instructions,
        )
    }

    fun usdcFundSwap(
        owner: PublicKey,
        sender: PublicKey,
        amount: Long,
        pool: LiquidityPool,
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
}