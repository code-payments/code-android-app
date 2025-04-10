package com.getcode.opencode.internal.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.intents.actions.ActionType
import com.getcode.opencode.internal.intents.actions.numberActions
import com.getcode.opencode.internal.network.extensions.asIntentId
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.asSignature
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.solana.Message
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey

abstract class IntentType {
    abstract val id: PublicKey
    abstract val actionGroup: ActionGroup

    fun getActions() = actionGroup.actions
    fun getAction(index: Int) = getActions()[index]

    fun apply(parameters: List<ServerParameter>) {
        if (parameters.size != actionGroup.actions.size) {
            throw Exception(Error.InvalidParameterCount.name)
        }

        parameters.forEachIndexed { index, parameter ->
            actionGroup.actions[index].serverParameter = parameter
        }
    }

    fun vixnHash(): Hash {
        return actionGroup.actions.flatMap { it.compactMessages() }
            .flatMap { it.toList() }
            .take(32).let { Hash(it) }
    }

    fun transaction(): SolanaTransaction {
        val message = actionGroup.actions.flatMap { it.transactions() }.map { it.message }
            .let { Message.newInstance(it.map { it.encode().toList() }.flatten()) }!!
        val sigs = actionGroup.actions.flatMap { it.signatures() }

        return SolanaTransaction(message, sigs)
    }

    fun signatures(): List<com.getcode.solana.keys.Signature> =
        actionGroup.actions.map { it.signatures().firstOrNull() }.mapNotNull { it }

    abstract fun metadata(): TransactionService.Metadata

    fun requestToSubmitSignatures(): TransactionService.SubmitIntentRequest {
        return TransactionService.SubmitIntentRequest.newBuilder()
            .setSubmitSignatures(
                TransactionService.SubmitIntentRequest.SubmitSignatures.newBuilder()
                    .addAllSignatures(signatures().map { it.bytes.toByteArray().asSignature() })
            )
            .build()
    }

    fun requestToSubmitActions(owner: Ed25519.KeyPair, deviceToken: String? = null): TransactionService.SubmitIntentRequest {
        val submitActionsBuilder = TransactionService.SubmitIntentRequest.SubmitActions.newBuilder()
        submitActionsBuilder.owner = owner.asSolanaAccountId()
        submitActionsBuilder.id = id.asIntentId()
        submitActionsBuilder.metadata = metadata()
        submitActionsBuilder.addAllActions(actionGroup.actions.map { it.action() })

        submitActionsBuilder.signature = submitActionsBuilder.sign(owner)

        return TransactionService.SubmitIntentRequest.newBuilder()
            .setSubmitActions(submitActionsBuilder)
            .build()
    }

    enum class Error {
        InvalidParameterCount,
        ActionParameterMismatch
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
}
typealias CompactMessage = ByteArray