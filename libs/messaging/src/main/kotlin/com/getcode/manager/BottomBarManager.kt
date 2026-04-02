package com.getcode.manager

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.flow.*
import java.util.*


data class BottomBarAction(
    val text: AnnotatedString,
    val inlineContentMap: Map<String, InlineTextContent>,
    val style: BottomBarManager.BottomBarButtonStyle = BottomBarManager.BottomBarButtonStyle.Filled,
    val isUser: Boolean = true,
    val onClick: () -> Unit = { }
) {
    constructor(
        text: String,
        style: BottomBarManager.BottomBarButtonStyle = BottomBarManager.BottomBarButtonStyle.Filled,
        isUser: Boolean = true,
        onClick: () -> Unit = { }
    ): this(
        text = AnnotatedString(text),
        inlineContentMap = emptyMap(),
        style = style,
        isUser = isUser,
        onClick = onClick
    )

    companion object {
        const val OK_DESCRIPTOR = ":::OK:::"
        val Ok = BottomBarAction(
            text = OK_DESCRIPTOR,
            isUser = false,
            style = BottomBarManager.BottomBarButtonStyle.Filled
        )
    }
}


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
        val additionalInfo: Map<String, Any?> = emptyMap(),
        val actions: List<BottomBarAction> = emptyList(),
        val showCancel: Boolean,
        val onClose: (selection: SelectedBottomBarAction) -> Unit = { },
        val onTimeout: () -> Unit = { },
        val type: BottomBarMessageType = BottomBarMessageType.DESTRUCTIVE,
        val isError: Boolean = type == BottomBarMessageType.ERROR,
        val isDismissible: Boolean = true,
        val showScrim: Boolean = true,
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
            showScrim: Boolean = true,
            timeoutSeconds: Int? = null,
            id: Long = UUID.randomUUID().mostSignificantBits
        ) : this(
            title = title,
            subtitle = subtitle,
            actions = buildList {
                if (positiveText.isNotBlank()) {
                    add(BottomBarAction(positiveText, positiveStyle, true, onPositive))
                }

                if (negativeText.isNotBlank()) {
                    add(BottomBarAction(negativeText, negativeStyle, true, onNegative))
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

    private val _messages: MutableStateFlow<List<BottomBarMessage>> = MutableStateFlow(emptyList())
    val messages: StateFlow<List<BottomBarMessage>> get() = _messages.asStateFlow()

    private fun showMessage(bottomBarMessage: BottomBarMessage) {
        _messages.update { currentMessages ->
            currentMessages + bottomBarMessage
        }
    }

    private fun showMessage(
        title: String,
        subtitle: String,
        actions: List<BottomBarAction>,
        showCancel: Boolean = false,
        isDismissible: Boolean = true,
        showScrim: Boolean = true,
        type: BottomBarMessageType = BottomBarMessageType.DESTRUCTIVE,
        onTimeout: () -> Unit = { },
        timeoutSeconds: Int? = null,
        onClose: (SelectedBottomBarAction) -> Unit = { }
    ) {
        showMessage(
            BottomBarMessage(
                title = title,
                subtitle = subtitle,
                showCancel = showCancel,
                type = type,
                actions = actions,
                isDismissible = isDismissible,
                showScrim = showScrim,
                onClose = onClose,
                onTimeout = onTimeout,
                timeoutSeconds = timeoutSeconds,
            )
        )
    }

    fun showMessage(
        title: String,
        message: String = "",
        actions: List<BottomBarAction> = listOf(BottomBarAction.Ok),
        showCancel: Boolean = false,
        showScrim: Boolean = true,
        isDismissible: Boolean = true,
        onTimeout: () -> Unit = { },
        timeoutSeconds: Int? = null,
        onDismiss: (SelectedBottomBarAction) -> Unit = { }
    ) {
        showMessage(
            type = BottomBarMessageType.DEFAULT,
            title = title,
            subtitle = message,
            actions = actions,
            showCancel = showCancel,
            isDismissible = isDismissible,
            showScrim = showScrim,
            onClose = onDismiss,
            onTimeout = onTimeout,
            timeoutSeconds = timeoutSeconds,
        )
    }

    fun showInfo(
        title: String,
        message: String,
        actions: List<BottomBarAction> = listOf(BottomBarAction.Ok),
        showCancel: Boolean = false,
        showScrim: Boolean = true,
        isDismissible: Boolean = true,
        onTimeout: () -> Unit = { },
        timeoutSeconds: Int? = null,
        onDismiss: (SelectedBottomBarAction) -> Unit = { }
    ) {
        showMessage(
            title = title,
            subtitle = message,
            type = BottomBarMessageType.INFO,
            actions = actions,
            showCancel = showCancel,
            isDismissible = isDismissible,
            showScrim = showScrim,
            onClose = onDismiss,
            onTimeout = onTimeout,
            timeoutSeconds = timeoutSeconds,
        )
    }

    fun showSuccess(
        title: String,
        message: String,
        actions: List<BottomBarAction> = listOf(BottomBarAction.Ok),
        showCancel: Boolean = false,
        isDismissible: Boolean = true,
        showScrim: Boolean = true,
        onTimeout: () -> Unit = { },
        timeoutSeconds: Int? = null,
        onDismiss: (SelectedBottomBarAction) -> Unit = { }
    ) {
        showMessage(
            BottomBarMessage(
                title = title,
                subtitle = message,
                showCancel = showCancel,
                type = BottomBarMessageType.SUCCESS,
                actions = actions,
                isDismissible = isDismissible,
                showScrim = showScrim,
                onClose = onDismiss,
                onTimeout = onTimeout,
                timeoutSeconds = timeoutSeconds,
            )
        )
    }

    fun showAlert(
        title: String,
        message: String,
        additionalInfo: Map<String, Any?> = emptyMap(),
        actions: List<BottomBarAction> = listOf(BottomBarAction.Ok),
        showCancel: Boolean = false,
        isDismissible: Boolean = true,
        showScrim: Boolean = true,
        onTimeout: () -> Unit = { },
        timeoutSeconds: Int? = null,
        onDismiss: (fromAction: Boolean) -> Unit = { },
    ) {
        showMessage(
            BottomBarMessage(
                title = title,
                subtitle = message,
                additionalInfo = additionalInfo,
                showCancel = showCancel,
                actions = actions,
                type = BottomBarMessageType.DESTRUCTIVE,
                isDismissible = isDismissible,
                showScrim = showScrim,
                onClose = { onDismiss(it.index != -1) },
                onTimeout = onTimeout,
                timeoutSeconds = timeoutSeconds,
            )
        )
    }

    /**
     * This replaces the error messaging from [TopBarManager] into a simpler, easy-to-reach
     * bottom anchored error message.
     *
     * Errors are type of [BottomBarMessage] that are by design ALWAYS:
     * - Dismissible
     * - Scrimmed
     * - Timeout-able
     * - Include "OK" button
     *
     * Additional [BottomBarAction]'s can be included via [actions] and dismiss callbacks
     * are available via [onDismiss].
     */
    fun showError(
        title: String,
        message: String,
        additionalInfo: Map<String, Any?> = emptyMap(),
        actions: List<BottomBarAction> = listOf(BottomBarAction.Ok),
        showCancel: Boolean = false,
        onDismiss: (fromAction: Boolean) -> Unit = { },
    ) {
        showMessage(
            BottomBarMessage(
                title = title,
                subtitle = message,
                additionalInfo = additionalInfo,
                showCancel = showCancel,
                actions = actions,
                type = BottomBarMessageType.ERROR,
                isDismissible = true,
                showScrim = true,
                onClose = { onDismiss(it.index != -1) }
            )
        )
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
        ERROR,
        WARNING,
        INFO,
        DEFAULT,
        SUCCESS,
    }

    enum class BottomBarButtonStyle {
        Filled, Filled50, Text, Outlined
    }
}