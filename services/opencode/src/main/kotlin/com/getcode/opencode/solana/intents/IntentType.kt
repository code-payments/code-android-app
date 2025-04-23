package com.getcode.opencode.solana.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.opencode.solana.intents.actions.numberActions
import com.getcode.opencode.internal.network.extensions.asIntentId
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.asSignature
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.solana.Message
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

abstract class IntentType {
    abstract val id: PublicKey
    abstract val actionGroup: ActionGroup

    val actions: List<ActionType>
        get() = actionGroup.actions

    fun apply(parameters: List<ServerParameter>) {
        if (parameters.size != actions.size) {
            throw Exception(Error.InvalidParameterCount.name)
        }

        parameters.forEachIndexed { index, parameter ->
            actions[index].serverParameter = parameter
        }
    }

    fun vixnHash(): Hash {
        return actions.flatMap { it.compactMessages() }
            .flatMap { it.toList() }
            .take(32).let { Hash(it) }
    }

    fun transaction(): SolanaTransaction {
        val message = actions.flatMap { it.transactions() }.map { it.message }
            .let { Message.newInstance(it.map { it.encode().toList() }.flatten()) }!!
        val sigs = actions.flatMap { it.signatures() }

        return SolanaTransaction(message, sigs)
    }

    val signatures: List<Signature>
        get() = actions.map { it.signatures().firstOrNull() }.mapNotNull { it }

    abstract fun metadata(): TransactionService.Metadata

    fun requestToSubmitSignatures(): TransactionService.SubmitIntentRequest {
        return TransactionService.SubmitIntentRequest.newBuilder()
            .setSubmitSignatures(
                TransactionService.SubmitIntentRequest.SubmitSignatures.newBuilder()
                    .addAllSignatures(signatures.map { it.bytes.toByteArray().asSignature() })
            )
            .build()
    }

    fun requestToSubmitActions(owner: Ed25519.KeyPair, deviceToken: String? = null): TransactionService.SubmitIntentRequest {
        val submitActionsBuilder = TransactionService.SubmitIntentRequest.SubmitActions.newBuilder()
        submitActionsBuilder.owner = owner.asSolanaAccountId()
        submitActionsBuilder.id = id.asIntentId()
        submitActionsBuilder.metadata = metadata()
        submitActionsBuilder.addAllActions(actions.map { it.action() })

        submitActionsBuilder.signature = submitActionsBuilder.sign(owner)

        return TransactionService.SubmitIntentRequest.newBuilder()
            .setSubmitActions(submitActionsBuilder)
            .build()
    }

    enum class Error {
        InvalidParameterCount
    }
}

class ActionGroup {
    var actions: List<ActionType> = listOf()
        set(value) {
            field = value.numberActions()
        }
}

sealed interface CompactMessageArgs {

    data class Transfer(
        val source: PublicKey,
        val destination: PublicKey,
        val amountInQuarks: Long,
        val nonce: PublicKey,
        val nonceValue: Hash,
    ): CompactMessageArgs

    data class Withdraw(
        val source: PublicKey,
        val destination: PublicKey,
        val nonce: PublicKey,
        val nonceValue: Hash,
    ): CompactMessageArgs
}

typealias CompactMessage = ByteArray