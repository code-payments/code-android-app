package com.flipcash.app.messenger

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.messenger.internal.screens.profile.ChatProfileViewModel
import com.flipcash.app.messenger.internal.screens.profile.PersonProfileScreen
import com.flipcash.app.messenger.internal.screens.profile.ProfileViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Another person's profile behind `AppRoute.Messaging.Profile`, for someone the viewer reached by
 * link rather than from a conversation.
 *
 * The same screen a chat's profile shows, with no chat behind it: [ProfileViewModel] turns the
 * link's address into a person, then [ChatProfileViewModel] takes over as it does in a chat, which
 * is what runs Block. With no chat there is no Mute row.
 */
@Composable
fun ProfileScreen(address: ProfileAddress) {
    val lookup = hiltViewModel<ProfileViewModel>()
    val viewModel = hiltViewModel<ChatProfileViewModel>()
    val navigator = LocalCodeNavigator.current
    val state by lookup.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(lookup, address) {
        lookup.dispatchEvent(ProfileViewModel.Event.Load(address))
    }

    LaunchedEffect(lookup) {
        lookup.eventFlow
            .filter {
                it is ProfileViewModel.Event.Unavailable || it is ProfileViewModel.Event.OwnProfile
            }
            .collect { event ->
                when (event) {
                    // The failure is on the bottom bar already; there's nothing here to show.
                    ProfileViewModel.Event.Unavailable -> navigator.pop()
                    // Where the router sends a self-link it can recognise. See AppRouter.profile.
                    else -> navigator.navigateAll(listOf(AppRoute.Tabs.Menu))
                }
            }
    }

    LaunchedEffect(viewModel, state.participant) {
        state.participant?.let {
            viewModel.dispatchEvent(
                ChatProfileViewModel.Event.OnParticipantSet(it, isFullProfile = true)
            )
        }
    }

    LaunchedEffect(viewModel) {
        // No chat to leave, as a DM's profile does after a block: back to wherever the link was
        // opened from.
        viewModel.eventFlow
            .filterIsInstance<ChatProfileViewModel.Event.BlockSuccessful>()
            .collect { navigator.pop() }
    }

    if (state.participant == null) {
        CodeScaffold(
            topBar = {
                AppBarWithTitle(onBackIconClicked = { navigator.pop() })
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CodeCircularProgressIndicator(
                    strokeWidth = CodeTheme.dimens.thickBorder,
                    color = CodeTheme.colors.textSecondary,
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x8),
                )
            }
        }
    } else {
        PersonProfileScreen(
            viewModel = viewModel,
            onBack = { navigator.pop() },
            // Reached by link, so there is no chat underneath to return to.
            onOpenChat = { chatId ->
                navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chatId)))
            },
        )
    }
}
