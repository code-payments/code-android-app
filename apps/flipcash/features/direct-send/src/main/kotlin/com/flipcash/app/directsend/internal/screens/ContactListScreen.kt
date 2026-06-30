package com.flipcash.app.directsend.internal.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.directsend.internal.SendFlowViewModel
import com.flipcash.app.directsend.internal.screens.components.ContactList
import com.flipcash.app.permissions.ContactAccessResult
import com.flipcash.app.permissions.rememberContactAccessHandle
import com.flipcash.features.directsend.R
import com.getcode.navigation.flow.LocalOuterCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.PullToReveal
import com.getcode.ui.components.SearchInput
import com.getcode.ui.components.rememberPullToRevealState
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.LocalSheetGesturesState
import kotlinx.coroutines.flow.filterIsInstance


@Composable
internal fun ContactListScreen() {
    val flowNavigator = rememberFlowNavigator<SendStep, SendResult>()
    val viewModel = flowSharedViewModel<SendFlowViewModel>()
    val navigator = LocalOuterCodeNavigator.current

    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.SendInvite>()
            .collect { event ->
                navigator.show(AppRoute.Main.InviteContact(event.contact.e164))
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.NavigateToChat>()
            .collect { event ->
                flowNavigator.navigate(
                    AppRoute.Messaging.Chat(identifier = event.identifier)
                )
            }
    }

    val accessHandle = rememberContactAccessHandle(
        isPickerMode = state.isPickerMode,
    ) { result ->
        when (result) {
            is ContactAccessResult.Picked -> {
                viewModel.dispatchEvent(SendFlowViewModel.Event.ContactsPicked(result.contacts))
            }

            else -> Unit
        }
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_send),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = {
                    AppBarDefaults.Close { flowNavigator.exitCanceled() }
                },
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()

        // Search bar is hidden by default; an explicit pull-down on the list reveals it.
        val pullToRevealState = rememberPullToRevealState()

        // PullToReveal drives this: the sheet's drag-to-dismiss is suppressed while a
        // pull is being captured (and while search is hidden), so a downward pull
        // reveals search instead of dismissing — even on a single hard pull — and is
        // restored once the gesture ends with search shown. Always re-enable on exit.
        val setSheetGesturesEnabled = LocalSheetGesturesState.current
        DisposableEffect(setSheetGesturesEnabled) {
            onDispose { setSheetGesturesEnabled(true) }
        }

        // Collapse the search bar again once the user scrolls into the list without
        // an active query.
        LaunchedEffect(pullToRevealState) {
            snapshotFlow {
                pullToRevealState.isRevealed &&
                    listState.firstVisibleItemIndex > 0 &&
                    state.searchState.text.isEmpty()
            }.collect { shouldHide ->
                if (shouldHide) pullToRevealState.hide()
            }
        }

        PullToReveal(
            state = pullToRevealState,
            modifier = Modifier.padding(innerPadding),
            onSuppressDismissChange = { suppress -> setSheetGesturesEnabled(!suppress) },
            revealContent = {
                Row(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.grid.x3)
                        .padding(vertical = CodeTheme.dimens.grid.x3),
                ) {
                    SearchInput(
                        modifier = Modifier.weight(1f),
                        state = state.searchState,
                        contentPadding = PaddingValues(start = CodeTheme.dimens.grid.x1),
                    )
                }
            },
        ) {
            ContactList(
                items = state.listItems,
                searchState = state.searchState,
                listState = listState,
                isPickerMode = state.isPickerMode,
                onAddMoreContacts = { accessHandle.launch() },
                onItemClick = { contact ->
                    viewModel.dispatchEvent(SendFlowViewModel.Event.OnContactClicked(contact))
                },
                onItemDismissed = { contact ->
                    viewModel.dispatchEvent(SendFlowViewModel.Event.ContactRemoved(contact.contact.e164))
                },
            )
        }
    }
}
