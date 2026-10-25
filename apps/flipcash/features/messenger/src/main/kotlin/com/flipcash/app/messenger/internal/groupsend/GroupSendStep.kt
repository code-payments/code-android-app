package com.flipcash.app.messenger.internal.groupsend

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * The screens inside the amount entry sheet, on the sheet's own nav host so Select People slides
 * in over the amount rather than replacing it. The confirm sheet is drawn over [People], not a step.
 */
internal sealed interface GroupSendStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    data object Amount : GroupSendStep

    @Parcelize
    @Serializable
    data object People : GroupSendStep
}
