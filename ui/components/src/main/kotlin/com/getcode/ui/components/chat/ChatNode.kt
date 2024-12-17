package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.Badge
import com.getcode.ui.components.Pill
import com.getcode.ui.components.R
import com.getcode.ui.utils.rememberedClickable
import com.getcode.util.DateUtils
import com.getcode.util.formatTimeRelatively

@Composable
fun ChatNode(
    title: String,
    modifier: Modifier = Modifier,
    avatar: Any? = null,
    avatarIconWhenFallback: @Composable BoxScope.() -> Unit = { },
    messagePreview: Pair<AnnotatedString, Map<String, InlineTextContent>>,
    titleTextStyle: TextStyle = CodeTheme.typography.textMedium,
    messageTextStyle: TextStyle = CodeTheme.typography.textMedium,
    messageMinLines: Int = 2,
    timestamp: Long? = null,
    isMuted: Boolean = false,
    isHost: Boolean = false,
    unreadCount: Int = 0,
    showMoreUnread: Boolean = unreadCount > 99,
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
        avatar?.let {
            val imageModifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x12)
                .clip(CircleShape)

            Box(
                modifier = Modifier
                    .padding(top = CodeTheme.dimens.grid.x1)
            ) {
                UserAvatar(modifier = imageModifier, data = it, overlay = avatarIconWhenFallback)

                if (isHost) {
                    Image(
                        modifier = Modifier
                            .align(Alignment.TopStart)
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

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(
                CodeTheme.dimens.grid.x1,
                Alignment.CenterVertically
            ),
        ) {
            val hasUnreadMessages = remember(unreadCount) { unreadCount > 0 }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = titleTextStyle
                )
                Spacer(Modifier.weight(1f))
                timestamp?.let {
                    val isToday = DateUtils.isToday(it)
                    Text(
                        text = if (isToday) {
                            it.formatTimeRelatively()
                        } else {
                            DateUtils.getDateRelatively(it)
                        },
                        style = CodeTheme.typography.textSmall,
                        color = when {
                            hasUnreadMessages -> CodeTheme.colors.indicator
                            else -> CodeTheme.colors.textSecondary
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
                verticalAlignment = Alignment.Top
            ) {

                val (preview, inlineContent) = messagePreview

                Text(
                    modifier = Modifier.weight(1f),
                    text = preview,
                    inlineContent = inlineContent,
                    style = messageTextStyle,
                    color = CodeTheme.colors.textSecondary,
                    minLines = messageMinLines.coerceAtMost(2),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isMuted) {
                        Icon(
                            modifier = Modifier
                                .size(CodeTheme.dimens.staticGrid.x4),
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "chat is muted",
                            tint = CodeTheme.colors.textSecondary
                        )
                    }

                    Badge(
                        count = unreadCount,
                        showMoreUnread = showMoreUnread,
                        color = CodeTheme.colors.indicator,
                        contentColor = Color.White
                    )
                }

            }
        }
    }
}

//private val Chat.messagePreview: Pair<AnnotatedString, Map<String, InlineTextContent>>
//    @Composable get() {
//        val contents = newestMessage?.contents ?: return AnnotatedString("No content") to emptyMap()
//
//        var filtered: List<MessageContent> = contents.filterIsInstance<MessageContent.Localized>()
//        if (filtered.isEmpty()) {
//            filtered = contents
//        }
//
//        val selfMember = self
//        val pointer =
//            selfMember?.pointers.orEmpty().find { it.messageId == newestMessage?.id?.uuid }
//
//        // joinToString does expose a Composable scoped lambda
//        @Suppress("SimplifiableCallChain")
//        val messageBody = filtered.map { it.localizedText }.joinToString(" ")
//
//        val textStyle = CodeTheme.typography.textMedium
//        return if (pointer != null && pointer !is Pointer.Unknown && isConversation) {
//            val string = buildAnnotatedString {
//                appendInlineContent("status", "status")
//                append(" ")
//                append(messageBody)
//            }
//
//            string to mapOf(
//                "status" to InlineTextContent(
//                    Placeholder(
//                        textStyle.fontSize,
//                        textStyle.fontSize,
//                        PlaceholderVerticalAlign.TextCenter
//                    )
//                ) {
//                    Image(
//                        painter = painterResource(
//                            id = when (pointer) {
//                                is Pointer.Delivered -> R.drawable.ic_message_status_delivered
//                                is Pointer.Read -> R.drawable.ic_message_status_read
//                                is Pointer.Sent -> R.drawable.ic_message_status_sent
//                                else -> -1
//                            }
//                        ),
//                        modifier = Modifier.fillMaxSize(),
//                        contentDescription = ""
//                    )
//                }
//            )
//        } else {
//            AnnotatedString(messageBody) to emptyMap()
//        }
//    }