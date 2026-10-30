package com.flipcash.services.modals

import androidx.annotation.DrawableRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object ModalManager {
    data class Message(
        @DrawableRes
        val icon: Int? = null,
        val title: String,
        val subtitle: String = "",
        val positiveText: String,
        val negativeText: String? = null,
        val tertiaryText: String? = null,
        val onPositive: () -> Unit,
        val onNegative: () -> Unit = {},
        val onTertiary: () -> Unit = {},
        val onClose: (actionType: ActionType?) -> Unit = {},
        val type: MessageType = MessageType.DEFAULT,
//        val isDismissibleByTouchOutside: Boolean = true,
        val isDismissibleByBackButton: Boolean = true,
        val timeoutSeconds: Int? = null,
        val id: Long = UUID.randomUUID().mostSignificantBits,
    )

    private val _messages: MutableStateFlow<List<Message>> = MutableStateFlow(
        listOf()
    )
    val messages: StateFlow<List<Message>> get() = _messages.asStateFlow()

    fun showMessage(message: Message) {
        _messages.update { currentMessages ->
            currentMessages + message
        }
    }

    fun setMessageShown(messageId: Long) {
        _messages.update { currentMessages ->
            currentMessages.filterNot { it.id == messageId }
        }
    }

    fun clear() = _messages.update { listOf() }

    fun clearByType(type: MessageType) =  _messages.update { it.filterNot { m -> m.type == type } }

    enum class MessageType { DEFAULT }

    enum class ActionType {
        Positive,
        Negative,
        Tertiary
    }

}