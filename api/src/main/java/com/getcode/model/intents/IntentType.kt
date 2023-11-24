package com.getcode.model.intents

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.keys.Signature
import com.getcode.model.intents.actions.ActionType
import com.getcode.model.intents.actions.numberActions
import com.getcode.network.repository.*
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
            if (actionGroup.actions[index].id != parameter.actionId) {
                throw Exception(Error.ActionParameterMismatch.name)
            }
            actionGroup.actions[index].serverParameter = parameter
        }
    }

    fun signatures(): List<Signature> =
        actionGroup.actions.map { it.signatures().firstOrNull() }.mapNotNull { it }

    abstract fun metadata(): TransactionService.Metadata

    fun requestToSubmitSignatures(): TransactionService.SubmitIntentRequest {
        return TransactionService.SubmitIntentRequest.newBuilder()
            .setSubmitSignatures(
                TransactionService.SubmitIntentRequest.SubmitSignatures.newBuilder()
                    .addAllSignatures(signatures().map { it.bytes.toByteArray().toSignature() })
            )
            .build()
    }

    fun requestToSubmitActions(owner: Ed25519.KeyPair): TransactionService.SubmitIntentRequest {
        val submitActionsBuilder = TransactionService.SubmitIntentRequest.SubmitActions.newBuilder()
        submitActionsBuilder.owner = owner.publicKeyBytes.toSolanaAccount()
        submitActionsBuilder.id = id.toIntentId()
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