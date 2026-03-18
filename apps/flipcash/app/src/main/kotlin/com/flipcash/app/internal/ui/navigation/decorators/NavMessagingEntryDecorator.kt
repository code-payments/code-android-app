package com.flipcash.app.internal.ui.navigation.decorators

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.NavMetadataKeys
import com.getcode.navigation.metadata
import com.getcode.ui.components.bars.BarManager
import com.getcode.ui.components.bars.BottomBarContainer
import com.getcode.ui.components.bars.TopBarContainer

@Suppress("FunctionName")
fun NavMessagingEntryDecorator(
    backStack: NavBackStack<NavKey>,
    barManager: BarManager
): NavEntryDecorator<NavKey> {
    return NavEntryDecorator { entry ->
        Box {
            entry.Content()
            if (backStack.lastOrNull()?.metadata()[NavMetadataKeys.IsSheet.key] != true) {
                TopBarContainer(barManager.barMessages)
                BottomBarContainer(barManager.barMessages)
            }
        }
    }
}

@Composable
fun rememberNavMessagingEntryDecorator(
    backStack: NavBackStack<NavKey>,
    barManager: BarManager
) = remember { NavMessagingEntryDecorator(backStack, barManager) }
