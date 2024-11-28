package xyz.flipchat.app.features.chat.conversation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
            data = state.imageUri ?: state.conversationId,
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
                text = state.title,
                style = CodeTheme.typography.screenTitle.copy(fontSize = 18.sp)
            )

            val memberCount = remember(state.members) {
                state.members
            }

            Text(
                text = pluralStringResource(R.plurals.title_conversationMemberCount, memberCount, memberCount),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}