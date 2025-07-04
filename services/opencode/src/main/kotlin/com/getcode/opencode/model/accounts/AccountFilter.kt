package com.getcode.opencode.model.accounts

import com.getcode.solana.keys.PublicKey

sealed interface AccountFilter {
    data class TokenAddress(val tokenAddress: PublicKey) : AccountFilter
    data class AccountType(val accountType: com.getcode.opencode.model.accounts.AccountType) : AccountFilter
}