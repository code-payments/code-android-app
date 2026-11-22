package com.getcode.opencode.internal.network.services

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.solana.keys.Mint
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A buy spends USDF, and [com.getcode.opencode.internal.network.funding.SwapFunding] moves it out
 * of the request owner's vault. The server rejects a funding transfer from any other vault with
 * "actions[0]: token must be <the owner's USDF vault>".
 */
class BuySwapRequestTest {

    private val authority = DerivedKey(DerivePath.primary, Ed25519.createKeyPair(ByteArray(32) { 1 }))
    private val usdfCluster = AccountCluster.newInstance(authority)
    private val bought = Token.usdf.copy(address = Mint(List(32) { 7 }))

    private fun request(owner: AccountCluster) = buySwapRequest(
        owner = owner,
        of = bought,
        amount = LocalFiat.Zero,
        feeAmount = null,
        swapId = null,
        verifiedState = mockk<VerifiedState>(relaxed = true),
        source = SwapFundingSource.SubmitIntent(),
    )

    @Test
    fun `a buy handed the bought token's cluster funds from the USDF vault`() {
        // A collection paid in the bought token hands its pay cluster to every funding leg.
        val boughtCluster = usdfCluster.withTimelockForToken(bought)
        assertNotEquals(usdfCluster.vaultPublicKey, boughtCluster.vaultPublicKey)

        val owner = request(boughtCluster).owner

        assertEquals(usdfCluster.vaultPublicKey, owner.vaultPublicKey)
        assertEquals(usdfCluster.authorityPublicKey, owner.authorityPublicKey)
    }

    @Test
    fun `a buy handed the USDF cluster funds from the USDF vault`() {
        assertEquals(usdfCluster.vaultPublicKey, request(usdfCluster).owner.vaultPublicKey)
    }
}
