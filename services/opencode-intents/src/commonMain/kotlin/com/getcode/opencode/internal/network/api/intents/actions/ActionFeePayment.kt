package com.getcode.opencode.internal.network.api.intents.actions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fee
import com.getcode.opencode.model.financial.FeeType
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.Mint

@InternalOpenCodeApi
class ActionFeePayment(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair,
    val mint: Mint,
    val fee: Fee,
    val source: AccountCluster,
): ActionType() {
    override fun compactMessageArgs(): List<CompactMessageArgs> {
        val configs = serverParameter?.configs ?: return emptyList()

        val destination = when (fee.type) {
            FeeType.CreateOnSendWithdrawal -> {
                (serverParameter?.parameter as? ServerParameter.Parameter.FeePayment)?.publicKey
                    ?: return emptyList()
            }
        }

        return configs.map {
            val amountInQuarks = fee.fiat.quarks
            val nonceAccount = it.nonce
            val nonceValue = it.blockhash

            CompactMessageArgs.Transfer(
                source = source.vaultPublicKey,
                destination = destination,
                amountInQuarks = amountInQuarks,
                nonce = nonceAccount,
                nonceValue = nonceValue
            )
        }
    }


    companion object {
        fun newInstance(
            fee: Fee,
            source: AccountCluster,
            mint: Mint,
        ): ActionFeePayment {
            return ActionFeePayment(
                id = 0,
                signer = source.authority.keyPair,
                fee = fee,
                source = source,
                mint = mint,
            )
        }
    }
}