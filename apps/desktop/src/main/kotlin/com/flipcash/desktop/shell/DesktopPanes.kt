package com.flipcash.desktop.shell

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

/**
 * A pane whose real screen does not compile for the JVM yet. It says which screen belongs here and
 * which modules hold it back, so the shell can be judged on layout while the screens catch up.
 */
@Composable
fun PlaceholderPane(
    title: String,
    blockedBy: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("placeholder_$title"),
    ) {
        if (onBack != null) {
            TextButton("‹ Back", onBack)
            Spacer(Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, CodeTheme.colors.divider, RoundedCornerShape(12.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = CodeTheme.typography.screenTitle, color = CodeTheme.colors.textMain)
                Text("Placeholder", style = CodeTheme.typography.caption, color = CodeTheme.colors.textTertiary)
                Text(
                    text = blockedBy,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                action?.let { (label, onClick) ->
                    Spacer(Modifier.height(8.dp))
                    TextButton(label, onClick)
                }
            }
        }
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = CodeTheme.typography.textMedium,
        color = CodeTheme.colors.textMain,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

private const val ChatBlockers =
    "The chat list and conversation live in :apps:flipcash:features:messenger and " +
        ":apps:flipcash:shared:chat / :chat-ui, which build for Android only."

/** The chat list in the sidebar, until the chat module builds for desktop (roadmap Plan 3b). */
@Composable
fun ChatListPlaceholder(onOpenChat: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderPane(
        title = "Chats",
        blockedBy = ChatBlockers,
        modifier = modifier,
        action = "Open a conversation" to onOpenChat,
    )
}

/** The conversation in the main pane, capped at the message column width. */
@Composable
fun ConversationPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        PlaceholderPane(
            title = "Conversation",
            blockedBy = ChatBlockers,
            modifier = Modifier.widthIn(max = DesktopDimens.MessagesMaxWidth),
        )
    }
}

@Composable
fun PaneDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .fillMaxHeight()
            .border(0.5.dp, CodeTheme.colors.divider),
    )
}
