package com.getcode.opencode.model.accounts

import com.getcode.solana.keys.PublicKey

sealed interface AccountFilter {
    data class TokenAddress(val address: PublicKey) : AccountFilter
    data class AccountType(val accountType: com.getcode.opencode.model.accounts.AccountType) : AccountFilter
    data class MintAddress(val address: PublicKey): AccountFilter
}