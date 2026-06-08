package com.flipcash.app.messenger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.flowScopedViewModel
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.util.resources.LocalResources
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun ChatAmountEntryScreen(e164: String) {
    val viewModel = flowScopedViewModel<ChatViewModel>(e164)
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val resources = LocalResources.current

    LaunchedEffect(state.resolveState) {
        if (state.resolveState is ChatViewModel.ResolveState.Failed) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_contactNotOnFlipcash),
                message = resources.getString(R.string.error_description_contactNotOnFlipcash),
                onDismiss = { navigator.pop() }
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.SendComplete>()
            .onEach { event ->
                BottomBarManager.showInfo(
                    title = resources.getString(R.string.prompt_title_fundsSentToContact),
                    message = resources.getString(
                        R.string.prompt_description_fundsSentToContact,
                        event.amount.formatted(rule = Fiat.FormattingRule.Truncated),
                        state.chattingWith?.displayName ?: resources.getString(R.string.subtitle_yourSelectedRecipient),
                    ),
                    onDismiss = { navigator.pop() }
                )
            }.launchIn(this)
    }

    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(ChatViewModel.Event.OnConfirmRequested) },
        onChangeCurrency = { navigator.push(AppRoute.Main.RegionSelection) },
        appBar = {
            AppBarWithTitle(
                isInModal = true,
                title = {
                    TokenSelectionPill(state.token) {
                        navigator.push(
                            AppRoute.Sheets.TokenSelection(TokenPurpose.Select)
                        )
                    }
                },
                leftIcon = {
                    AppBarDefaults.UpNavigation { navigator.pop() }
                },
            )
        },
    )
}
