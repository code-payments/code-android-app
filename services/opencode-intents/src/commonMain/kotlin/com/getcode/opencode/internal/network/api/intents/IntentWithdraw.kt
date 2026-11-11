package com.getcode.opencode.internal.network.api.intents

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.opencode.model.transactions.VerifiedExchangeState
import com.getcode.opencode.internal.network.api.intents.actions.ActionFeePayment
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fee
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.buildActionGroup
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

@InternalOpenCodeApi
class IntentWithdraw(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup,
) : IntentType() {

    companion object {
        fun create(
            amount: LocalFiat,
            mint: Mint,
            sourceCluster: AccountCluster,
            destination: PublicKey,
            destinationOwner: PublicKey?,
            fee: Fee? = null,
            verifiedState: VerifiedExchangeState,
        ): IntentWithdraw {
            // transfer the amount less any fee
            val feeAmount = fee?.fiat ?: Fiat.Zero
            val transferAmount = amount.underlyingTokenAmount - feeAmount

            val actionGroup = buildActionGroup {
                add(
                    ActionPublicTransfer.newInstance(
                        owner = sourceCluster.authority.keyPair,
                        source = sourceCluster.vaultPublicKey,
                        destination = destination,
                        amount = transferAmount,
                        mint = mint,
                    )
                )

                if (fee != null) {
                    add(
                        ActionFeePayment.newInstance(
                            fee = fee,
                            source = sourceCluster,
                            mint = mint,
                        )
                    )
                }
            }

            return IntentWithdraw(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.SendPublicPayment(
                    source = sourceCluster.vaultPublicKey,
                    destination = destination,
                    destinationOwner = destinationOwner,
                    amount = amount,
                    mint = mint,
                    isIndirect = false,
                    isWithdrawal = true,
                    verifiedState = verifiedState,
                ),
                actionGroup = actionGroup
            )
        }
    }
}