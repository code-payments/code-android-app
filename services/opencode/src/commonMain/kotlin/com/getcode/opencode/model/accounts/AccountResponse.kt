package com.getcode.opencode.model.accounts

import com.getcode.solana.keys.PublicKey

data class AccountResponse(
    val accounts: Map<PublicKey, AccountInfo>,
)
