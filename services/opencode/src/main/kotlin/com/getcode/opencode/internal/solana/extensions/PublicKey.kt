package com.getcode.opencode.internal.solana.extensions

import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram
import com.getcode.opencode.internal.solana.programs.TimelockProgram
import com.getcode.opencode.internal.solana.programs.TokenProgram
import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.solana.keys.PublicKey
import java.io.ByteArrayOutputStream
import java.io.IOException

internal fun PublicKey.Companion.deriveVirtualMachineAccount(mint: PublicKey, lockout: UByte): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "code_vm".toByteArray(Charsets.UTF_8),
            mint.bytes.toByteArray(),
            vmAuthority.bytes.toByteArray(),
            byteArrayOf(lockout.toByte())
        ),
        programId = VirtualMachineProgram.address,
    )
}

internal fun PublicKey.Companion.deriveDepositAccount(vm: PublicKey, depositor: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "code_vm".toByteArray(Charsets.UTF_8),
            "vm_deposit_pda".toByteArray(Charsets.UTF_8),
            depositor.bytes.toByteArray(),
            vm.bytes.toByteArray()
        ),
        programId = VirtualMachineProgram.address,
    )
}

internal fun PublicKey.Companion.deriveAssociatedAccount(owner: PublicKey, mint: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(owner.bytes.toByteArray(), TokenProgram.address.bytes.toByteArray(), mint.bytes.toByteArray()),
        programId = AssociatedTokenProgram.address,
    )
}

internal fun PublicKey.Companion.deriveTimelockStateAccount(owner: PublicKey, lockout: UByte): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "timelock_state".toByteArray(Charsets.UTF_8),
            usdc.bytes.toByteArray(),
            vmAuthority.bytes.toByteArray(),
            owner.bytes.toByteArray(),
            byteArrayOf(lockout.toByte())
        ),
        programId = TimelockProgram.address,
    )
}

internal fun PublicKey.Companion.deriveTimelockVaultAccount(
    stateAccount: PublicKey,
    version: Long
): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "timelock_vault".toByteArray(Charsets.UTF_8),
            stateAccount.bytes.toByteArray(),
            byteArrayOf(version.toByte())
        ),
        programId = TimelockProgram.address,
    )
}

/// FindProgramAddress mirrors the implementation of the Solana SDK's FindProgramAddress. Its primary
/// use case (for Kin and Agora) is for deriving associated accounts.
///
/// Reference: https://github.com/solana-labs/solana/blob/5548e599fe4920b71766e0ad1d121755ce9c63d5/sdk/program/src/pubkey.rs#L234
///
private fun findProgramAddress(
    seeds: List<ByteArray>,
    programId: PublicKey
): ProgramDerivedAccount {
    for (i in 0..255) {
        val bumpValue = 255 - i
        try {
            val publicKey = deriveProgramAddress(programId, listOf(*seeds.toTypedArray(), byteArrayOf(bumpValue.toByte())))
            return ProgramDerivedAccount(publicKey, bumpValue)
        } catch (e: RuntimeException) {
            //no-op
        }
    }

    throw Exception("Unable to find a viable program address nonce")
}

/// CreateProgramAddress mirrors the implementation of the Solana SDK's CreateProgramAddress.
///
/// ProgramAddresses are public keys that _do not_ lie on the ed25519 curve to ensure that
/// there is no associated private key. In the event that the program and seed parameters
/// result in a valid public key, ErrInvalidPublicKey is returned.
///
/// Reference: https://github.com/solana-labs/solana/blob/5548e599fe4920b71766e0ad1d121755ce9c63d5/sdk/program/src/pubkey.rs#L158
///
private fun deriveProgramAddress(programId: PublicKey, seeds: List<ByteArray>): PublicKey {
    fun PublicKey.Companion.getMaxSeeds() = 16

    val buffer = ByteArrayOutputStream()
    require(seeds.size < PublicKey.MAX_SEEDS) { "Max seed size exceeded" }

    for (seed in seeds) {
        try {
            buffer.write(seed)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
    try {
        buffer.write(programId.bytes.toByteArray())
        buffer.write("ProgramDerivedAddress".toByteArray())
    } catch (e: IOException) {
        throw RuntimeException(e)
    }
    val hash = Sha256Hash.hash(buffer.toByteArray())

    val publicKey = PublicKey(hash.toList())

    // Following the Solana SDK, we want to _reject_ the generated public key
    // if it's a valid compressed EdwardsPoint (on the curve).
    //
    if (Ed25519.onCurve(publicKey.bytes.toByteArray())) {
        throw RuntimeException("Invalid seeds, address must fall off the curve")
    }

    return PublicKey(hash.toList())
}