package com.getcode.model.intents.actions

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.keys.Signature
import com.getcode.model.intents.ServerParameter
import com.getcode.solana.SolanaTransaction

abstract class ActionType {
    abstract var id: Int
    abstract var serverParameter: ServerParameter?
    abstract val signer: Ed25519.KeyPair?

    //abstract var configCountRequirement: Int

    abstract fun transactions(): List<SolanaTransaction>

    fun signatures(): List<Signature> {
        return signer?.let { s ->
            transactions().map { transaction -> transaction.sign(s).first() }
        } ?: listOf()
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
