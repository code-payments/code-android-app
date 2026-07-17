package com.getcode.opencode.model.financial

import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.ui.TokenBillCustomizations

data class TokenCreateRequest(
    val name: ModerationAttestation.Text,
    val symbol: ModerationAttestation.Text? = null,
    val description: ModerationAttestation.Text?,
    val bill: TokenBillCustomizations?,
    val icon: ModerationAttestation.Image?,
    val funding: Funding,
) {
    /**
     * How the launch is paid for. [token] is the funding currency — USDF (reserves) or any
     * launchpad currency; the service layer decides buy vs. cross-currency swap from it.
     *
     * [amount] is the funding-token valuation whose on-chain quarks are the amount of [token] to
     * move (for a launchpad currency, token quarks from the bonding curve), plus the verified state.
     * [fullAmount] is the exact USD launch cost — the service pins [amount]'s native value to it for
     * the treasury flow. [feeAmount] is the pool fee in funding-token terms.
     */
    data class Funding(
        val token: Token,
        val amount: VerifiedFiat,
        val fullAmount: Fiat,
        val feeAmount: LocalFiat? = null,
    )
}
