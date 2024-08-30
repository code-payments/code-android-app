package com.getcode.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import cafe.adriel.voyager.core.stack.StackEvent
import com.getcode.CodeAppState
import com.getcode.manager.ModalManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.buildMessageContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun ModalContainer(
    navigator: CodeNavigator,
    appState: CodeAppState
) {
    val modalMessage by appState.modalMessage.collectAsState()
    var modalMessageId by remember { mutableLongStateOf(0L) }
    val onClose: suspend (actionType: ModalManager.ActionType?) -> Unit = {
        modalMessageId = modalMessage?.id ?: 0
        modalMessage?.onClose?.invoke(it)

        delay(100)
        ModalManager.setMessageShown(modalMessageId)
    }

    // handle changes in visible state
    LaunchedEffect(navigator) {
        snapshotFlow { navigator.lastEvent }
            .filter { it == StackEvent.Pop }
            .onEach { delay(50) }
            .onEach {
                if (modalMessageId == modalMessage?.id) {
                    modalMessageId = 0
                }
            }.launchIn(this)
    }

    // handle provided timeout duration; triggering onClose with no action
    LaunchedEffect(modalMessage) {
        modalMessage?.timeoutSeconds?.let {
            delay(it * 1000L)
            onClose(null)
        }
    }

    val scope = rememberCoroutineScope()
    val closeWith: (ModalManager.ActionType?) -> Unit = { type ->
        scope.launch {
            onClose(type)
        }
        navigator.hide()
    }

    modalMessage?.let { message ->
        navigator.show(buildMessageContent(message, onClose = closeWith))
    }
}