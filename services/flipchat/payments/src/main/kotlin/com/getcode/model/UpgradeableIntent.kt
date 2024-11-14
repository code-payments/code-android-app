package com.getcode.model

import com.codeinc.gen.transaction.v2.CodeTransactionService as TransactionService
import com.getcode.solana.keys.PublicKey

class UpgradeableIntent(
    val id: PublicKey,
    val actions: List<UpgradeablePrivateAction>,
) {
    companion object {
        fun newInstance(proto: TransactionService.UpgradeableIntent): UpgradeableIntent {
            val intentId = PublicKey(proto.id.value.toByteArray().toList())

            val actions = proto.actionsList.map {
                UpgradeablePrivateAction.newInstance(it)
            }

            return UpgradeableIntent(intentId, actions)
        }

    }

}
