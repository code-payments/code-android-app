package com.flipcash.app.core.ui.flow

import android.os.Parcelable
import com.getcode.navigation.flow.FlowRouteWithResult
import com.getcode.navigation.flow.FlowStep
import kotlin.reflect.KClass

/**
 * A [FlowRouteWithResult] whose flow presents a stepped-progress wizard. Declaring a route as a
 * [SteppedFlowRoute] is how a flow opts into the shared [SteppedFlowScaffold] chrome (progress top
 * bar). [progressSteps] is the ordered list of step types that fill the progress bar; steps not in
 * this list (intro / processing / funding, etc.) show no progress.
 */
interface SteppedFlowRoute<T : Parcelable> : FlowRouteWithResult<T> {
    val progressSteps: List<KClass<out FlowStep>>
}
