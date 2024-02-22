package com.getcode.solana.keys

import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.model.Kin
import com.getcode.network.repository.toPublicKey
import com.getcode.solana.instructions.programs.*
import com.getcode.vendor.Base58
import org.kin.sdk.base.tools.longToByteArray
import java.io.ByteArrayOutputStream
import java.io.IOException


class PublicKey(bytes: List<Byte>) : Key32(bytes) {
    companion object {

        val kin: Mint
            get() = Mint(Base58.decode("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6").toList())

        val usdc: Mint
            get() = Mint(org.kin.sdk.base.tools.Base58.decode("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v").toList())

        fun generate(): PublicKey = Ed25519.createSeed32().toPublicKey()

        fun fromBase58(base58: String): PublicKey {
            return PublicKey(Base58.decode(base58).toList())
        }

        fun deriveAssociatedAccount(owner: PublicKey, mint: PublicKey): ProgramDerivedAccount {
            return findProgramAddress(
                seeds = listOf(owner.bytes.toByteArray(), TokenProgram.address.bytes.toByteArray(), mint.bytes.toByteArray()),
                programId = AssociatedTokenProgram.address,
            )
        }

        fun deriveTimelockStateAccount(
            owner: PublicKey,
            lockout: Long
        ): ProgramDerivedAccount {
            val seeds: List<ByteArray> = listOf(
                "timelock_state".toByteArray(Charsets.UTF_8),
                Mint.kin.bytes.toByteArray(),
                timeAuthority.bytes.toByteArray(),
                owner.bytes.toByteArray(),
                byteArrayOf(lockout.toByte())
            )

            return findProgramAddress(
                seeds = seeds,
                programId = TimelockProgram.address,
            )
        }

        fun deriveTimelockVaultAccount(
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

        fun deriveLegacyTimelockStateAccount(
            owner: PublicKey,
            lockout: Long
        ): ProgramDerivedAccount {
            val nonce = SystemProgram.address
            val version = byteArrayOf(1)
            val pdaPadding = SystemProgram.address

            val seeds: List<ByteArray> = listOf(
                "timelock_state".toByteArray(Charsets.UTF_8),
                version,
                Mint.kin.bytes.toByteArray(),
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

        fun deriveLegacyTimelockVaultAccount(
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
        fun findProgramAddress(
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
        fun deriveProgramAddress(programId: PublicKey, seeds: List<ByteArray>): PublicKey {
            fun getMaxSeeds() = 16

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

        fun deriveCommitmentStateAccount(treasury: PublicKey, recentRoot: Hash, transcript: Hash, destination: PublicKey, amount: Kin): ProgramDerivedAccount {
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

        fun deriveCommitmentVaultAccount(treasury: PublicKey, commitmentState: PublicKey): ProgramDerivedAccount {
            return findProgramAddress(
                programId = splitter,
                seeds = listOf(
                    "commitment_vault".toByteArray(Charsets.UTF_8),
                    treasury.bytes.toByteArray(),
                    commitmentState.bytes.toByteArray()
                )
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        other as PublicKey
        if (size == other.size && bytes == other.bytes) return true

        return false
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + size
        return result
    }

}