package com.flipcash.app.core.send

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
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
}

@Serializable
sealed interface SendResult : Parcelable
