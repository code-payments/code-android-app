package com.getcode.manager

import kotlinx.coroutines.flow.*
import java.util.*
import kotlin.concurrent.timerTask


/**
 * Class responsible for managing BottomBar messages to show on the screen
 */
object BottomBarManager {
    data class BottomBarMessage(
        val title: String,
        val subtitle: String = "",
        val positiveText: String,
        val negativeText: String,
        val tertiaryText: String? = null,
        val onPositive: () -> Unit,
        val onNegative: () -> Unit = {},
        val onTertiary: () -> Unit = {},
        val onClose: (actionType: BottomBarActionType?) -> Unit = {},
        val type: BottomBarMessageType = BottomBarMessageType.DEFAULT,
        val isDismissible: Boolean = true,
        val timeoutSeconds: Int? = null,
        val id: Long = UUID.randomUUID().mostSignificantBits,
    )

    private val _messages: MutableStateFlow<List<BottomBarMessage>> = MutableStateFlow(
        listOf()
    )
    val messages: StateFlow<List<BottomBarMessage>> get() = _messages.asStateFlow()

    fun showMessage(bottomBarMessage: BottomBarMessage) {
        _messages.update { currentMessages ->
            currentMessages + bottomBarMessage
        }
    }

    fun setMessageShown(messageId: Long) {
        _messages.update { currentMessages ->
            currentMessages.filterNot { it.id == messageId }
        }
    }

    fun clear() = _messages.update { listOf() }

    fun clearByType(type: BottomBarMessageType) =  _messages.update { it.filterNot { m -> m.type == type } }

    enum class BottomBarMessageType { DEFAULT, REMOTE_SEND }

    enum class BottomBarActionType {
        Positive,
        Negative,
        Tertiary
    }

}