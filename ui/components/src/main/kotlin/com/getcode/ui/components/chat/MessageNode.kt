package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Sender
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.messagecontents.AnnouncementMessage
import com.getcode.ui.components.chat.messagecontents.DeletedMessage
import com.getcode.ui.components.chat.messagecontents.EncryptedContent
import com.getcode.ui.components.chat.messagecontents.MessagePayment
import com.getcode.ui.components.chat.messagecontents.MessageText
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

    val ContentStyle: TextStyle
        @Composable get() = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W500)
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
    contents: MessageContent,
    isDeleted: Boolean,
    date: Instant,
    sender: Sender,
    status: MessageStatus,
    showStatus: Boolean,
    isPreviousSameMessage: Boolean,
    isNextSameMessage: Boolean,
    isInteractive: Boolean,
    modifier: Modifier = Modifier,
    contentStyle: TextStyle = MessageNodeDefaults.ContentStyle,
    openMessageControls: () -> Unit,
) {
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .padding(vertical = CodeTheme.dimens.grid.x1)
    ) {
        BoxWithConstraints(modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset)) {
            val scope = rememberMessageNodeScope(contents = contents, boxScope = this)

            with(scope) {
                if (isDeleted) {
                    DeletedMessage(
                        modifier = Modifier.fillMaxWidth(),
                        isFromSelf = sender.isSelf,
                        date = date,
                    )
                } else {
                    val contentsWithSender: (String) -> String = { contents: String ->
                        if (sender.displayName != null) {
                            "${sender.displayName}: $contents"
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
                                contentStyle = contentStyle,
                                date = date,
                                status = status,
                                isFromSelf = sender.isSelf,
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
                                contentStyle = contentStyle,
                                date = date,
                                status = status,
                                isFromSelf = sender.isSelf,
                                showStatus = showStatus,
                                isInteractive = isInteractive,
                                showControls = openMessageControls
                            )
                        }

                        is MessageContent.RawText -> {
                            MessageText(
                                modifier = Modifier.fillMaxWidth(),
                                content = contentsWithSender(contents.value),
                                contentStyle = contentStyle,
                                date = date,
                                status = status,
                                isFromSelf = sender.isSelf,
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

        if (!isDeleted) {
            when {
                sender.isHost && !sender.isSelf -> {
                    Image(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .offset(
                                x = -(CodeTheme.dimens.grid.x1),
                                y = -(CodeTheme.dimens.grid.x1)
                            )
                            .size(CodeTheme.dimens.staticGrid.x4)
                            .background(color = Color(0xFFE9C432), shape = CircleShape)
                            .padding(4.dp),
                        painter = painterResource(R.drawable.ic_crown),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(CodeTheme.colors.brand)
                    )
                }
            }
        }
    }
}