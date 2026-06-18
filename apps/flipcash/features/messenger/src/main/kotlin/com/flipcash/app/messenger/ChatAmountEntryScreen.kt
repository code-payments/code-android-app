package com.flipcash.app.messenger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.LocalFlowNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.util.resources.LocalResources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Standalone amount entry screen — used when navigating directly from the contact list
 * (messenger disabled). Creates its own [ChatViewModel] scoped to this nav entry.
 */
@Composable
fun ChatAmountEntryScreen(identifier: ChatIdentifier) {
    val viewModel = hiltViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val navigator = LocalCodeNavigator.current
    LaunchedEffect(viewModel, identifier) {
        viewModel.dispatchEvent(ChatViewModel.Event.OnChatOpened(identifier))
    }

    ChatAmountEntryContent(
        amountDelegate = viewModel.amountDelegate,
        resolveState = state.resolveState,
        token = state.token,
        eventFlow = viewModel.eventFlow,
        onExit = { navigator.pop() },
        onConfirm = { viewModel.dispatchEvent(ChatViewModel.Event.OnConfirmRequested) },
    )
}

@Composable
internal fun ChatAmountEntryContent(
    amountDelegate: AmountEntryDelegate,
    resolveState: ChatViewModel.ResolveState,
    token: Token?,
    eventFlow: Flow<ChatViewModel.Event>,
    onExit: () -> Unit,
    onConfirm: () -> Unit,
    onSendComplete: (() -> Unit)? = null,
) {
    val navigator = LocalCodeNavigator.current
    val resources = LocalResources.current

    LaunchedEffect(resolveState) {
        if (resolveState is ChatViewModel.ResolveState.Failed) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_contactNotOnFlipcash),
                message = resources.getString(R.string.error_description_contactNotOnFlipcash),
                onDismiss = { onExit() },
            )
        }
    }

    LaunchedEffect(eventFlow) {
        eventFlow
            .filterIsInstance<ChatViewModel.Event.SendComplete>()
            .onEach { event ->
                onSendComplete?.invoke() ?: navigator.pop()
            }.launchIn(this)
    }

    AmountEntryScreen(
        controller = amountDelegate,
        onConfirm = onConfirm,
        onChangeCurrency = { navigator.push(AppRoute.Main.RegionSelection) },
        appBar = {
            AppBarWithTitle(
                title = {
                    TokenSelectionPill(token) {
                        navigator.push(
                            AppRoute.Sheets.TokenSelection(TokenPurpose.Select)
                        )
                    }
                },
                leftIcon = {
                    AppBarDefaults.UpNavigation { onExit() }
                },
            )
        },
    )
}
