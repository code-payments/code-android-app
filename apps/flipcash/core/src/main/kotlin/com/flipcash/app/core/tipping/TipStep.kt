package com.flipcash.app.core.tipping

import android.os.Parcelable
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface TipStep : FlowStep, Parcelable, NonDismissableRoute, NonDraggableRoute {

    @Parcelize
    @Serializable
    data object Intro : TipStep

    @Parcelize
    @Serializable
    data object TipCard: TipStep

    @Parcelize
    @Serializable
    data object Tips : TipStep
}
