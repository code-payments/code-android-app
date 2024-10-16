package com.getcode.model.extensions

import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.model.Kin
import com.getcode.solana.instructions.programs.*
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.Key32.Companion.splitter
import com.getcode.solana.keys.Key32.Companion.subsidizer
import com.getcode.solana.keys.Key32.Companion.timeAuthority
import com.getcode.solana.keys.ProgramDerivedAccount
import com.getcode.solana.keys.PublicKey
import org.kin.sdk.base.tools.longToByteArray
import java.io.ByteArrayOutputStream
import java.io.IOException

fun PublicKey.Companion.deriveAssociatedAccount(owner: PublicKey, mint: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(owner.bytes.toByteArray(), TokenProgram.address.bytes.toByteArray(), mint.bytes.toByteArray()),
        programId = AssociatedTokenProgram.address,
    )
}

fun PublicKey.Companion.deriveTimelockStateAccount(
    owner: PublicKey,
    lockout: Long
): ProgramDerivedAccount {
    val seeds: List<ByteArray> = listOf(
        "timelock_state".toByteArray(Charsets.UTF_8),
        kin.bytes.toByteArray(),
        timeAuthority.bytes.toByteArray(),
        owner.bytes.toByteArray(),
        byteArrayOf(lockout.toByte())
    )

    return findProgramAddress(
        seeds = seeds,
        programId = TimelockProgram.address,
    )
}

fun PublicKey.Companion.deriveTimelockVaultAccount(
    stateAccount: PublicKey,
    version: Long
): ProgramDerivedAccount {
    val seeds: List<ByteArray> = listOf(
        "timelock_vault".toByteArray(Charsets.UTF_8),
        stateAccount.bytes.toByteArray(),
        byteArrayOf(version.toByte())
    )

    return findProgramAddress(
        seeds = seeds,
        programId = TimelockProgram.address,
    )
}

fun PublicKey.Companion.deriveLegacyTimelockStateAccount(
    owner: PublicKey,
    lockout: Long
): ProgramDerivedAccount {
    val nonce = SystemProgram.address
    val version = byteArrayOf(1)
    val pdaPadding = SystemProgram.address

    val seeds: List<ByteArray> = listOf(
        "timelock_state".toByteArray(Charsets.UTF_8),
        version,
        kin.bytes.toByteArray(),
        subsidizer.bytes.toByteArray(),
        nonce.bytes.toByteArray(),
        owner.bytes.toByteArray(),
        lockout.longToByteArray(),
        pdaPadding.bytes.toByteArray()
    )

    return findProgramAddress(
        seeds = seeds,
        programId = TimelockProgram.legacyAddress,
    )
}

fun PublicKey.Companion.deriveLegacyTimelockVaultAccount(
    stateAccount: PublicKey
): ProgramDerivedAccount {
    val seeds: List<ByteArray> = listOf(
        "timelock_vault".toByteArray(Charsets.UTF_8),
        stateAccount.bytes.toByteArray(),
        byteArrayOf(0)
    )

    return findProgramAddress(
        seeds = seeds,
        programId = TimelockProgram.legacyAddress,
    )
}

/// FindProgramAddress mirrors the implementation of the Solana SDK's FindProgramAddress. Its primary
/// use case (for Kin and Agora) is for deriving associated accounts.
///
/// Reference: https://github.com/solana-labs/solana/blob/5548e599fe4920b71766e0ad1d121755ce9c63d5/sdk/program/src/pubkey.rs#L234
///
fun PublicKey.Companion.findProgramAddress(
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
fun PublicKey.Companion.deriveProgramAddress(programId: PublicKey, seeds: List<ByteArray>): PublicKey {
    fun PublicKey.Companion.getMaxSeeds() = 16

    val buffer = ByteArrayOutputStream()
    require(seeds.size < getMaxSeeds()) { "Max seed size exceeded" }

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

fun PublicKey.Companion.deriveCommitmentStateAccount(treasury: PublicKey, recentRoot: Hash, transcript: Hash, destination: PublicKey, amount: Kin): ProgramDerivedAccount {
    return findProgramAddress(
        programId = splitter,
        seeds = listOf(
            "commitment_state".toByteArray(Charsets.UTF_8),
            treasury.bytes.toByteArray(),
            recentRoot.bytes.toByteArray(),
            transcript.bytes.toByteArray(),
            destination.bytes.toByteArray(),
            amount.quarks.longToByteArray()
        )
    )
}

fun PublicKey.Companion.deriveCommitmentVaultAccount(treasury: PublicKey, commitmentState: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        programId = splitter,
        seeds = listOf(
            "commitment_vault".toByteArray(Charsets.UTF_8),
            treasury.bytes.toByteArray(),
            commitmentState.bytes.toByteArray()
        )
    )
}

fun PublicKey.Companion.derivePreSwapState(
    source: PublicKey, destination: PublicKey, nonce: PublicKey
): ProgramDerivedAccount {
    return findProgramAddress(
        programId = SwapValidatorProgram.address,
        seeds = listOf(
            "pre_swap_state".toByteArray(Charsets.UTF_8),
            source.bytes.toByteArray(),
            destination.bytes.toByteArray(),
            nonce.bytes.toByteArray(),
        )
    )
}