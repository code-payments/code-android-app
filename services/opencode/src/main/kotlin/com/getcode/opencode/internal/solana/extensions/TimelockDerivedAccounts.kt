package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

internal fun TimelockDerivedAccounts.Companion.newInstance(owner: PublicKey, mint: Mint): TimelockDerivedAccounts {
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

internal fun TimelockDerivedAccounts.Companion.newInstance(owner: PublicKey, token: Token): TimelockDerivedAccounts {
    val state: ProgramDerivedAccount = PublicKey.deriveTimelockStateAccount(
        mint = token.address,
        owner = owner,
        authority = token.vmMetadata.authority,
        lockout = token.vmMetadata.lockDurationInDays.toUByte()
    )

    val vault: ProgramDerivedAccount = PublicKey.deriveTimelockVaultAccount(
        stateAccount = state.publicKey,
        version = dataVersion
    )

    return TimelockDerivedAccounts(
        owner = owner,
        state = state,
        vault = vault,
    )
}