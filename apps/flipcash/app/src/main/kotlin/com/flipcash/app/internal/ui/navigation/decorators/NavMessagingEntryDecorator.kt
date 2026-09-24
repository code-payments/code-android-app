package com.flipcash.app.internal.ui.navigation.decorators

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.flipcash.analytics.events.ErrorModalEvents
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.NavMetadataKeys
import com.getcode.ui.components.bars.BarManager
import com.getcode.ui.components.bars.BottomBarContainer
import com.getcode.ui.components.bars.TopBarContainer
import com.getcode.utils.TraceType
import com.getcode.utils.trace

@Suppress("FunctionName")
fun NavMessagingEntryDecorator(
    backStack: NavBackStack<NavKey>,
    barManager: BarManager
): NavEntryDecorator<NavKey> {
    return NavEntryDecorator { entry ->
        val analytics = rememberAnalytics()
        Box {
            entry.Content()
            val hosts = hostsBars(
                isTopEntry = entry.contentKey == backStack.lastOrNull()?.toString(),
                isSheet = entry.metadata[NavMetadataKeys.IsSheet.key] == true,
                coversScreen = entry.metadata[NavMetadataKeys.IsFullscreenSheet.key] == true,
            )
            if (hosts) {
                BottomBarContainer(barManager.barMessages) { message ->
                    trace(
                        message = "bottom bar message shown [${message.type.name}]",
                        metadata = {
                            "title" to message.title
                            "message" to message.subtitle
                            "type" to message.type.name
                            "additionalInfo" to message.additionalInfo
                        },
                        type = TraceType.Process,
                    )
                    when (message.type) {
                        BottomBarManager.BottomBarMessageType.DESTRUCTIVE -> Unit
                        BottomBarManager.BottomBarMessageType.ERROR -> {
                            val screen = backStack.lastOrNull()?.screenName()
                            analytics.track(
                                ErrorModalEvents.displayed(
                                    title = message.title,
                                    message = message.subtitle,
                                    screen = screen,
                                    callSite = message.callSite,
                                )
                            )
                        }
                        BottomBarManager.BottomBarMessageType.WARNING -> Unit
                        BottomBarManager.BottomBarMessageType.INFO -> Unit
                        BottomBarManager.BottomBarMessageType.DEFAULT -> Unit
                        BottomBarManager.BottomBarMessageType.SUCCESS -> Unit
                    }
                }
            }
        }
    }
}

@Composable
fun rememberNavMessagingEntryDecorator(
    backStack: NavBackStack<NavKey>,
    barManager: BarManager
) = remember { NavMessagingEntryDecorator(backStack, barManager) }

private fun NavKey.screenName(): String? {
    val prefix = AppRoute::class.java.name
    val name = this::class.java.name
    return name.removePrefix("$prefix$").replace('$', '.').takeIf { it != name }
}

/**
 * Whether an entry is the one that puts the app's bars on screen.
 *
 * A sheet that covers the screen carries its own bars, exactly as a route does: the bar has the
 * whole window to sit in, and its scrim has nothing left to leave showing. A partial sheet has
 * neither — a bar inside one is clipped to the sheet, and one behind it is covered — so a partial
 * sheet leaves bars to the route beneath it. That is why a bar raised from inside a partial sheet
 * is only seen once the sheet has closed, and why a flow that wants to be acknowledged before it
 * closes has to cover the screen.
 */
internal fun hostsBars(isTopEntry: Boolean, isSheet: Boolean, coversScreen: Boolean): Boolean =
    isTopEntry && (!isSheet || coversScreen)
