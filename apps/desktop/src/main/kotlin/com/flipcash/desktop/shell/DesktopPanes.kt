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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            .padding(top = LocalTitleBarInset.current)
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

/**
 * Chat (Chats node 12627:208): list and conversation side by side from 600 wide, the list 280 wide at
 * medium and 360 at expanded, messages capped at 720. At compact a conversation opens over the list.
 */
@Composable
fun ChatPane(widthClass: WidthClass, modifier: Modifier = Modifier) {
    var conversationOpen by remember { mutableStateOf(false) }
    val list: @Composable (Modifier) -> Unit = { m ->
        PlaceholderPane(
            title = "Chats",
            blockedBy = ChatBlockers,
            modifier = m,
            action = "Open a conversation" to { conversationOpen = true },
        )
    }
    val conversation: @Composable (Modifier, (() -> Unit)?) -> Unit = { m, onBack ->
        Box(m, contentAlignment = Alignment.TopCenter) {
            PlaceholderPane(
                title = "Conversation",
                blockedBy = ChatBlockers,
                modifier = Modifier.widthIn(max = DesktopDimens.MessagesMaxWidth),
                onBack = onBack,
            )
        }
    }
    when (widthClass) {
        WidthClass.Compact ->
            if (conversationOpen) conversation(modifier.fillMaxSize()) { conversationOpen = false }
            else list(modifier.fillMaxSize())
        WidthClass.Medium, WidthClass.Expanded -> Row(modifier.fillMaxSize()) {
            val listWidth =
                if (widthClass == WidthClass.Medium) DesktopDimens.ChatListMedium else DesktopDimens.ChatListExpanded
            list(Modifier.width(listWidth).fillMaxHeight())
            PaneDivider()
            conversation(Modifier.weight(1f).fillMaxHeight(), null)
        }
    }
}

/** You: the mobile You screen, with Settings behind its gear. */
@Composable
fun YouPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        PlaceholderPane(
            title = "You",
            blockedBy = "The You screen (tip card and profile) lives in :apps:flipcash:features:tipping " +
                "and :apps:flipcash:features:myaccount, and Settings in :apps:flipcash:features:settings, " +
                "all Android-only.",
            modifier = Modifier.widthIn(max = DesktopDimens.MoneySinglePaneMaxWidth),
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

@Composable
internal fun CenteredColumn(maxWidth: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) { content() }
    }
}
