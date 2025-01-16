package xyz.flipchat.app.features.chat.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FixedThreshold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.currentOrThrow
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.util.vibration.LocalVibrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import xyz.flipchat.app.R
import xyz.flipchat.app.ui.LocalUserManager
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatNode(
    chat: ConversationWithMembersAndLastMessage,
    modifier: Modifier = Modifier,
    onToggleMute: (mute: Boolean) -> Unit = { },
    onClick: () -> Unit,
) {
    val userManager = LocalUserManager.currentOrThrow

    val dismissState = rememberChatDismissState({ chat.isMuted }, onToggleMute)

    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }
            .filter { it == DismissValue.DismissedToStart }
            .collect {
                dismissState.animateTo(DismissValue.Default)
            }
    }

    var muteContentState by remember { mutableStateOf(chat.isMuted) }

    LaunchedEffect(chat.id, chat.isMuted) {
        delay(400)
        muteContentState = chat.isMuted
    }

    SwipeToDismiss(
        state = dismissState,
        dismissThresholds = { FixedThreshold(150.dp) },
        directions = if (chat.canChangeMuteState) setOf(DismissDirection.EndToStart) else emptySet(),
        background = {
            if (chat.canChangeMuteState) {
                DismissBackground(dismissState, muteContentState)
            }
        }
    ) {
        com.getcode.ui.components.chat.ChatNode(
            modifier = modifier.background(CodeTheme.colors.background),
            title = chat.title,
            messagePreview = chat.messagePreview,
            messageTextStyle = CodeTheme.typography.textSmall,
            messageMinLines = 2,
            avatar = chat.imageUri ?: chat.id,
            avatarIconWhenFallback = {
                Image(
                    modifier = Modifier.padding(5.dp),
                    painter = painterResource(R.drawable.ic_fc_chats),
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null,
                )
            },
            timestamp = chat.lastMessage?.dateMillis,
            isMuted = muteContentState,
            isHost = chat.ownerId == userManager.userId,
            unreadCount = chat.unreadCount,
            showMoreUnread = chat.hasMoreUnread,
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
            Image(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                colorFilter = ColorFilter.tint(Color.White),
                contentDescription = null
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun rememberChatDismissState(
    isChatMuted: () -> Boolean,
    onToggleMute: (mute: Boolean) -> Unit
): DismissState {
    val mutedState by rememberUpdatedState(isChatMuted())
    val vibrator = LocalVibrator.current
    return remember {
        DismissState(
            initialValue = DismissValue.Default,
            confirmStateChange = {
                if (it == DismissValue.DismissedToStart) {
                    onToggleMute(!mutedState)
                    vibrator.tick()
                    true
                } else false
            }
        )
    }
}

private val ConversationWithMembersAndLastMessage.messagePreview: Pair<AnnotatedString, Map<String, InlineTextContent>>
    @Composable get() {
        val user = LocalUserManager.currentOrThrow
        val contents = messageContentPreview ?: return AnnotatedString("No content") to emptyMap()
        val messageBody = if (this.lastMessage?.isDeleted == true) {
            when {
                user.isSelf(this.lastMessage?.deletedBy) -> {
                    stringResource(R.string.title_messageDeletedByYou)
                }
                this.ownerId == this.lastMessage?.deletedBy -> {
                    stringResource(R.string.title_messageDeletedByHost)
                }
                else -> {
                    stringResource(R.string.title_messageWasDeleted)
                }
            }
        } else {
            contents.localizedText
        }

        return AnnotatedString(messageBody) to emptyMap()
    }