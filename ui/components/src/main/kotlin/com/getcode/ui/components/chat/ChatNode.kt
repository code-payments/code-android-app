package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.getcode.model.chat.Chat
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Pointer
import com.getcode.model.chat.isConversation
import com.getcode.model.chat.self
import com.getcode.model.uuid
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.utils.localized
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.utils.rememberedClickable
import com.getcode.util.DateUtils
import com.getcode.util.formatTimeRelatively

object ChatNodeDefaults {
    val UnreadIndicator: Color = Color(0xFF31BB00)
}

@Composable
fun ChatNode(
    modifier: Modifier = Modifier,
    chat: Chat,
    showAvatar: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .rememberedClickable { onClick() }
            .padding(
                vertical = CodeTheme.dimens.grid.x3,
                horizontal = CodeTheme.dimens.inset
            ),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showAvatar) {
            val imageModifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x10)
                .clip(CircleShape)

            UserAvatar(modifier = imageModifier, data = chat.imageData)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(
                CodeTheme.dimens.grid.x1,
                Alignment.CenterVertically
            ),
        ) {
            val hasUnreadMessages by remember(chat.unreadCount) {
                derivedStateOf { chat.unreadCount > 0 }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = chat.title.localized,
                    maxLines = 1,
                    style = CodeTheme.typography.textMedium
                )
                chat.lastMessageMillis?.let {
                    val isToday = DateUtils.isToday(it)
                    Text(
                        text = if (isToday) {
                            it.formatTimeRelatively()
                        } else {
                            DateUtils.getDateRelatively(it)
                        },
                        style = CodeTheme.typography.textSmall,
                        color = if (hasUnreadMessages) ChatNodeDefaults.UnreadIndicator else CodeTheme.colors.textSecondary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                val (preview, inlineContent) = chat.messagePreview

                Text(
                    modifier = Modifier.weight(1f),
                    text = preview,
                    inlineContent = inlineContent,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (chat.isMuted) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "chat is muted",
                        tint = CodeTheme.colors.brandLight
                    )
                } else {
                    Badge(
                        Modifier
                            .padding(end = CodeTheme.dimens.grid.x1),
                        count = chat.unreadCount,
                        color = ChatNodeDefaults.UnreadIndicator
                    )
                }
            }
        }
    }
}

private val Chat.messagePreview: Pair<AnnotatedString, Map<String, InlineTextContent>>
    @Composable get() {
        val contents = newestMessage?.contents ?: return AnnotatedString("No content") to emptyMap()

        var filtered: List<MessageContent> = contents.filterIsInstance<MessageContent.Localized>()
        if (filtered.isEmpty()) {
            filtered = contents
        }

        val selfMember = self
        val pointer =
            selfMember?.pointers.orEmpty().find { it.messageId == newestMessage?.id?.uuid }

        // joinToString does expose a Composable scoped lambda
        @Suppress("SimplifiableCallChain")
        val messageBody = filtered.map { it.localizedText }.joinToString(" ")

        val textStyle = CodeTheme.typography.textMedium
        return if (pointer != null && pointer !is Pointer.Unknown && isConversation) {
            val string = buildAnnotatedString {
                appendInlineContent("status", "status")
                append(" ")
                append(messageBody)
            }

            string to mapOf(
                "status" to InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    Image(
                        painter = painterResource(
                            id = when (pointer) {
                                is Pointer.Delivered -> R.drawable.ic_message_status_delivered
                                is Pointer.Read -> R.drawable.ic_message_status_read
                                is Pointer.Sent -> R.drawable.ic_message_status_sent
                                else -> -1
                            }
                        ),
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = ""
                    )
                }
            )
        } else {
            AnnotatedString(messageBody) to emptyMap()
        }
    }