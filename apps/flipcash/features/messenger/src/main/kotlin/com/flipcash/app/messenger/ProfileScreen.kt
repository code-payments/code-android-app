package com.flipcash.app.messenger

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.messenger.internal.screens.profile.ProfileHeader
import com.flipcash.app.messenger.internal.screens.profile.ProfileShortcuts
import com.flipcash.app.messenger.internal.screens.profile.ProfileViewModel
import com.flipcash.app.messenger.internal.screens.profile.dmRoute
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filter

/**
 * Another person's profile behind `AppRoute.Messaging.Profile`: the header a chat profile shows,
 * with the Message shortcut under it, for someone the viewer reached by link rather than from a
 * conversation.
 *
 * No rows below the header. Report, block and mute act on a person the viewer has a chat with,
 * and they are one tap away in the DM that Message opens.
 */
@Composable
fun ProfileScreen(address: ProfileAddress) {
    val viewModel = hiltViewModel<ProfileViewModel>()
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, address) {
        viewModel.dispatchEvent(ProfileViewModel.Event.Load(address))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
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

    CodeScaffold(
        topBar = {
            AppBarWithTitle(onBackIconClicked = { navigator.pop() })
        },
    ) { innerPadding ->
        val participant = state.participant
        if (participant == null) {
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
        } else {
            ProfileHeader(
                participant = participant,
                joinDate = participant.profile.joinedAt,
                shortcuts = {
                    ProfileShortcuts(
                        cashSymbol = state.cashSymbol,
                        onMessage = { navigator.push(participant.dmRoute()) },
                        onSendCash = { navigator.push(participant.dmRoute(openSendCash = true)) },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    // The chat profile's top inset, so the two read as the same screen.
                    .padding(top = CodeTheme.dimens.grid.x7),
            )
        }
    }
}
