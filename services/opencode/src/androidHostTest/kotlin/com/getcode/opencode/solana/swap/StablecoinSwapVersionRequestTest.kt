package com.getcode.opencode.solana.swap

import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.opencode.internal.network.api.intents.IntentStatelessSwap
import com.getcode.opencode.internal.network.extensions.stablecoinParams
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.StatelessSwapRequest
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapProgram
import com.getcode.opencode.model.transactions.SwapRoute
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The server builds the stablecoin swap with the version we request and we rebuild it locally, so
 * the request's swap_version and the locally built instruction must come from one value.
 */
class StablecoinSwapVersionRequestTest {

    private fun key(seed: Int) = PublicKey(ByteArray(32) { seed.toByte() }.toList())

    @Test
    fun `swap version constant is 2`() {
        assertEquals(2, CoinbaseStableSwapperProgram.SERVER_SWAP_VERSION)
    }

    @Test
    fun `stateful stablecoin initiate params carry swap_version 2`() {
        val request = StatefulSwapRequest(
            owner = mockk<AccountCluster>(relaxed = true),
            swapAuthority = mockk<KeyPair>(relaxed = true) {
                every { publicKeyBytes } returns ByteArray(32) { 21 }
            },
            program = SwapProgram.Stablecoin(
                fromMint = key(1),
                toMint = key(2),
                destinationOwner = key(3),
                fundingSource = SwapFundingSource.SubmitIntent(key(30).bytes),
            ),
            route = mockk<SwapRoute>(relaxed = true),
            swapAmount = LocalFiat(
                underlyingTokenAmount = Fiat(quarks = 1_000_000),
                nativeAmount = Fiat(quarks = 1_000_000),
                rate = Rate.oneToOne,
                mint = Mint.usdf,
            ),
            feeAmount = null,
            swapId = SwapId(key(31).bytes),
            verifiedState = mockk<VerifiedState>(relaxed = true),
        )

        assertEquals(2, request.stablecoinParams().build().swapVersion)
    }

    @Test
    fun `stateless stablecoin initiate params carry swap_version 2`() {
        val intent = IntentStatelessSwap(
            StatelessSwapRequest(
                owner = mockk<AccountCluster>(relaxed = true),
                fromMint = Mint(key(1).bytes),
                toMint = Mint(key(2).bytes),
                amount = 1_000_000,
            )
        )

        assertEquals(2, intent.stablecoinParams().swapVersion)
    }
}
