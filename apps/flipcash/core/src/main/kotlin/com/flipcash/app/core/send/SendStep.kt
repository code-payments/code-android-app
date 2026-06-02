package com.flipcash.app.core.send

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import com.getcode.opencode.model.financial.Fiat
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Steps inside the Send flow. Rendered inside a [com.getcode.navigation.flow.FlowHost]
 * by `SendFlowScreen` in the `direct-send` feature module.
 */
@Serializable
sealed interface SendStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    data object PhoneGate : SendStep

    @Parcelize
    @Serializable
    data object ContactsGate : SendStep

    @Parcelize
    @Serializable
    data object ContactList : SendStep

    @Parcelize
    @Serializable
    data class AmountEntry(
        val e164: String,
        val displayName: String,
    ) : SendStep
}

@Serializable
sealed interface SendResult : Parcelable {
    @Parcelize
    @Serializable
    data class Sent(val amount: Fiat) : SendResult
}
