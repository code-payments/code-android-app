package com.getcode.opencode.model.balance

/**
 * Balance data for an owner account, as returned by the OpenCode `Balance.GetBalance`
 * RPC. Unlike most OCP responses, this is not scoped to a single token account —
 * it reports the owner's core-mint (USDF) balance directly.
 */
data class Balance(
    /**
     * The owner's core-mint (USDF) balance, in quarks. Mirrors how token-account
     * balances are represented elsewhere in this module (e.g. [com.getcode.opencode.model.accounts.AccountInfo.balance]) —
     * a raw quark [Long] rather than a currency-aware [com.getcode.opencode.model.financial.Fiat],
     * since the response carries no currency code.
     */
    val coreMintValue: Long,
)
