package com.flipcash.app.core.tokens

import android.os.Parcelable
import com.getcode.navigation.HalfSheet
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.Sheet
import com.getcode.navigation.WrapContentSheet
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
    data class TokenSelection(val amount: Fiat) : SwapStep

    @Parcelize
    @Serializable
    data object BuyReceipt : SwapStep

    @Parcelize
    @Serializable
    data object SellReceipt : SwapStep

    /**
     * Destination picker for a conversion. Presented as a bottom sheet over amount entry — the
     * amount screen stays composed underneath, so picking a currency never re-runs its entry
     * effects.
     */
    @Parcelize
    @Serializable
    data object ConvertDestinationSelection : SwapStep, Sheet, WrapContentSheet, HalfSheet

    /**
     * v2 Get only: the payment-source picker, opened from the inline "Get with" row on amount
     * entry. Distinct from [TokenSelection] because picking here pops back to the amount screen
     * rather than advancing to the receipt.
     */
    @Parcelize
    @Serializable
    data object FundingSelection : SwapStep, Sheet, WrapContentSheet, HalfSheet

    @Parcelize
    @Serializable
    data object ConvertReceipt : SwapStep

    @Parcelize
    @Serializable
    data object PhantomConnect: SwapStep

    @Parcelize
    @Serializable
    data object PhantomConfirmTransaction: SwapStep
    @Parcelize
    @Serializable
    data object Processing : SwapStep, NonDismissableRoute, NonDraggableRoute
}
