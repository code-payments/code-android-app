package com.getcode.navigation.flow

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.results.NavigationRetVal

/**
 * Marker interface for an [AppRoute] that represents a multi-step flow.
 *
 * A [FlowRoute] owns its own inner [androidx.navigation3.runtime.NavBackStack] via [FlowHost],
 * so steps within the flow cannot be navigated to from outside and predictive back stays within
 * the flow.
 */
interface FlowRoute : NavKey {
    /**
     * The initial inner back stack to seed the flow with. Must not be empty for a well-formed
     * flow. Deeplinks may seed this with more than one entry — e.g. an email magic link deeplink
     * can open a verification flow already on the magic-link step with the entry step underneath.
     */
    val initialStack: List<NavKey>
}

/**
 * A [FlowRoute] that returns a typed result to the caller via
 * [com.getcode.navigation.results.navigateForResult].
 */
interface FlowRouteWithResult<T : Parcelable> : FlowRoute, NavigationRetVal<T>

/**
 * Remembers the [FlowRoute.initialStack] cast to the concrete [FlowStep] type.
 * Eliminates boilerplate from every flow screen wrapper.
 */
@Composable
inline fun <reified S : FlowStep> FlowRoute.rememberInitialStack(): List<S> {
    return remember(this) {
        @Suppress("UNCHECKED_CAST")
        initialStack as List<S>
    }
}
