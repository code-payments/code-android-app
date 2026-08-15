package com.flipcash.app.internal.ui.navigation.decorators

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.core.navigation.asNavBarTab
import com.getcode.navigation.core.CodeNavigator

/**
 * Provides [LocalTabBarPadding] **per entry** so tab-home screens reserve space for the (separately
 * hoisted) v2 navigation bar while pushed/detail screens do not.
 *
 * Crucially it renders *nothing* — the bar itself is a single persistent overlay at the app root (see
 * AppNavigationBar in NewAppContent) so tab switches stay seamless (the selection pill slides, one haze
 * source, no crossfade). This decorator only supplies the inset, and does so per entry:
 *
 * - The inset is a single global before this — toggled by the top route — collapsed mid-transition and
 *   jumped the still-visible outgoing tab screen. Here the value is fixed for the entry's lifetime, so
 *   the outgoing tab keeps its inset through a push while the incoming detail simply never had one.
 * - The entry's route is resolved once (keyed by its stable contentKey), not re-read from the live
 *   backstack, so a tab switch (replaceAll) removing the outgoing entry can't flip it mid-animation.
 *
 * [tabBarHeight] is the height measured by the hoisted bar; reading `.value` here keeps the inset in
 * sync once the bar is laid out.
 */
@Suppress("FunctionName")
fun NavTabBarInsetEntryDecorator(
    navigator: CodeNavigator,
    tabBarHeight: State<Dp>,
): NavEntryDecorator<NavKey> {
    return NavEntryDecorator { entry ->
        // entry.key is private; recover THIS entry's route by matching its contentKey (= key.toString())
        // against the backstack, once per entry.
        val route = remember(entry.contentKey) {
            navigator.backStack.firstOrNull { it.toString() == entry.contentKey } as? AppRoute
        }
        val isTabHome = route?.asNavBarTab() != null
        val padding = if (isTabHome) PaddingValues(bottom = tabBarHeight.value) else PaddingValues()

        CompositionLocalProvider(LocalTabBarPadding provides padding) {
            entry.Content()
        }
    }
}

@Composable
fun rememberNavTabBarInsetEntryDecorator(
    navigator: CodeNavigator,
    tabBarHeight: State<Dp>,
): NavEntryDecorator<NavKey> =
    remember(navigator, tabBarHeight) { NavTabBarInsetEntryDecorator(navigator, tabBarHeight) }
