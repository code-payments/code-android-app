package com.getcode.model.intents.actions

import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.model.intents.CompactMessage
import com.getcode.model.intents.CompactMessageArgs
import com.getcode.model.intents.ServerParameter
import com.getcode.solana.SolanaTransaction
import com.getcode.utils.toByteArray
import org.kin.sdk.base.models.toUTF8Bytes
import com.codeinc.gen.transaction.v2.CodeTransactionService as TransactionService

abstract class ActionType {
    abstract var id: Int
    abstract var serverParameter: ServerParameter?
    abstract val signer: Ed25519.KeyPair?

    //abstract var configCountRequirement: Int

    abstract fun transactions(): List<SolanaTransaction>
    open fun compactMessageArgs(): List<CompactMessageArgs> = emptyList()
    fun compactMessages(): List<CompactMessage> {
        return compactMessageArgs().map { args ->
            when (args) {
                is CompactMessageArgs.Transfer -> {

                    val data = mutableListOf<Byte>()
                    data.addAll("transfer".toUTF8Bytes().toList())
                    data.addAll(args.source.bytes)
                    data.addAll(args.destination.bytes)
                    data.addAll(args.amountInQuarks.toByteArray().toList())
                    data.addAll(args.nonce.bytes)
                    data.addAll(args.nonceValue.bytes)

                    Sha256Hash.hash(data.toByteArray())
                }
            }
        }
    }

    fun signatures(): List<com.getcode.solana.keys.Signature> {
        return signer?.let { s ->
            compactMessages().map {
                com.getcode.solana.keys.Signature(Ed25519.sign(it, s).toList()) }
        }.orEmpty()
    }

    abstract fun action(): TransactionService.Action

    companion object {
        const val kreIndex: Int = 268
    }
}

fun List<ActionType>.numberActions(): List<ActionType> {
    return this.mapIndexed { index, _ ->
        this[index].apply { this.id = index }
    }
}