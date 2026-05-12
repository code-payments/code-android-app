package com.flipcash.app.core.tokens

import android.os.Parcelable
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.flow.FlowStep
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.Fiat
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface SwapStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    data class Entry(val purpose: SwapPurpose, val initialAmount: Fiat? = null) : SwapStep

    @Parcelize
    @Serializable
    data object SellReceipt : SwapStep

    @Parcelize
    @Serializable
    data class Processing(
        val swapId: SwapId,
        val awaitExternalWallet: Boolean = false,
    ) : SwapStep, NonDismissableRoute, NonDraggableRoute
}
