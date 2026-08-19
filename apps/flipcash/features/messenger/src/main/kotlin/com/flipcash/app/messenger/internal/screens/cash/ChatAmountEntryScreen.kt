package com.flipcash.app.messenger.internal.screens.cash

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.Token
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.util.resources.LocalResources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
                // Same centred pill as the give screen — declare the centring rather than leaning on
                // the leading slot's width to position a Start-aligned title.
                titleAlignment = Alignment.CenterHorizontally,
                title = {
                    TokenSelectionPill(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        token = token
                    ) {
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
