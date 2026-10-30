package com.getcode.ui.components.bars

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Composable
fun rememberBarManager(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) =
    remember(coroutineScope) {
        BarManager(coroutineScope)
    }

class BarManager(
    coroutineScope: CoroutineScope
) {
    val barMessages = BarMessages()

    init {
        coroutineScope.launch {
            TopBarManager.messages.collect { currentMessages ->
                barMessages.topBar.value = currentMessages.firstOrNull()
            }
        }
        coroutineScope.launch {
            BottomBarManager.messages.collect { currentMessages ->
                barMessages.bottomBar.value = currentMessages.firstOrNull()
            }
        }
    }
}