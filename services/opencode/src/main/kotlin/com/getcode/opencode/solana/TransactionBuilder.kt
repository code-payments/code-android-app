package com.getcode.opencode.solana

import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapDirection
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.opencode.solana.swap.buildBuyInstructions
import com.getcode.opencode.solana.swap.buildSellInstructions
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
     *                 lookup tables (ALTs), and potentially a blockhash. Can be stateful or stateless.
     * @param metadata Verified metadata associated with the swap request, including nonce information.
     * @param authority The public key of the user authorizing the swap (the wallet owner).
     * @param swapAuthority The public key of the temporary swap authority derived from the nonce.
     * @param direction The direction of the swap (Buy or Sell) and the target/source mint involved.
     * @param amount The amount of tokens to swap (in the source currency's smallest unit).
     * @param minOutput The minimum acceptable amount of output tokens to receive (slippage protection).
     *                  Defaults to 0.
     * @return A constructed [SolanaTransaction] (V0) ready to be signed and submitted to the network.
     */
    fun swap(
        response: SwapResponseServerParameters,
        metadata: VerifiedSwapMetadata,
        authority: PublicKey,
        swapAuthority: PublicKey,
        direction: SwapDirection,
        amount: Long,
        minOutput: Long = 0,
    ): SolanaTransaction {
        val (payer, blockhash, alts) = when (val params = response) {
            is SwapResponseServerParameters.Stateful -> Triple(params.payer, metadata.serverParameters.blockhash, params.alts)
            is SwapResponseServerParameters.Stateless -> Triple(params.payer, params.recentBlockhash, params.alts)
        }

        val coreMint = Token.usdf

        val instructions = when (direction) {
            is SwapDirection.Buy -> buildBuyInstructions(
                serverParameters = response,
                nonce = metadata.serverParameters.nonce,
                authority = authority,
                swapAuthority = swapAuthority,
                coreMintMetadata = coreMint,
                targetMintMetadata = direction.mint,
                amount = amount,
                minOutput = minOutput,
            )
            is SwapDirection.Sell -> buildSellInstructions(
                serverParameters = response,
                nonce = metadata.serverParameters.nonce,
                authority = authority,
                swapAuthority = swapAuthority,
                sourceMintMetadata = direction.mint,
                coreMintMetadata = coreMint,
                amount = amount,
                minOutput = minOutput,
            )
        }

        return SolanaTransaction.newV0Instance(
            payer = payer,
            recentBlockhash = blockhash,
            addressLookupTables = alts,
            instructions = instructions,
        )
    }
}