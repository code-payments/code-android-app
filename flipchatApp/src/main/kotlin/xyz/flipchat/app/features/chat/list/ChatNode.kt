package xyz.flipchat.app.features.chat.list

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FixedThreshold
import androidx.compose.material.Icon
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.asFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.currentOrThrow
import com.getcode.model.chat.MessageContent
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import xyz.flipchat.app.R
import com.getcode.ui.components.chat.utils.localizedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.ui.LocalUserManager
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatNode(
    chat: ConversationWithMembersAndLastMessage,
    modifier: Modifier = Modifier,
    onSwipedToStart: () -> Unit = { },
    onClick: () -> Unit,
) {
    val userManager = LocalUserManager.currentOrThrow

    val dismissState = remember(chat.id) {
        DismissState(
            initialValue = DismissValue.Default,
            confirmStateChange = {
                if (it == DismissValue.DismissedToStart) {
                    onSwipedToStart()
                    true
                } else false
            }
        )
    }

    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }
            .filter { it == DismissValue.DismissedToStart }
            .collect {
                dismissState.reset()
            }
    }

    SwipeToDismiss(
        state = dismissState,
        dismissThresholds = { FixedThreshold(150.dp) },
        directions = if (chat.canChangeMuteState) setOf(DismissDirection.EndToStart) else emptySet(),
        background = {
            if (chat.canChangeMuteState) {
                DismissBackground(dismissState, chat.isMuted)
            }
        }
    ) {
        com.getcode.ui.components.chat.ChatNode(
            modifier = modifier.background(CodeTheme.colors.background),
            title = chat.title,
            messagePreview = chat.messagePreview,
            avatar = chat.imageUri ?: chat.id,
            avatarIconWhenFallback = {
                Image(
                    modifier = Modifier.padding(5.dp),
                    painter = painterResource(R.drawable.ic_fc_chats),
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null,
                )
            },
            timestamp = chat.lastMessage?.message?.dateMillis,
            isMuted = false,
            isHost = chat.ownerId == userManager.userId,
            unreadCount = chat.unreadCount,
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DismissBackground(dismissState: DismissState, isMuted: Boolean) {
    val color = when (dismissState.dismissDirection) {
        DismissDirection.EndToStart -> Color(0xFF251B4C)
        else -> CodeTheme.colors.background
    }
    val direction = dismissState.dismissDirection

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(end = CodeTheme.dimens.inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (direction == DismissDirection.EndToStart) {
            Text(
               text = if (isMuted) "Unmute" else "Mute",
                color = Color.White,
                style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W700)
            )
        }
    }
}

private val ConversationWithMembersAndLastMessage.messagePreview: Pair<AnnotatedString, Map<String, InlineTextContent>>
    @Composable get() {
        val contents = lastMessage?.contents ?: return AnnotatedString("No content") to emptyMap()

        var filtered: List<MessageContent> = contents.filterIsInstance<MessageContent.Localized>()
        if (filtered.isEmpty()) {
            filtered = contents
        }

        // joinToString does expose a Composable scoped lambda
        @Suppress("SimplifiableCallChain")
        val messageBody = filtered.map { it.localizedText }.joinToString(" ")

        return AnnotatedString(messageBody) to emptyMap()
    }