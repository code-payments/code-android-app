package com.flipcash.app.core.deposit

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Steps inside the Withdrawal flow. Owned by [com.flipcash.app.core.AppRoute.Transfers.Withdrawal]
 * and rendered inside a [com.getcode.navigation.flow.FlowHost].
 */
@Serializable
sealed interface DepositStep : FlowStep, Parcelable {
    @Parcelize
    data class UsdcInformational(val showOtherOptions: Boolean) : DepositStep


    @Parcelize
    @Serializable
    data object SelectToken: DepositStep
    @Parcelize
    @Serializable
    data class Destination(val mint: Mint) : DepositStep
}