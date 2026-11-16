package com.getcode.opencode.internal.network.api.intents.actions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

@InternalOpenCodeApi
class ActionPublicTransfer(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: KeyPair,

    val mint: Mint,
    val amount: Fiat,
    val source: PublicKey,
    val destination: PublicKey,
) : ActionType() {

    override fun transactions(): List<SolanaTransaction> = listOf()
    override fun compactMessageArgs(): List<CompactMessageArgs> {
        val configs = serverParameter?.configs ?: return emptyList()
        return configs.map {

            val amountInQuarks = amount.quarks
            val nonceAccount = it.nonce
            val nonceValue = it.blockhash

            CompactMessageArgs.Transfer(
                source = source,
                destination = destination,
                amountInQuarks = amountInQuarks,
                nonce = nonceAccount,
                nonceValue = nonceValue
            )
        }
    }



    companion object {
        fun newInstance(
            amount: Fiat,
            mint: Mint,
            source: PublicKey,
            owner: KeyPair,
            destination: PublicKey
        ): ActionPublicTransfer {
            return ActionPublicTransfer(
                id = 0,
                signer = owner,
                amount = amount,
                mint = mint,
                source = source,
                destination = destination
            )
        }
    }
}