package com.flipcash.app.core.reporting

import android.os.Parcelable
import com.flipcash.reporting.ReportReason
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Picking a reason, and — for [ReportReason.Other] alone — saying what happened.
 *
 * [NonDraggableRoute] at the interface level, as `CurrencyCreatorStep` does: the enclosing route is
 * a sheet, and a sheet that can be dragged away is the wrong affordance over a half-written report.
 * Leaving is offered by the app bar, which [com.getcode.navigation.flow.FlowHost] turns from a
 * close into a back arrow once there is a step to go back to.
 *
 * Steps rather than one screen that swaps its own contents. The earlier version did the latter
 * inside a wrap-content sheet and deadlocked the main thread: such a sheet takes its drag anchors
 * from the height its content reports and reads those anchors again while placing that content, so
 * content that resizes itself keeps changing the number it was measured against. A step boundary
 * makes the height change once, between two fixed layouts.
 */
@Serializable
sealed interface ReportStep : FlowStep, Parcelable, NonDraggableRoute {
    @Parcelize
    @Serializable
    data object ReasonSelection : ReportStep

    /** Reached only from [ReportReason.Other]; every other reason submits from the list. */
    @Parcelize
    @Serializable
    data class Details(val reason: ReportReason) : ReportStep
}
