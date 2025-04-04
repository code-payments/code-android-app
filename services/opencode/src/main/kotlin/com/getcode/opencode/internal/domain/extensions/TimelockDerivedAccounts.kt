package com.getcode.opencode.internal.domain.extensions

import com.getcode.solana.keys.ProgramDerivedAccount
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.TimelockDerivedAccounts

fun TimelockDerivedAccounts.Companion.newInstance(owner: PublicKey, legacy: Boolean = false): TimelockDerivedAccounts {
    val state: ProgramDerivedAccount
    val vault: ProgramDerivedAccount

    if (legacy) {
        state =
            PublicKey.deriveLegacyTimelockStateAccount(owner = owner, lockout = 1_814_400)
        vault = PublicKey.deriveLegacyTimelockVaultAccount(stateAccount = state.publicKey)
    } else {
        state = PublicKey.deriveTimelockStateAccount(owner = owner, lockout = lockoutInDays)
        vault = PublicKey.deriveTimelockVaultAccount(
            stateAccount = state.publicKey,
            version = dataVersion
        )
    }

    return TimelockDerivedAccounts(
        owner = owner,
        state = state,
        vault = vault
    )
}