package com.flipcash.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.theme.White05
import com.getcode.theme.White50

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

/**
 * The main pane's toolbar content for the open chat (node 13000:224): avatar and name. The design's
 * "6 people" line has no data behind it, since [ConversationReference] carries no member count, so
 * the name is centred on the avatar instead.
 */
@Composable
fun RowScope.ConversationToolbarTitle(chat: ConversationReference?) {
    if (chat == null) {
        Spacer(Modifier.weight(1f))
        return
    }
    ContactAvatar(
        image = chat.image,
        displayName = chat.name.orEmpty(),
        access = chat.avatarAccess,
        modifier = Modifier.size(28.dp).clip(CircleShape),
    )
    Text(
        text = chat.name.orEmpty(),
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textMain,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).testTag("toolbar_title"),
    )
}

/**
 * The conversation in the main pane. Messages need `ChatViewModel`, which is Android-only until it
 * moves to commonMain, so the pane says so in one quiet line and shows the composer disabled
 * (node 13000:282). With no chat open it asks for one.
 */
@Composable
fun ConversationPane(chat: ConversationReference?, modifier: Modifier = Modifier) {
    Column(modifier.testTag("conversation_pane")) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = if (chat == null) "Select a conversation" else "Messages are coming to desktop soon.",
                style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.Normal),
                color = CodeTheme.colors.textSecondary,
            )
        }
        if (chat != null) {
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
                ComposerShell(Modifier.widthIn(max = 640.dp))
            }
        }
    }
}

/** The composer drawn but not usable: the field with its attach button, and Give beside it. */
@Composable
private fun ComposerShell(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().alpha(0.5f).testTag("composer_disabled").clearAndSetSemantics { disabled() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(White05)
                .border(1.dp, White10, RoundedCornerShape(20.dp))
                .padding(start = 4.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(ChipFill), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = CodeTheme.colors.textMain, modifier = Modifier.size(18.dp))
            }
            Text("Message", style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp), color = White50)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(ChipFill), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AttachMoney, contentDescription = null, tint = CodeTheme.colors.textMain, modifier = Modifier.size(20.dp))
        }
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
