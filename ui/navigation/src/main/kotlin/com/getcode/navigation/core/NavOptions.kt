package com.getcode.navigation.core

import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.BuildConfig
import kotlin.reflect.KClass

/**
 * Options for navigating with [CodeNavigator.navigate].
 *
 * @param popUpTo If set, pop up to a specific destination before navigating in a transaction.
 * @param navigatingForResult A flag to help detect unintended navigation for result scenarios which
 *   would otherwise result is missed results.
 */
data class NavOptions(
    val popUpTo: PopUpTo = PopUpTo.None,
    val navigatingForResult: Boolean = false,
    val debugRouting: Boolean = BuildConfig.DEBUG,
) {
    sealed interface PopUpTo {
        data object ClearAll : PopUpTo
        data object PopLast : PopUpTo

        data class ClearToFirst<T : NavKey>(
            val routeClass: KClass<T>,
            val inclusive: Boolean = false,
        ) : PopUpTo
        data object None : PopUpTo
    }
}
