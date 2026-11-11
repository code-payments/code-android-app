package com.getcode.opencode.solana.intents

import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.Message
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

/**
 * An OCP intent: its id, domain metadata and the actions it is built from. Protocol encoding (the
 * SubmitIntent request and metadata messages) lives with each client: protobuf-java in
 * `:services:opencode`, Wire in `:services:opencode-client`.
 */
abstract class IntentType {
    abstract val id: PublicKey
    abstract val metadata: TransactionMetadata
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
            .let {
                Message.newInstance(it.map { it.encode().toList() }.flatten())
            }!!
        val sigs = actions.flatMap { it.signatures() }

        return SolanaTransaction(message, sigs)
    }

    val signatures: List<Signature>
        get() = actions.map { it.signatures().firstOrNull() }.mapNotNull { it }

    enum class Error {
        InvalidParameterCount
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