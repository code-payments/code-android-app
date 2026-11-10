package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram
import com.getcode.opencode.internal.solana.programs.TimelockProgram
import com.getcode.opencode.internal.solana.programs.TokenProgram
import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

@InternalOpenCodeApi public fun PublicKey.Companion.deriveVirtualMachineAccount(mint: PublicKey, authority: PublicKey, lockout: UByte): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "code_vm".encodeToByteArray(),
            mint.bytes.toByteArray(),
            authority.bytes.toByteArray(),
            byteArrayOf(lockout.toByte())
        ),
        programId = VirtualMachineProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveDepositAccount(vm: PublicKey, depositor: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "code_vm".encodeToByteArray(),
            "vm_deposit_pda".encodeToByteArray(),
            depositor.bytes.toByteArray(),
            vm.bytes.toByteArray()
        ),
        programId = VirtualMachineProgram.address,
    )
}
 fun PublicKey.Companion.deriveAssociatedAccount(owner: PublicKey, mint: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(owner.bytes.toByteArray(), TokenProgram.address.bytes.toByteArray(), mint.bytes.toByteArray()),
        programId = AssociatedTokenProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveTimelockStateAccount(owner: PublicKey, mint: Mint, authority: PublicKey, lockout: UByte): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "timelock_state".encodeToByteArray(),
            mint.bytes.toByteArray(),
            authority.bytes.toByteArray(),
            owner.bytes.toByteArray(),
            byteArrayOf(lockout.toByte())
        ),
        programId = TimelockProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveTimelockVaultAccount(
    stateAccount: PublicKey,
    version: Long
): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "timelock_vault".encodeToByteArray(),
            stateAccount.bytes.toByteArray(),
            byteArrayOf(version.toByte())
        ),
        programId = TimelockProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveSwapAddress(owner: PublicKey, mint: PublicKey, authority: PublicKey, lockout: UByte): ProgramDerivedAccount {
    val vmAccount = deriveVirtualMachineAccount(mint, authority, lockout)

    return findProgramAddress(
        seeds = listOf(
            "code_vm".encodeToByteArray(),
            "vm_swap_pda".encodeToByteArray(),
            owner.bytes.toByteArray(),
            vmAccount.publicKey.bytes.toByteArray()
        ),
        programId = VirtualMachineProgram.address
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveVmOmnibusAddress(vm: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        programId = VirtualMachineProgram.address,
        seeds = listOf(
            "code_vm".encodeToByteArray(),
            "vm_omnibus".encodeToByteArray(),
            vm.bytes.toByteArray()
        )
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveCurrencyMintAddress(authority: PublicKey, name: String, seed: PublicKey): ProgramDerivedAccount {
    val nameBytes = ByteArray(32)
    val raw = name.encodeToByteArray()
    raw.copyInto(nameBytes, 0, 0, minOf(raw.size, 32))

    return findProgramAddress(
        seeds = listOf(
            "mint".encodeToByteArray(),
            authority.bytes.toByteArray(),
            nameBytes,
            seed.bytes.toByteArray()
        ),
        programId = CurrencyCreatorProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveCurrencyConfigAddress(mint: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "currency".encodeToByteArray(),
            mint.bytes.toByteArray()
        ),
        programId = CurrencyCreatorProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveLiquidityPoolAddress(currency: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "pool".encodeToByteArray(),
            currency.bytes.toByteArray()
        ),
        programId = CurrencyCreatorProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveVaultAddress(pool: PublicKey, mint: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "treasury".encodeToByteArray(),
            pool.bytes.toByteArray(),
            mint.bytes.toByteArray()
        ),
        programId = CurrencyCreatorProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveCoinbasePoolAddress(): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf("liquidity_pool".encodeToByteArray()),
        programId = CoinbaseStableSwapperProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveCoinbaseTokenVaultAddress(pool: PublicKey, mint: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "token_vault".encodeToByteArray(),
            pool.bytes.toByteArray(),
            mint.bytes.toByteArray(),
        ),
        programId = CoinbaseStableSwapperProgram.address,
    )
}

@InternalOpenCodeApi public fun PublicKey.Companion.deriveCoinbaseVaultTokenAccountAddress(vault: PublicKey): ProgramDerivedAccount {
    return findProgramAddress(
        seeds = listOf(
            "vault_token_account".encodeToByteArray(),
            vault.bytes.toByteArray(),
        ),
        programId = CoinbaseStableSwapperProgram.address,
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

    require(seeds.size < PublicKey.MAX_SEEDS) { "Max seed size exceeded" }

    // seeds || programId || "ProgramDerivedAddress"
    var buffer = ByteArray(0)
    for (seed in seeds) {
        buffer += seed
    }
    buffer += programId.bytes.toByteArray()
    buffer += "ProgramDerivedAddress".encodeToByteArray()
    val hash = Sha256Hash.hash(buffer)

    val publicKey = PublicKey(hash.toList())

    // Following the Solana SDK, we want to _reject_ the generated public key
    // if it's a valid compressed EdwardsPoint (on the curve).
    //
    if (Ed25519.onCurve(publicKey.bytes.toByteArray())) {
        throw RuntimeException("Invalid seeds, address must fall off the curve")
    }

    return PublicKey(hash.toList())
}