package com.getcode.manager

import kotlinx.coroutines.flow.*
import java.util.*


data class BottomBarAction(
    val text: String,
    val style: BottomBarManager.BottomBarButtonStyle = BottomBarManager.BottomBarButtonStyle.Filled,
    val onClick: () -> Unit
)

/**
 * Represents an action related to a selected bottom bar item.
 *
 * @property index The index of the selected bottom bar item.
 * If index is -1, that means no action was selected or cancel was.
 */
data class SelectedBottomBarAction(
    val index: Int,
)

/**
 * Class responsible for managing BottomBar messages to show on the screen
 */
object BottomBarManager {
    data class BottomBarMessage(
        val title: String = "",
        val subtitle: String = "",
        val actions: List<BottomBarAction>,
        val showCancel: Boolean,
        val onClose: (selection: SelectedBottomBarAction) -> Unit = { },
        val onTimeout: () -> Unit = { },
        val type: BottomBarMessageType = BottomBarMessageType.DESTRUCTIVE,
        val isDismissible: Boolean = true,
        val showScrim: Boolean = false,
        val timeoutSeconds: Int? = null,
        val id: Long = UUID.randomUUID().mostSignificantBits,
    ) {
        constructor(
            title: String = "",
            subtitle: String = "",
            positiveText: String,
            positiveStyle: BottomBarButtonStyle = BottomBarButtonStyle.Filled,
            negativeText: String = "",
            negativeStyle: BottomBarButtonStyle = BottomBarButtonStyle.Filled50,
            tertiaryText: String? = null,
            onPositive: () -> Unit,
            onNegative: () -> Unit = {},
            onClose: (selection: SelectedBottomBarAction) -> Unit = { },
            onTimeout: () -> Unit = { },
            type: BottomBarMessageType = BottomBarMessageType.DESTRUCTIVE,
            isDismissible: Boolean = true,
            showScrim: Boolean = false,
            timeoutSeconds: Int? = null,
            id: Long = UUID.randomUUID().mostSignificantBits
        ) : this(
            title = title,
            subtitle = subtitle,
            actions = buildList {
                if (positiveText.isNotBlank()) {
                    add(BottomBarAction(positiveText, positiveStyle, onPositive))
                }

                if (negativeText.isNotBlank()) {
                    add(BottomBarAction(negativeText, negativeStyle, onNegative))
                }
            },
            showCancel = tertiaryText != null,
            onClose = onClose,
            onTimeout = onTimeout,
            type = type,
            isDismissible = isDismissible,
            showScrim = showScrim,
            timeoutSeconds = timeoutSeconds,
            id = id,
        )
    }

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

    fun clearByType(type: BottomBarMessageType) =
        _messages.update { it.filterNot { m -> m.type == type } }

    enum class BottomBarMessageType {
        DESTRUCTIVE,
        WARNING,
        @Deprecated("This is no longer necessary with the VM and balance deductions are handled intelligently")
        REMOTE_SEND,
        THEMED,
        SUCCESS,
    }

    enum class BottomBarButtonStyle {
        Filled, Filled50
    }

}