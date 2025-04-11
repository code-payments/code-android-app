package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.PublicKey

internal fun TimelockDerivedAccounts.Companion.newInstance(owner: PublicKey): TimelockDerivedAccounts {

    val state: ProgramDerivedAccount = PublicKey.deriveTimelockStateAccount(owner = owner, lockout = lockoutInDays.toUByte())
    val vault: ProgramDerivedAccount = PublicKey.deriveTimelockVaultAccount(
        stateAccount = state.publicKey,
        version = dataVersion
    )

    return TimelockDerivedAccounts(
        owner = owner,
        state = state,
        vault = vault
    )
}