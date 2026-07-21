package com.flipcash.app.userprofile

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.userprofile.internal.name.NameEntryScreen
import com.flipcash.app.userprofile.internal.photo.PhotoSelectionScreen
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry

@Composable
fun UpdateUserProfileFlowScreen(
    route: AppRoute.UpdateUserProfile,
    resultStateRegistry: NavResultStateRegistry,
) {
    // Capture the outer navigator before FlowHost overrides LocalCodeNavigator.
    val outerNavigator = LocalCodeNavigator.current

    val steps = route.rememberInitialStack<UpdateProfileStep>()

    FlowHost<UpdateProfileStep, UpdateProfileResult>(
        steps = steps,
        resumeAt = 0,
        resultStateRegistry = resultStateRegistry,
        // Walking off the end of the step list (the last proceed()) completes the flow.
        completedResult = UpdateProfileResult.Success,
        onExit = { reason, _ ->
            val result: UpdateProfileResult = when (reason) {
                is FlowExitReason.Completed -> reason.result
                FlowExitReason.Canceled,
                FlowExitReason.BackedOutOfRoot -> UpdateProfileResult.Canceled
            }
            outerNavigator.deliverFlowResult(
                route = route,
                value = NavResultOrCanceled.ReturnValue(result),
            )
            if (route.target != null && result is UpdateProfileResult.Success) {
                outerNavigator.replaceAll(route.target!!)
            } else {
                outerNavigator.pop()
            }
        },
        entryProvider = profileUpdateProvider(route),
    )
}

private fun profileUpdateProvider(
    route: AppRoute.UpdateUserProfile,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<UpdateProfileStep.Name> {
        NameEntryScreen()
    }
    annotatedEntry<UpdateProfileStep.Photo> {
        PhotoSelectionScreen()
    }
}
