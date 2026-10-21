package com.flipcash.app.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.links.ExternalLinkUriHandler
import com.flipcash.app.core.links.LocalTrustedWebsites
import com.flipcash.app.core.tokens.TokenInfoEntry
import com.flipcash.app.search.internal.SearchContent
import com.flipcash.app.search.internal.SearchPreloadViewModel
import com.flipcash.app.search.internal.SearchViewModel
import com.flipcash.app.core.chat.ChatIdentifier
import com.getcode.navigation.core.LocalCodeNavigator
import dev.chrisbanes.haze.HazeState

/**
 * Search across chats, currencies, messages and links (nodes 12248:72, 12248:97, 12248:121,
 * 12248:136). The query is held by the screen's view model only, so it is gone once Search is.
 *
 * [fieldModifier] goes on the search field, so the host can grow it out of the nav bar's search
 * button. [backButton] draws the bar's leading button, which closes Search; the host passes the tab
 * Search was opened from, so the way back reads as that tab. Without one it is a chats glyph.
 */
@Composable
fun SearchScreen(
    fieldModifier: Modifier = Modifier,
    backButton: SearchBackButton? = null,
) {
    val viewModel = hiltViewModel<SearchViewModel>()
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val trustedWebsites = LocalTrustedWebsites.current
    // Links here are ones other people typed, so they go through the same leaving-the-app check
    // the transcript uses.
    val externalLinks = remember(context, uriHandler, trustedWebsites) {
        ExternalLinkUriHandler(context, uriHandler, trustedWebsites)
    }

    val chip by viewModel.chip.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    SearchContent(
        query = viewModel.query,
        chip = chip,
        recents = recents,
        results = results,
        onSelectChip = viewModel::selectChip,
        onOpenChat = { chatId, messageId ->
            viewModel.opened(chatId)
            navigator.push(
                AppRoute.Messaging.Chat(
                    identifier = ChatIdentifier.ByChatId(chatId),
                    jumpToMessageId = messageId,
                )
            )
        },
        onOpenCurrency = { token ->
            navigator.push(AppRoute.Token.Info(token.address, TokenInfoEntry.Discovery))
        },
        onOpenLink = { link -> externalLinks.openUri(link.url) },
        onClose = { navigator.pop() },
        fieldModifier = fieldModifier,
        backButton = backButton,
    )
}

/**
 * The leading button of Search's bar. Call [onClick] to close Search; it lowers the keyboard first.
 * [hazeState] is the results', for a button that frosts what scrolls under it.
 */
typealias SearchBackButton = @Composable (onClick: () -> Unit, hazeState: HazeState) -> Unit

/**
 * Starts loading what Search fetches over the network, so Search opens on loaded results rather
 * than a spinner. Drawn by Home, behind the Chats list.
 */
@Composable
fun PreloadSearch() {
    hiltViewModel<SearchPreloadViewModel>()
}
