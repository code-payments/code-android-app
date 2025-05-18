package com.getcode.manager

import kotlinx.coroutines.flow.*
import java.util.*


data class BottomBarAction(
    val text: String,
    val style: BottomBarManager.BottomBarButtonStyle = BottomBarManager.BottomBarButtonStyle.Filled,
    val onClick: () -> Unit
)

/**
 * Class responsible for managing BottomBar messages to show on the screen
 */
object BottomBarManager {
    data class BottomBarMessage(
        val title: String = "",
        val subtitle: String = "",
        val actions: List<BottomBarAction> = emptyList(),
        val showCancel: Boolean = true,
        val onClose: (fromAction: Boolean) -> Unit = {},
        val type: BottomBarMessageType = BottomBarMessageType.DESTRUCTIVE,
        val isDismissible: Boolean = true,
        val showScrim: Boolean = false,
        val timeoutSeconds: Int? = null,
        val id: Long = UUID.randomUUID().mostSignificantBits,

        @Deprecated(
            message = "Use actions instead (e.g., the first item in actions)",
            replaceWith = ReplaceWith("actions.firstOrNull()?.title ?: \"\""),
            level = DeprecationLevel.WARNING
        )
        val positiveText: String = "",
        @Deprecated(
            message = "Use actions instead (e.g., the first item in actions)",
            replaceWith = ReplaceWith("actions.firstOrNull()?.style ?: \"\""),
            level = DeprecationLevel.WARNING
        )
        val positiveStyle: BottomBarButtonStyle = BottomBarButtonStyle.Filled,
        @Deprecated(
            message = "Use actions instead (e.g., the second item in actions)",
            replaceWith = ReplaceWith("actions.getOrNull(1)?.title ?: \"\""),
            level = DeprecationLevel.WARNING
        )
        val negativeText: String = "",
        @Deprecated(
            message = "Use actions instead (e.g., the second item in actions)",
            replaceWith = ReplaceWith("actions.getOrNull(1)?.style ?: \"\""),
            level = DeprecationLevel.WARNING
        )
        val negativeStyle: BottomBarButtonStyle = BottomBarButtonStyle.Filled50,
        @Deprecated(
            message = "Use actions instead (e.g., the third item in actions)",
            replaceWith = ReplaceWith("actions.getOrNull(2)?.title"),
            level = DeprecationLevel.WARNING
        )
        val tertiaryText: String? = null,
        @Deprecated(
            message = "Use actions instead (e.g., the first item in actions)",
            replaceWith = ReplaceWith("actions.firstOrNull()?.onClick ?: { }"),
            level = DeprecationLevel.WARNING
        )
        val onPositive: () -> Unit = { },
        @Deprecated(
            message = "Use actions instead (e.g., the second item in actions)",
            replaceWith = ReplaceWith("actions.getOrNull(1)?.onClick ?: { }"),
            level = DeprecationLevel.WARNING
        )
        val onNegative: () -> Unit = {},
        @Deprecated(
            message = "Use actions instead (e.g., the third item in actions)",
            replaceWith = ReplaceWith("actions.getOrNull(2)?.onClick ?: { }"),
            level = DeprecationLevel.WARNING
        )
        val onTertiary: () -> Unit = {},
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
            onTertiary: () -> Unit = {},
            onClose: (fromAction: Boolean) -> Unit = {},
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
            type = type,
            isDismissible = isDismissible,
            showScrim = showScrim,
            timeoutSeconds = timeoutSeconds,
            id = id,
            positiveText = positiveText,
            positiveStyle = positiveStyle,
            negativeText = negativeText,
            negativeStyle = negativeStyle,
            tertiaryText = tertiaryText,
            onPositive = onPositive,
            onNegative = onNegative,
            onTertiary = onTertiary
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