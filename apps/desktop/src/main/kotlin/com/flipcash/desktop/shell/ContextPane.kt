package com.flipcash.desktop.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    Box(modifier.padding(top = LocalTitleBarInset.current)) {
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
                onBack = { nav.back() },
            )
        }
    }
}
