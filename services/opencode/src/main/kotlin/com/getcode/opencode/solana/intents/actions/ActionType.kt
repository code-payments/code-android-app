package com.getcode.opencode.solana.intents.actions

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.solana.intents.CompactMessage
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.utils.toByteArray
import org.kin.sdk.base.models.toUTF8Bytes

abstract class ActionType {
    abstract var id: Int
    abstract var serverParameter: ServerParameter?
    abstract val signer: Ed25519.KeyPair?

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

                is CompactMessageArgs.Withdraw -> {
                    val data = mutableListOf<Byte>()
                    data.addAll("withdraw_and_close".toUTF8Bytes().toList())
                    data.addAll(args.source.bytes)
                    data.addAll(args.destination.bytes)
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

internal fun List<ActionType>.numberActions(): List<ActionType> {
    return List(this.size) { index ->
        this[index].apply { this.id = index }
    }
}