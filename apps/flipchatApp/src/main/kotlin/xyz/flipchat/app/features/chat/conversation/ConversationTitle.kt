package xyz.flipchat.app.features.chat.conversation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.UserAvatar
import xyz.flipchat.app.R

@Composable
internal fun ConversationTitle(
    modifier: Modifier = Modifier,
    state: ConversationViewModel.State,
) {
    val listenerCount = remember(state.members) {
        state.listeners
    }

    ConversationTitle(
        modifier = modifier,
        imageUri = state.imageUri ?: state.conversationId,
        title = state.title,
        listenerCount = listenerCount,
    )
}

@Composable
internal fun ConversationTitle(
    modifier: Modifier = Modifier,
    imageUri: Any?,
    title: String,
    listenerCount: Int?,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
    ) {
        UserAvatar(
            modifier = Modifier
                .padding(start = CodeTheme.dimens.grid.x2)
                .size(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
            data = imageUri,
            overlay = {
                Image(
                    modifier = Modifier.padding(5.dp),
                    painter = painterResource(R.drawable.ic_fc_chats),
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null,
                )
            }
        )
        Column {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = CodeTheme.typography.screenTitle.copy(fontSize = 18.sp)
            )

            Text(
                text = if (listenerCount != null) {
                    pluralStringResource(
                        R.plurals.title_roomInfoListenerCount,
                        listenerCount,
                        listenerCount
                    )
                } else {
                    ""
                },
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}