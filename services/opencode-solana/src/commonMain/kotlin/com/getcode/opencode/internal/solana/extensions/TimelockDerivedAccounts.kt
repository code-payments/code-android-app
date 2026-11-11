package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.opencode.solana.keys.TimelockVmSwapAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

@InternalOpenCodeApi public fun TimelockDerivedAccounts.Companion.newInstance(owner: PublicKey, mint: Mint): TimelockDerivedAccounts {
    val state: ProgramDerivedAccount = PublicKey.deriveTimelockStateAccount(
        mint = mint,
        owner = owner,
        lockout = lockoutInDays.toUByte(),
        authority = vmAuthority
    )

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
