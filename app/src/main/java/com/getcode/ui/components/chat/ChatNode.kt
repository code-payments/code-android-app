package com.getcode.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.error
import com.getcode.R
import com.getcode.model.chat.Chat
import com.getcode.model.chat.MessageContent
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.ui.components.chat.utils.localized
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.utils.rememberedClickable
import com.getcode.util.DateUtils
import com.getcode.util.formatTimeRelatively
import java.util.UUID

object ChatNodeDefaults {
    val UnreadIndicator: Color = Color(0xFF31BB00)
}

@Composable
fun ChatNode(
    modifier: Modifier = Modifier,
    chat: Chat,
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
        val imageModifier = Modifier
            .size(CodeTheme.dimens.staticGrid.x10)
            .clip(CircleShape)

        UserAvatar(modifier = imageModifier, data = chat.imageData)

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
                        color = if (hasUnreadMessages) ChatNodeDefaults.UnreadIndicator else CodeTheme.colors.brandLight,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = chat.messagePreview,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.brandLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (chat.isMuted) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "chat is muted",
                        tint = BrandLight
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

private val Chat.messagePreview: String
    @Composable get() {
        val contents = newestMessage?.contents ?: return "No content"

        var filtered: List<MessageContent> = contents.filterIsInstance<MessageContent.Localized>()
        if (filtered.isEmpty()) {
            filtered = contents
        }

        return filtered.map { it.localizedText }.joinToString(" ")
    }