package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.messagecontents.AnnouncementMessage
import com.getcode.ui.components.chat.messagecontents.DeletedMessage
import com.getcode.ui.components.chat.messagecontents.EncryptedContent
import com.getcode.ui.components.chat.messagecontents.MessageControlAction
import com.getcode.ui.components.chat.messagecontents.MessageControls
import com.getcode.ui.components.chat.messagecontents.MessagePayment
import com.getcode.ui.components.chat.messagecontents.MessageText
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.decor.LocalScrimHandler
import com.getcode.ui.decor.ScrimStateChange
import com.getcode.ui.utils.keyboardAsState
import com.getcode.ui.utils.measured
import com.getcode.ui.window.ShowcaseDropdownMenu
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant

object MessageNodeDefaults {

    val DefaultShape: CornerBasedShape
        @Composable get() = CodeTheme.shapes.small
    val PreviousSameShape: CornerBasedShape
        @Composable get() = DefaultShape.copy(topStart = CornerSize(3.dp))

    val NextSameShape: CornerBasedShape
        @Composable get() = DefaultShape.copy(bottomStart = CornerSize(3.dp))

    val MiddleSameShape: CornerBasedShape
        @Composable get() = DefaultShape.copy(
            topStart = CornerSize(3.dp),
            bottomStart = CornerSize(3.dp)
        )
}

class MessageNodeScope(
    private val contents: MessageContent,
    private val boxScope: BoxWithConstraintsScope
) {
    fun Modifier.sizeableWidth() =
        this.widthIn(max = boxScope.maxWidth * 0.85f)

    val isAnnouncement: Boolean
        @Composable get() = remember {
            when (contents) {
                is MessageContent.ThankYou -> true
                else -> false
            }
        }

    val color: Color
        @Composable get() = when {
            isAnnouncement -> CodeTheme.colors.secondary
            contents.isFromSelf -> CodeTheme.colors.secondary
            else -> CodeTheme.colors.brandDark
        }
}

@Composable
private fun rememberMessageNodeScope(
    contents: MessageContent,
    boxScope: BoxWithConstraintsScope
): MessageNodeScope {
    return remember(contents, boxScope) {
        MessageNodeScope(contents, boxScope)
    }
}

@Composable
fun MessageNode(
    modifier: Modifier = Modifier,
    contents: MessageContent,
    isDeleted: Boolean,
    date: Instant,
    isFromSelf: Boolean,
    senderName: String?,
    status: MessageStatus,
    showStatus: Boolean,
    isPreviousSameMessage: Boolean,
    isNextSameMessage: Boolean,
    isInteractive: Boolean,
    openMessageControls: () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(vertical = CodeTheme.dimens.grid.x1)
            .zIndex(8f)
    ) {
        val scope = rememberMessageNodeScope(contents = contents, boxScope = this)

        with(scope) {
            if (isDeleted) {
                DeletedMessage(
                    modifier = Modifier.fillMaxWidth(),
                    isFromSelf = isFromSelf,
                    date = date,
                )
            } else {
                val contentsWithSender: (String) -> String = { contents: String ->
                    if (senderName != null) {
                        "$senderName: $contents"
                    } else {
                        contents
                    }
                }

                when (contents) {
                    is MessageContent.Exchange -> {
                        MessagePayment(
                            modifier = Modifier
                                .align(if (contents.isFromSelf) Alignment.CenterEnd else Alignment.CenterStart)
                                .sizeableWidth()
                                .background(
                                    color = color,
                                    shape = when {
                                        isAnnouncement -> MessageNodeDefaults.DefaultShape
                                        isPreviousSameMessage && isNextSameMessage -> MessageNodeDefaults.MiddleSameShape
                                        isPreviousSameMessage -> MessageNodeDefaults.PreviousSameShape
                                        isNextSameMessage -> MessageNodeDefaults.NextSameShape
                                        else -> MessageNodeDefaults.DefaultShape
                                    }
                                ),
                            contents = contents,
                            status = status,
                            date = date,
                        )
                    }

                    is MessageContent.Localized -> {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            content = contentsWithSender(contents.localizedText),
                            date = date,
                            status = status,
                            isFromSelf = isFromSelf,
                            showStatus = showStatus,
                            isInteractive = isInteractive,
                            showControls = openMessageControls
                        )
                    }

                    is MessageContent.SodiumBox -> {
                        EncryptedContent(
                            modifier = Modifier
                                .align(if (status.isOutgoing()) Alignment.CenterEnd else Alignment.CenterStart)
                                .sizeableWidth()
                                .background(
                                    color = color,
                                    shape = when {
                                        isAnnouncement -> MessageNodeDefaults.DefaultShape
                                        isPreviousSameMessage && isNextSameMessage -> MessageNodeDefaults.MiddleSameShape
                                        isPreviousSameMessage -> MessageNodeDefaults.PreviousSameShape
                                        isNextSameMessage -> MessageNodeDefaults.NextSameShape
                                        else -> MessageNodeDefaults.DefaultShape
                                    }
                                )
                                .padding(CodeTheme.dimens.grid.x2),
                            date = date
                        )
                    }

                    is MessageContent.Decrypted -> {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            content = contentsWithSender(contents.data),
                            date = date,
                            status = status,
                            isFromSelf = isFromSelf,
                            showStatus = showStatus,
                            isInteractive = isInteractive,
                            showControls = openMessageControls
                        )
                    }

                    is MessageContent.RawText -> {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            content = contentsWithSender(contents.value),
                            date = date,
                            status = status,
                            isFromSelf = isFromSelf,
                            showStatus = showStatus,
                            isInteractive = isInteractive,
                            showControls = openMessageControls
                        )
                    }

                    is MessageContent.ThankYou -> {
                        AnnouncementMessage(
                            modifier = Modifier.align(Alignment.Center),
                            text = contents.localizedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageActionsMenu(
    actions: List<MessageControlAction>,
    isVisible: Boolean,
    message: @Composable () -> Unit,
    messageSize: DpSize,
    onDismiss: () -> Unit,
) {
    ShowcaseDropdownMenu(
        modifier = Modifier
            .background(Color.White)
            .fillMaxWidth(0.9f),
        content = {
            Box(Modifier.fillMaxWidth(0.9f)) {
                message()
            }
        },
        contentSize = messageSize,
        spacing = CodeTheme.dimens.grid.x1,
        expanded = isVisible,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        )
    ) {
        actions.fastForEachIndexed { index, action ->
            DropdownMenuItem(
                onClick = {
                    onDismiss()
                    action.onSelect()
                }
            ) {
                Text(
                    text = when (action) {
                        is MessageControlAction.Copy -> "Copy"
                        is MessageControlAction.Delete -> "Delete"
                        is MessageControlAction.RemoveUser -> "Remove ${action.name}"
                    },
                    color = Color.Black,
                    style = CodeTheme.typography.textMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (index < actions.lastIndex) {
                Divider(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.grid.x2),
                    color = Color(0xFFD9D9D9)
                )
            }
        }
    }
}