package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.model.MessageStatus
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Reference
import com.getcode.theme.BrandDark
import com.getcode.theme.ChatOutgoing
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.utils.localizedText
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

    val color = if (contents is MessageContent.Exchange && !contents.verb.increasesBalance) {
        ChatOutgoing
    } else {
        BrandDark
    }

    val isAnnouncement: Boolean
        @Composable get() = remember {
            when (contents) {
                is MessageContent.IdentityRevealed -> true
                is MessageContent.ThankYou -> true
                else -> false
            }
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
    date: Instant,
    status: MessageStatus,
    isPreviousSameMessage: Boolean,
    isNextSameMessage: Boolean,
    showTipActions: Boolean = true,
    openMessageChat: (Reference) -> Unit = { },
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(vertical = CodeTheme.dimens.grid.x1)
    ) {
        val scope = rememberMessageNodeScope(contents = contents, boxScope = this)

        with(scope) {
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
                        showTipActions = showTipActions,
                        status = status,
                        date = date,
                        openMessageChat = { openMessageChat(contents.reference) }
                    )
                }

                is MessageContent.Localized -> {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        content = contents.localizedText,
                        date = date,
                        status = status,
                        isFromSelf = contents.isFromSelf
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
                        content = contents.data,
                        date = date,
                        status = status,
                        isFromSelf = contents.isFromSelf
                    )
                }

                is MessageContent.IdentityRevealed -> {
                    AnnouncementMessage(
                        modifier = Modifier.align(Alignment.Center),
                        text = contents.localizedText
                    )
                }
                is MessageContent.RawText -> {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        content = contents.value,
                        date = date,
                        status = status,
                        isFromSelf = contents.isFromSelf
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