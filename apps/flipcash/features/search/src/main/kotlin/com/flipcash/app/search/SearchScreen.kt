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
import com.flipcash.app.core.extensions.navigateAll
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
 * [field] wraps the search field, so the host can grow it out of the nav bar's search button, and
 * [close] replaces the plain pop so the host can shrink it back as the keyboard drops. [backButton] draws the bar's leading button, which closes Search; the host passes the tab
 * Search was opened from, so the way back reads as that tab. Without one it is a chats glyph.
 * [barButtons] goes on the leading button and ✕, so the host can bring them in with the field.
 */
@Composable
fun SearchScreen(
    field: SearchFieldHost = DefaultSearchFieldHost,
    close: SearchClose? = null,
    backButton: SearchBackButton? = null,
    barButtons: Modifier = Modifier,
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
            // Switch to the Money tab and push the currency there, so Search is left behind
            // (a tab switch replaces the stack) and back lands on the wallet. Discovery is the
            // push-presented entry; Wallet would draw the card-expand ✕ with no card to shrink to.
            navigator.navigateAll(
                listOf(
                    AppRoute.Tabs.Wallet,
                    AppRoute.Token.Info(token.address, TokenInfoEntry.Discovery),
                )
            )
        },
        onOpenLink = { link -> externalLinks.openUri(link.url) },
        onClose = { navigator.pop() },
        close = close,
        field = field,
        backButton = backButton,
        barButtons = barButtons,
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

/**
 * Hosts the search field: call `field` with the modifier the host's morph needs. [modifier] holds
 * the field's place in the bar.
 */
typealias SearchFieldHost = @Composable (modifier: Modifier, field: @Composable (Modifier) -> Unit) -> Unit

internal val DefaultSearchFieldHost: SearchFieldHost = { modifier, field -> field(modifier) }

/**
 * Closes Search once its keyboard has been asked down: [raised] is the keyboard's height when it
 * was, and [imeBottom] reads its height now (0 once it's gone, or if it never was up).
 */
typealias SearchClose = suspend (raised: Int, imeBottom: () -> Int) -> Unit
