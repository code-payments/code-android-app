package com.flipcash.app.messenger.internal.request

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * The screens inside Request Money, on the screen's own nav host so each one slides in over the
 * last rather than replacing it. [RequestMoneyViewModel.Stage] decides which is on top; the
 * deadline and confirm sheets are drawn over [Summary], not steps.
 */
internal sealed interface RequestStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    data object History : RequestStep

    @Parcelize
    @Serializable
    data object Amount : RequestStep

    @Parcelize
    @Serializable
    data object Summary : RequestStep

    @Parcelize
    @Serializable
    data object People : RequestStep

    @Parcelize
    @Serializable
    data object Name : RequestStep
}

/** The back stack that puts [stage] on top: History, then Amount, then Summary and its editors. */
internal fun stackFor(stage: RequestMoneyViewModel.Stage): List<RequestStep> {
    val toSummary = listOf(RequestStep.History, RequestStep.Amount, RequestStep.Summary)
    return when (stage) {
        RequestMoneyViewModel.Stage.History -> listOf(RequestStep.History)
        RequestMoneyViewModel.Stage.Amount -> listOf(RequestStep.History, RequestStep.Amount)
        RequestMoneyViewModel.Stage.Summary -> toSummary
        RequestMoneyViewModel.Stage.People -> toSummary + RequestStep.People
        RequestMoneyViewModel.Stage.Name -> toSummary + RequestStep.Name
    }
}

/**
 * What the nav host does, seen from [step], to reach [target]: pop when [step] is no longer on the
 * stack, push the next step when [step] is below the top, and nothing when it is the top. Applied
 * one step at a time, each entry moving the stack on as it composes.
 */
internal sealed interface StackMove {
    data object None : StackMove
    data object Pop : StackMove
    data class Push(val step: RequestStep) : StackMove
}

internal fun moveFrom(step: RequestStep, target: List<RequestStep>): StackMove {
    val index = target.indexOf(step)
    return when {
        index < 0 -> StackMove.Pop
        index == target.lastIndex -> StackMove.None
        else -> StackMove.Push(target[index + 1])
    }
}
