package com.flipcash.desktop.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

/**
 * What the context pane shows for [content]. Wallet is the real wallet; the rest are placeholders
 * naming the plan that ports them (roadmap: Info and You in Plan 3b, Currency in Plan 5).
 */
@Composable
fun ContextPane(
    content: ContextContent,
    nav: DesktopNavState,
    wallet: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val back: (() -> Unit)? = if (content is ContextContent.Currency && content.from != null) {
        { nav.back() }
    } else null
    Box(modifier.padding(top = LocalWindowChrome.current.titleBarHeight)) {
        when (content) {
            ContextContent.Wallet -> wallet(Modifier)
            ContextContent.Info -> PlaceholderPane(
                title = "Info",
                blockedBy = "Chat Info needs the chat module on desktop (roadmap Plan 3b).",
            )
            ContextContent.You -> PlaceholderPane(
                title = "You",
                blockedBy = "The profile screen lives in :apps:flipcash:features:myaccount, Android-only " +
                    "until roadmap Plan 3b.",
            )
            is ContextContent.Currency -> PlaceholderPane(
                title = "Currency info",
                blockedBy = "TokenInfoScreen is in :apps:flipcash:shared:tokens androidMain (roadmap Plan 5).",
                onBack = back,
            )
            is ContextContent.Unavailable -> PlaceholderPane(
                title = content.name,
                blockedBy = "This screen has no desktop build yet.",
                onBack = nav::closeContext,
            )
        }
        // Every context pane can be closed: a column has no Esc, and at compact widths the pane
        // covers the toolbar and footer that opened it.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = nav::closeContext)
                .testTag("context_close"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = CodeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
