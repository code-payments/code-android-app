package com.getcode.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.getcode.BuildConfig
import com.getcode.model.Chat
import com.getcode.model.MessageContent
import com.getcode.model.Title
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.utils.debugBounds
import com.getcode.ui.utils.rememberedClickable
import com.getcode.util.DateUtils
import com.getcode.util.formatTimeRelatively
import com.getcode.util.toInstantFromMillis
import java.util.Locale

object ChatNodeDefaults {
    val UnreadIndicator: Color = Color(0xFF31BB00)
}

@Composable
fun ChatNode(
    modifier: Modifier = Modifier,
    chat: Chat,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .rememberedClickable { onClick() }
            .padding(
                vertical = CodeTheme.dimens.grid.x3,
                horizontal = CodeTheme.dimens.inset
            ),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        val hasUnreadMessages by remember(chat.unreadCount) {
            derivedStateOf { chat.unreadCount > 0 }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = chat.localizedTitle, maxLines = 1, style = CodeTheme.typography.body1)
            chat.lastMessageMillis?.let {
                val isToday = DateUtils.isToday(it)
                Text(
                    text = if (isToday) {
                        it.formatTimeRelatively()
                    } else {
                        DateUtils.getDateRelatively(it)
                    },
                    style = CodeTheme.typography.body2,
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
                style = CodeTheme.typography.body1,
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


private val Chat.localizedTitle: String
    @Composable get() {
        return title.localized
    }

val Title?.localized: String
    @Composable get() = when (val t = this) {
        is Title.Domain -> {
            t.value.capitalize(Locale.getDefault())
        }

        is Title.Localized -> {
            with(LocalContext.current) {
                val resId = resources.getIdentifier(
                    t.value,
                    "string",
                    BuildConfig.APPLICATION_ID
                ).let { if (it == 0) null else it }

                resId?.let { getString(it) } ?: t.value
            }
        }

        else -> "Anonymous"
    }

private val Chat.messagePreview: String
    @Composable get() {
        val contents = messages.lastOrNull()?.contents ?: return "No content"

        var filtered: List<MessageContent> = contents.filterIsInstance<MessageContent.Localized>()
        if (filtered.isEmpty()) {
            filtered = contents
        }

        return filtered.map { it.localizedText }.joinToString(" ")
    }