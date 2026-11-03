package com.flipcash.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.tipping.internal.ChatsViewModel
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.title_filterAll
import com.flipcash.core.resources.title_filterGroups
import com.flipcash.core.resources.title_filterUnread
import com.flipcash.core.resources.title_noChatsYet
import com.flipcash.core.resources.title_noGroupChats
import com.flipcash.core.resources.title_noUnreadChats
import com.flipcash.desktop.input.HostOs
import com.flipcash.shared.chat.ChatListFilter
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.theme.White50
import org.jetbrains.compose.resources.stringResource

/**
 * The chat list down the window's leading side (Sidebar node 13000:92): the title row with new chat,
 * search, the All / Unread / Groups chips, the rows, and the footer the shell appends. It reads the
 * shared [ChatsViewModel.State] directly rather than the phone's screen, which brings a centred
 * title, a hidden chip row and swipe actions. The window's sidebar toggle is drawn by the shell over
 * the title row's leading end; this row keeps that space free.
 */
@Composable
fun DesktopSidebar(
    state: ChatsViewModel.State,
    nav: DesktopNavState,
    onSelectFilter: (ChatListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val search = remember { FocusRequester() }
    // Consumed here so a list that mounts after the request (the peek) doesn't take focus later.
    LaunchedEffect(nav.searchFocusRequested) {
        if (!nav.searchFocusRequested) return@LaunchedEffect
        withFrameNanos { }
        runCatching { search.requestFocus() }
        nav.searchFocusRequested = false
    }
    val rows = state.visibleChats.matching(nav.searchQuery)
    Column(modifier) {
        TitleRow()
        SearchField(
            query = nav.searchQuery,
            onQueryChange = { nav.searchQuery = it },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp).focusRequester(search),
        )
        FilterRow(state.filter, onSelectFilter, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp))
        ChatRows(state, rows, nav, Modifier.weight(1f).fillMaxWidth())
    }
}

/** New chat sits at the trailing end. There is no desktop new-chat screen yet, so it is drawn disabled. */
@Composable
private fun TitleRow() {
    Row(
        Modifier.fillMaxWidth().height(DesktopDimens.TitleRowHeight).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).alpha(0.4f).testTag("new_chat"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = "New chat", tint = CodeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keycap = if (HostOs.current == HostOs.Mac) "⌘K" else "Ctrl K"
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(White05)
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = White50, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.Medium, color = CodeTheme.colors.textMain)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(CodeTheme.colors.textMain),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        // Esc clears what was typed; with nothing typed it falls through to the window.
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && query.isNotEmpty()) {
                            onQueryChange("")
                            true
                        } else false
                    }
                    .testTag("sidebar_search"),
            )
            if (query.isEmpty()) {
                Text("Search", style = style.copy(color = White50))
            }
        }
        if (query.isEmpty()) {
            Text(
                text = keycap,
                style = CodeTheme.typography.caption,
                color = White50,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(ChipFill).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FilterRow(selected: ChatListFilter, onSelect: (ChatListFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterPill(stringResource(Res.string.title_filterAll), selected == ChatListFilter.All) { onSelect(ChatListFilter.All) }
        FilterPill(stringResource(Res.string.title_filterUnread), selected == ChatListFilter.Unread) { onSelect(ChatListFilter.Unread) }
        FilterPill(stringResource(Res.string.title_filterGroups), selected == ChatListFilter.Groups) { onSelect(ChatListFilter.Groups) }
    }
}

/**
 * A chip 26 tall (node 13007:255). The shared `FilterChip` is 7dp-padded to iOS's 32, and the design
 * shows no counts, so this is its own.
 */
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = CodeTheme.typography.textSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        color = if (selected) CodeTheme.colors.textMain else CodeTheme.colors.textSecondary,
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) ChipFill else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, White10, RoundedCornerShape(13.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun ChatRows(
    state: ChatsViewModel.State,
    rows: List<ConversationReference>,
    nav: DesktopNavState,
    modifier: Modifier = Modifier,
) {
    when {
        // Nothing to say until the feed emits, so the empty state doesn't flash.
        !state.chats.isLoaded() -> Box(modifier)
        state.hasNoChatsAtAll -> Empty(stringResource(Res.string.title_noChatsYet), modifier)
        rows.isEmpty() -> Empty(
            when {
                nav.searchQuery.isNotBlank() -> "No chats match “${nav.searchQuery.trim()}”"
                state.filter == ChatListFilter.Unread -> stringResource(Res.string.title_noUnreadChats)
                else -> stringResource(Res.string.title_noGroupChats)
            },
            modifier,
        )
        else -> LazyColumn(
            modifier.testTag("chat_list"),
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(rows, key = { it.chatId }) { chat ->
                DesktopChatRow(chat = chat, selected = nav.isOpen(chat), onClick = { nav.openChat(chat) })
            }
        }
    }
}

@Composable
private fun Empty(message: String, modifier: Modifier) {
    Box(modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}
