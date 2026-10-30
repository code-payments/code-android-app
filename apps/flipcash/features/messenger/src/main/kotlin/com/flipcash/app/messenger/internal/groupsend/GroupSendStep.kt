package com.flipcash.app.messenger.internal.groupsend

import android.os.Parcelable
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * The screens inside the amount entry sheet, on the sheet's own nav host so Select People slides
 * in over the amount rather than replacing it. The confirm sheet is drawn over [People], not a step.
 *
 * [NonDraggableRoute] at the interface level, as `ReportStep` does: the sheet closes by its X, as
 * the share sheet does, never by a drag.
 */
internal sealed interface GroupSendStep : FlowStep, Parcelable, NonDraggableRoute {
    @Parcelize
    @Serializable
    data object Amount : GroupSendStep

    @Parcelize
    @Serializable
    data object People : GroupSendStep
}
