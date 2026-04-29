package com.flipcash.app.core.withdrawal

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Steps inside the Withdrawal flow. Owned by [com.flipcash.app.core.AppRoute.Transfers.Withdrawal]
 * and rendered inside a [com.getcode.navigation.flow.FlowHost].
 */
@Serializable
sealed interface WithdrawalStep : FlowStep, Parcelable {
    @Parcelize
    object UsdcInformational: WithdrawalStep
    @Parcelize
    @Serializable
    data class Amount(val mint: Mint) : WithdrawalStep

    @Parcelize
    @Serializable
    data object Destination : WithdrawalStep

    @Parcelize
    @Serializable
    data object Confirmation : WithdrawalStep

    @Parcelize
    @Serializable
    data class Processing(val swapId: SwapId): WithdrawalStep
}
