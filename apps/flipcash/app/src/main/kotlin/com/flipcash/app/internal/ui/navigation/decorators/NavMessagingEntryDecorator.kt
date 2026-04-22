package com.flipcash.app.internal.ui.navigation.decorators

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
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
            val isTopEntry = entry.contentKey == backStack.lastOrNull()?.toString()
            if (isTopEntry && entry.metadata[NavMetadataKeys.IsSheet.key] != true) {
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
                            analytics.displayedErrorModal(
                                title = message.title,
                                message = message.subtitle,
                                screen = screen,
                                callSite = message.callSite,
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
    val prefix = AppRoute::class.qualifiedName ?: return null
    val qualifiedName = this::class.qualifiedName ?: return null
    return qualifiedName.removePrefix("$prefix.").takeIf { it != qualifiedName }
}
