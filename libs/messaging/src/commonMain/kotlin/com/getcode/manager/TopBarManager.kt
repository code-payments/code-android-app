package com.getcode.manager

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


/**
 * Class responsible for managing TopBar messages to show on the screen
 */
object TopBarManager {
    data class TopBarMessage(
        val title: String,
        val message: String,
        val type: TopBarMessageType = TopBarMessageType.ERROR,
        val primaryText: String? = null,
        val secondaryText: String? = null,
        val primaryAction: () -> Unit = {},
        val secondaryAction: () -> Unit = {},
        val id: Long = UUID.randomUUID().mostSignificantBits
    )

    private val _messages: MutableStateFlow<List<TopBarMessage>> = MutableStateFlow(
        listOf()
    )
    val messages: StateFlow<List<TopBarMessage>> get() = _messages.asStateFlow()

    fun showMessage(title: String, message: String, type: TopBarMessageType = TopBarMessageType.ERROR) =
        showMessage(TopBarMessage(title, message, type))

    fun showMessage(topBarMessage: TopBarMessage) {
        val isAlreadyNetworkError = topBarMessage.type == TopBarMessageType.ERROR_NETWORK &&
                messages.value.any { it.type == TopBarMessageType.ERROR_NETWORK }
        val isAlreadyExisting = messages.value.any { it.message == topBarMessage.message }

        if (isAlreadyNetworkError || isAlreadyExisting) {
            return
        }

        _messages.update { currentMessages ->
            currentMessages + topBarMessage
        }
    }

    fun setMessageShown(messageId: Long) {
        _messages.update { currentMessages ->
            currentMessages.filterNot { it.id == messageId }
        }
    }

    fun setMessageShown() {
        if (_messages.value.isEmpty()) return

        _messages.update { currentMessages ->
            currentMessages.filterNot { it.id == currentMessages.first().id }
        }
    }
    enum class TopBarMessageType { ERROR_NETWORK, ERROR, WARNING, NOTIFICATION, NEUTRAL, SUCCESS }
}
