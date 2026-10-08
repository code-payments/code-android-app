package com.flipcash.app.messenger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.messenger.internal.GroupInviteViewModel
import com.flipcash.app.messenger.internal.screens.GroupInviteSheet
import com.flipcash.app.messenger.internal.screens.ShareToChatsSubject
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.ui.utils.rememberKeyboardController

/**
 * A person's profile link, shared by the system share sheet, copied, or sent into recent chats,
 * behind `AppRoute.Messaging.ShareProfileToChats`.
 *
 * Opened from the root navigator by the viewer's own profile and by someone else's, so it names the
 * chat to leave out on the route: the DM with that person, or nothing for the viewer's own.
 */
@Composable
fun ShareProfileToChatsScreen(route: AppRoute.Messaging.ShareProfileToChats) {
    val viewModel = hiltViewModel<GroupInviteViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    // Exit through the sheet so it animates down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val keyboard = rememberKeyboardController()

    val link = remember(route.userId, route.username) {
        Linkify.tipcard(TipCardOwner.preferringUsername(route.username, route.userId))
    }
    val preview = remember(viewModel, route.userId) { viewModel.previewFor(route.userId) }

    LaunchedEffect(viewModel, route.directChatId) { viewModel.exclude(route.directChatId) }

    // The chat is opened once the sheet is gone, so it is not pushed under one still on its way
    // out. The keyboard goes first: the message field has it up, and leaving with it still open
    // drags the screen behind out from under it.
    LaunchedEffect(viewModel) {
        viewModel.invited.collect { chatId ->
            keyboard.hideIfVisible {
                navigator.pendingSheetDismiss = {
                    navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chatId)))
                }
            }
        }
    }

    GroupInviteSheet(
        inviteUrl = link,
        subject = ShareToChatsSubject.User(
            userId = route.userId,
            username = route.username,
            displayName = route.displayName,
            preview = preview,
        ),
        state = state,
        // A person's link has no group analytics to fire.
        onShare = {},
        onCopy = { viewModel.copyProfileLink(link) },
        onToggle = viewModel::toggle,
        onMessageChanged = viewModel::onMessageChanged,
        onInvite = { viewModel.invite(link) },
        onDismiss = dismissSheet,
        // The card is drawn over this sheet, which stays open beneath it.
        onShowCard = {
            keyboard.hideIfVisible {
                navigator.push(AppRoute.Menu.ProfileCard(route.userId))
            }
        },
    )
}
