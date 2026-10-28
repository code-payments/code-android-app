package com.flipcash.app.messenger.internal.currencysend

import android.os.Parcelable
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** The screens inside the Currency Info send sheet, on its own nav host so Select Recipient slides in over the amount. */
internal sealed interface CurrencySendStep : FlowStep, Parcelable, NonDraggableRoute {
    @Parcelize
    @Serializable
    data object Amount : CurrencySendStep

    @Parcelize
    @Serializable
    data object Recipients : CurrencySendStep
}
