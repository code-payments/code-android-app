package com.getcode.model.intents

import com.codeinc.gen.transaction.v2.CodeTransactionService.SwapRequest
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.generate
import com.getcode.network.repository.toSignature
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.builder.TransactionBuilder
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.solana.organizer.AccountCluster
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import java.lang.IllegalStateException

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