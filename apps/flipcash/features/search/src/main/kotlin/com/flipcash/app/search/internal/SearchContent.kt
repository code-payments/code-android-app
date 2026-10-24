package com.flipcash.app.search.internal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.flipcash.app.search.SearchBackButton
import com.flipcash.app.search.SearchClose
import com.flipcash.app.search.SearchFieldHost
import com.flipcash.app.search.DefaultSearchFieldHost
import com.getcode.ui.components.BottomScrollEdge
import com.getcode.ui.components.TopScrollEdge
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.foundation.border
import com.getcode.theme.inputColors
import com.getcode.ui.components.chat.ChatInputDefaults
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.TokenIcon
import com.flipcash.app.core.ui.billCardBrush
import com.flipcash.app.core.ui.inDisplayCurrency
import com.flipcash.app.core.util.abbreviated
import com.flipcash.core.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ChatListRow
import com.flipcash.shared.chat.ui.ChatRowSubtitle
import com.flipcash.shared.chat.ui.ChatRowTrailing
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.SubtitleText
import com.flipcash.shared.chat.ui.formatLastActivity
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.components.FilterChip
import com.getcode.ui.components.SearchInput
import com.getcode.ui.components.text.SectionHeader
import kotlin.math.abs

@Composable
internal fun SearchContent(
    query: TextFieldState,
    chip: SearchChip,
    recents: List<ConversationReference>,
    results: SearchResults?,
    onSelectChip: (SearchChip) -> Unit,
    onOpenChat: (ChatId, messageId: Long?) -> Unit,
    onOpenCurrency: (Token) -> Unit,
    onOpenLink: (SharedLink) -> Unit,
    onClose: () -> Unit,
    close: SearchClose? = null,
    field: SearchFieldHost = DefaultSearchFieldHost,
    backButton: SearchBackButton? = null,
    barButtons: Modifier = Modifier,
) {
    val hazeState = rememberHazeState()
    val closeSearch = rememberCloseAfterKeyboard(onClose, close)
    val density = LocalDensity.current
    var chipsHeight by remember { mutableStateOf(0.dp) }
    var barHeight by remember { mutableStateOf(0.dp) }

    // Results run the full height, under the chips and the bar, and blur in toward both as they
    // scroll beneath them. At rest the padding keeps the first and last rows clear of each.
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars)
        .asPaddingValues().calculateBottomPadding()
    val top = statusBar + if (results != null) chipsHeight else 0.dp
    val bottom = barHeight + bottomInset
    val contentPadding = PaddingValues(top = top, bottom = bottom)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background)
    ) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            if (results == null) {
                EmptySearch(
                    recents = recents,
                    contentPadding = contentPadding,
                    onOpenChat = { onOpenChat(it, null) },
                )
            } else {
                // Only the results change. They fade through rather than cross-fade: the old list
                // is gone before the new one comes in, so rows in the same place never overlap.
                AnimatedContent(
                    targetState = chip,
                    transitionSpec = {
                        fadeIn(tween(ResultsFadeInMs, delayMillis = ResultsFadeOutMs)) togetherWith
                            fadeOut(tween(ResultsFadeOutMs))
                    },
                    label = "searchResults",
                ) { shown ->
                    if (results.isEmpty(shown)) NoResults(contentPadding) else SearchResultsList(
                        chip = shown,
                        results = results,
                        contentPadding = contentPadding,
                        onSelectChip = onSelectChip,
                        onOpenChat = onOpenChat,
                        onOpenCurrency = onOpenCurrency,
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }

        TopScrollEdge(
            modifier = Modifier.align(Alignment.TopCenter),
            hazeState = hazeState,
            visible = true,
            height = top + EdgeFade,
        )
        if (results != null) {
            SearchChips(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .onSizeChanged { chipsHeight = with(density) { it.height.toDp() } },
                selected = chip,
                onSelect = onSelectChip,
            )
        }

        BottomScrollEdge(
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
            visible = true,
            height = bottom + EdgeFade,
        )
        SearchBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
            query = query,
            hazeState = hazeState,
            onClose = closeSearch,
            field = field,
            backButton = backButton,
            buttons = barButtons,
        )
    }
}

/**
 * Lowers the keyboard, then closes Search. Closing with it up would shrink the field into the nav
 * bar's button while the keyboard slides away under it, so the keyboard goes first: [close], when the
 * host has one, follows it down; otherwise [onClose] waits until it is nearly gone.
 */
@Composable
private fun rememberCloseAfterKeyboard(onClose: () -> Unit, close: SearchClose?): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val scope = rememberCoroutineScope()
    val latestOnClose by rememberUpdatedState(onClose)
    val latestClose by rememberUpdatedState(close)
    var closing by remember { mutableStateOf(false) }
    return {
        if (!closing) {
            closing = true
            scope.launch {
                focusManager.clearFocus()
                val raised = ime.getBottom(density)
                if (raised > 0) keyboard?.hide()
                val hostClose = latestClose
                if (hostClose != null) {
                    hostClose(raised) { ime.getBottom(density) }
                    return@launch
                }
                if (raised > 0) {
                    // A keyboard that never reports itself gone mustn't strand Search open.
                    withTimeoutOrNull(KeyboardHideTimeout) {
                        snapshotFlow { ime.getBottom(density) }
                            .first { it <= raised * KeyboardCloseHandoff }
                    }
                }
                latestOnClose()
            }
        }
    }
}

/** Search before anything is typed: the title and the chats last opened from here (node 12248:72). */
@Composable
private fun EmptySearch(
    recents: List<ConversationReference>,
    contentPadding: PaddingValues,
    onOpenChat: (ChatId) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Text(
            modifier = Modifier.padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x3,
            ),
            text = stringResource(R.string.title_search),
            style = CodeTheme.typography.screenTitleLarge,
            color = CodeTheme.colors.textMain,
        )
        if (recents.isNotEmpty()) {
            SectionHeader(
                modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                title = stringResource(R.string.label_searchRecent),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                items(recents, key = { it.chatId.toString() }) { chat ->
                    RecentChat(chat = chat, onClick = { onOpenChat(chat.chatId) })
                }
            }
        }
    }
}

@Composable
private fun RecentChat(chat: ConversationReference, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(RecentAvatarSize)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        ChatAvatar(chat = chat, modifier = Modifier.requiredSize(RecentAvatarSize))
        Text(
            text = chat.name.orEmpty(),
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoResults(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(CodeTheme.dimens.inset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.title_searchNoResults),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Text(
            text = stringResource(R.string.subtitle_noSearchResults),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The chips pinned above results (node 12248:136). The row only scrolls when the selected chip is
 * cut off, and then just far enough to show it whole.
 */
@Composable
private fun SearchChips(
    selected: SearchChip,
    onSelect: (SearchChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val inset = with(LocalDensity.current) { CodeTheme.dimens.inset.roundToPx() }
    LaunchedEffect(selected) {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selected.ordinal }
        if (item == null) {
            state.animateScrollToItem(selected.ordinal)
            return@LaunchedEffect
        }
        val start = info.viewportStartOffset + inset
        val end = info.viewportEndOffset - inset
        val delta = when {
            item.offset < start -> item.offset - start
            item.offset + item.size > end -> item.offset + item.size - end
            else -> 0
        }
        if (delta != 0) state.animateScrollBy(delta.toFloat())
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = PaddingValues(
            horizontal = CodeTheme.dimens.inset,
            vertical = CodeTheme.dimens.grid.x2,
        ),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        items(SearchChip.entries) { chip ->
            FilterChip(
                label = stringResource(chip.label),
                selected = chip == selected,
                onClick = { onSelect(chip) },
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    chip: SearchChip,
    results: SearchResults,
    contentPadding: PaddingValues,
    onSelectChip: (SearchChip) -> Unit,
    onOpenChat: (ChatId, messageId: Long?) -> Unit,
    onOpenCurrency: (Token) -> Unit,
    onOpenLink: (SharedLink) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + CodeTheme.dimens.grid.x3,
        ),
    ) {
        when (chip) {
            SearchChip.All -> {
                // Links have no section here (spec): they are reached from their chip only.
                if (results.chats.isNotEmpty()) {
                    sectionHeader(SearchChip.Chats, onSelectChip)
                    chatRows(results.chats.take(ALL_SECTION_LIMIT), onOpenChat)
                }
                if (results.currencies.isNotEmpty()) {
                    sectionHeader(SearchChip.Currencies, onSelectChip)
                    item(key = "currency-rail") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
                            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                        ) {
                            items(results.currencies, key = { it.token.address.toString() }) {
                                CurrencyCard(result = it, onClick = { onOpenCurrency(it.token) })
                            }
                        }
                    }
                }
                if (results.messages.isNotEmpty()) {
                    sectionHeader(SearchChip.Messages, onSelectChip)
                    messageRows(results.messages.take(ALL_SECTION_LIMIT), onOpenChat)
                }
            }
            SearchChip.Chats -> chatRows(results.chats, onOpenChat)
            SearchChip.Currencies -> {
                val rows = results.currencies.chunked(2)
                items(rows.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = CodeTheme.dimens.inset,
                                vertical = CodeTheme.dimens.grid.x1,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    ) {
                        rows[index].forEach { result ->
                            CurrencyCard(
                                modifier = Modifier.weight(1f),
                                result = result,
                                onClick = { onOpenCurrency(result.token) },
                            )
                        }
                        if (rows[index].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            SearchChip.Messages -> messageRows(results.messages, onOpenChat)
            SearchChip.Links -> items(results.links, key = { it.link.url }) { link ->
                LinkRow(result = link, onClick = { onOpenLink(link.link) })
            }
        }
    }
}

private fun LazyListScope.sectionHeader(chip: SearchChip, onSelectChip: (SearchChip) -> Unit) {
    item(key = "header-${chip.name}") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionHeader(modifier = Modifier.weight(1f), title = stringResource(chip.label))
            Text(
                modifier = Modifier
                    .clickable { onSelectChip(chip) }
                    .padding(bottom = CodeTheme.dimens.grid.x1),
                text = stringResource(R.string.action_searchSeeAll),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}

private fun LazyListScope.chatRows(
    chats: List<ConversationReference>,
    onOpenChat: (ChatId, messageId: Long?) -> Unit,
) {
    items(chats, key = { "chat-${it.chatId}" }) { chat ->
        ChatListRow(
            avatar = { ChatAvatar(chat, Modifier.requiredSize(CodeTheme.dimens.staticGrid.x9)) },
            title = {
                RowTitle(chat.name.orEmpty())
                ChatRowTrailing(lastActivity = chat.lastActivity, unreadCount = 0, canOpen = false)
            },
            subtitle = {
                ChatRowSubtitle(
                    isTyping = false,
                    preview = chat.lastMessagePreview,
                    hasMessages = chat.hasMessages,
                    fallback = { SubtitleText("") },
                )
            },
            showDivider = false,
            onClick = { onOpenChat(chat.chatId, null) },
        )
    }
}

private fun LazyListScope.messageRows(
    messages: List<MessageResult>,
    onOpenChat: (ChatId, messageId: Long?) -> Unit,
) {
    items(messages, key = { "message-${it.hit.chatId}-${it.hit.messageId}" }) { result ->
        val chat = result.chat
        val text = when {
            !chat.isGroup -> result.hit.text
            result.hit.isFromSelf -> stringResource(R.string.label_searchMessageFromYou, result.hit.text)
            result.senderName != null -> stringResource(
                R.string.label_searchMessageFrom, result.senderName, result.hit.text,
            )
            else -> result.hit.text
        }
        ChatListRow(
            avatar = { ChatAvatar(chat, Modifier.requiredSize(CodeTheme.dimens.staticGrid.x9)) },
            title = {
                RowTitle(chat.name.orEmpty())
                ChatRowTrailing(lastActivity = result.hit.timestamp, unreadCount = 0, canOpen = false)
            },
            subtitle = { SubtitleText(text) },
            showDivider = false,
            onClick = { onOpenChat(chat.chatId, result.hit.messageId) },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RowTitle(text: String) {
    Text(
        modifier = Modifier.weight(1f),
        text = text,
        style = CodeTheme.typography.textMedium,
        color = CodeTheme.colors.textMain,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChatAvatar(chat: ConversationReference, modifier: Modifier = Modifier) {
    ContactAvatar(
        image = chat.image,
        displayName = chat.name.orEmpty(),
        access = chat.avatarAccess,
        modifier = modifier.clip(CircleShape),
    )
}

/**
 * A currency on its bill colours: market cap, then the weekly change and new members when
 * Discover reported them, and who shared it (node 12248:97).
 */
@Composable
private fun CurrencyCard(
    result: CurrencyResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(CurrencyCardSize),
) {
    val token = result.token
    val card = remember(token) { currencyCardFigures(token) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CodeTheme.dimens.grid.x4))
            .background(billCardBrush(token.billCustomizations))
            .clickable(onClick = onClick)
            .padding(CodeTheme.dimens.grid.x3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            TokenIcon(token = token, modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6))
            Text(
                text = token.name,
                style = CodeTheme.typography.textMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        card.marketCap?.let {
            Text(
                text = it.inDisplayCurrency().abbreviated(),
                style = CodeTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 1,
            )
        }
        card.weeklyChange?.let {
            val sign = if (it < 0) "-" else "+"
            // Discover reports the change in USD; it reads in the viewer's currency.
            val change = Fiat(fiat = abs(it), currencyCode = CurrencyCode.USD).inDisplayCurrency()
            CardLine(stringResource(R.string.label_searchWeeklyChange, "$sign${change.abbreviated()}"))
        }
        card.newMembers?.let {
            CardLine(pluralStringResource(R.plurals.label_searchNewMembers, it.toInt(), it.abbreviated()))
        }
        result.sharedBy?.let { CardLine(attributionText(it)) }
    }
}

@Composable
private fun CardLine(text: String) {
    Text(
        text = text,
        style = CodeTheme.typography.caption,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A shared web link: its host, title, and who shared it when (node 12248:136). */
@Composable
private fun LinkRow(result: LinkResult, onClick: () -> Unit) {
    val link = result.link
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x1)
            .clip(RoundedCornerShape(CodeTheme.dimens.grid.x3))
            .background(CodeTheme.colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        LinkThumbnail(link)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.host,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
            )
            Text(
                text = link.preview?.title?.takeIf { it.isNotBlank() } ?: link.url,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.label_searchAttribution,
                    attributionText(result.attribution),
                    formatLastActivity(link.timestamp),
                ),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The link preview's image, or a link glyph when the preview has none. */
@Composable
private fun LinkThumbnail(link: SharedLink) {
    val image = link.preview?.image?.urlForSize(LinkThumbnailPx)
    Box(
        modifier = Modifier
            .size(LinkThumbnailSize)
            .clip(RoundedCornerShape(CodeTheme.dimens.grid.x2))
            .background(CodeTheme.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun attributionText(attribution: Attribution): String = when (attribution) {
    Attribution.You -> stringResource(R.string.label_searchSharedByYou)
    is Attribution.By -> stringResource(R.string.label_searchSharedBy, attribution.name)
    is Attribution.In -> stringResource(R.string.label_searchSharedIn, attribution.group)
}

/**
 * The bar Search is driven from, at the bottom where the tab bar was (node 12248:72): back to the
 * chats, the field, and ✕, which clears a query before it closes Search.
 */
@Composable
private fun SearchBar(
    query: TextFieldState,
    hazeState: HazeState,
    onClose: () -> Unit,
    field: SearchFieldHost,
    backButton: SearchBackButton?,
    buttons: Modifier,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        if (backButton != null) {
            Box(buttons) { backButton(onClose, hazeState) }
        } else {
            CircularIconButton(modifier = buttons, hazeState = hazeState, onClick = onClose, testTag = "search_back") { size ->
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(size),
                )
            }
        }
        field(Modifier.weight(1f)) { morph ->
            SearchField(query = query, hazeState = hazeState, modifier = Modifier.fillMaxWidth().then(morph))
        }
        CircularIconButton(
            modifier = buttons,
            hazeState = hazeState,
            onClick = onClose,
            testTag = "search_close",
        ) { size ->
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_searchClose),
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
    }
}

/**
 * The field, in the same glass as the chat composer so it reads as solid over results. Opening Search
 * is for typing, so it takes focus as soon as it is composed. Focus alone doesn't show the keyboard on
 * every device (a hardware keyboard suppresses it), so the keyboard is asked for explicitly; the host's
 * morph follows it up.
 */
@Composable
private fun SearchField(query: TextFieldState, hazeState: HazeState, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequester) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }
    SearchInput(
        modifier = modifier
            .clip(CircleShape)
            .hazeBlur(HazeInput.Sources(hazeState), ChatInputDefaults.glassMaterial())
            .background(ChatInputDefaults.ContainerColor, CircleShape)
            .border(CodeTheme.dimens.border, ChatInputDefaults.RimBrush, CircleShape)
            .focusRequester(focusRequester),
        state = query,
        placeholder = stringResource(R.string.hint_search),
        colors = inputColors(
            backgroundColor = Color.Transparent,
            borderColor = Color.Transparent,
        ),
    )
}

/** How much of the keyboard is still up when closing hands over to the morph. */
private const val KeyboardCloseHandoff = 0.2f

/** Longest Search waits on the keyboard going down before it closes anyway. */
private val KeyboardHideTimeout = 600.milliseconds

private const val ResultsFadeOutMs = 90
private const val ResultsFadeInMs = 150

/** How far the blur fades in past the chips and the bar. */
private val EdgeFade = 24.dp

private val SearchChip.label: Int
    get() = when (this) {
        SearchChip.All -> R.string.label_searchAll
        SearchChip.Chats -> R.string.label_searchChats
        SearchChip.Currencies -> R.string.label_searchCurrencies
        SearchChip.Messages -> R.string.label_searchMessages
        SearchChip.Links -> R.string.label_searchLinks
    }

private val RecentAvatarSize = 64.dp
private val CurrencyCardSize = 170.dp
private val LinkThumbnailSize = 48.dp
private const val LinkThumbnailPx = 160
