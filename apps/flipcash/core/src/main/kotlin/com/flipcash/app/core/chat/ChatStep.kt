package com.flipcash.app.core.chat

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface ChatStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    data object Conversation : ChatStep

    @Parcelize
    @Serializable
    data object AmountEntry : ChatStep
}
