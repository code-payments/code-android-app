package com.getcode.model.intents

import com.codeinc.gen.common.v1.Model.InstructionAccount
import com.codeinc.gen.transaction.v2.TransactionService.SwapRequest
import com.codeinc.gen.transaction.v2.TransactionService.SwapResponse
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.network.repository.toHash
import com.getcode.network.repository.toPublicKey
import com.getcode.network.repository.toSignature
import com.getcode.solana.AccountMeta
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.builder.TransactionBuilder
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.solana.organizer.AccountCluster
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.google.protobuf.ByteString
import org.kin.sdk.base.models.Key
import java.lang.IllegalStateException
import kotlin.math.sign

class SwapIntent(
    val id: PublicKey,
    val organizer: Organizer,
    val owner: KeyPair,
    val swapCluster: AccountCluster,
) {

    var parameters: SwapConfigParameters? = null

    fun sign(parameters: SwapConfigParameters): List<Signature> {
        val transaction = transaction(parameters)
        return transaction.sign(organizer.swapKeyPair)
    }

    fun transaction(parameters: SwapConfigParameters): SolanaTransaction {
        return TransactionBuilder.swap(
            fromUsdc = swapCluster,
            toPrimary = organizer.primaryVault,
            parameters = parameters
        )
    }

    companion object {
        fun newInstance(organizer: Organizer): SwapIntent {
            return SwapIntent(
                id = PublicKey.generate(),
                organizer = organizer,
                owner = organizer.ownerKeyPair,
                swapCluster = organizer.tray.cluster(AccountType.Swap),
            )
        }
    }
}

fun SwapIntent.requestToSubmitSignatures(): SwapRequest? = runCatching {
    parameters ?: throw IllegalStateException("Missing swap parameters")

    return@runCatching SwapRequest.newBuilder()
        .setSubmitSignature(
            SwapRequest.SubmitSignature.newBuilder()
                .setSignature(sign(parameters!!).first().bytes.toByteArray().toSignature())
                .build()
        ).build()
}.getOrNull()

data class SwapConfigParameters(
    val payer: PublicKey,
    val swapProgram: PublicKey,
    val nonce: PublicKey,
    val blockHash: Hash,
    val maxToSend: Long,
    val minToReceive: Long,
    val computeUnitLimit: Int,
    val computeUnitPrice: Long,
    val swapAccounts: List<AccountMeta>,
    val swapData: ByteString,
) {
    companion object {
        operator fun invoke(proto: SwapResponse.ServerParameters): SwapConfigParameters? {
            return runCatching {
                val payer = proto.payer.value.toByteArray().toPublicKey()
                val swapProgram = proto.swapProgram.value.toByteArray().toPublicKey()
                val nonce = proto.nonce.value.toByteArray().toPublicKey()
                val blockHash = proto.recentBlockhash.value.toByteArray().toHash()

                SwapConfigParameters(
                    payer = payer,
                    swapProgram = swapProgram,
                    nonce = nonce,
                    blockHash = blockHash,
                    maxToSend = proto.maxToSend,
                    minToReceive = proto.minToReceive,
                    computeUnitLimit = proto.computeUnitLimit,
                    computeUnitPrice = proto.computeUnitPrice,
                    swapAccounts = proto.swapIxnAccountsList.mapNotNull { it.meta() },
                    swapData = proto.swapIxnData
                )
            }.getOrNull()
        }
    }
}

private fun InstructionAccount.meta(): AccountMeta? = runCatching {
    val publicKey = PublicKey(account.value.toList())
    AccountMeta(
        publicKey = publicKey,
        isSigner = isSigner,
        isWritable = isWritable,
        isPayer = false,
        isProgram = false
    )
}.getOrNull()