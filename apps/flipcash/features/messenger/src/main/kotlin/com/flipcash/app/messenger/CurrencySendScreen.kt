package com.flipcash.app.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.currencysend.CurrencySendViewModel
import com.flipcash.features.messenger.R
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SearchInput
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.solana.keys.Mint

/**
 * Send a currency to a person from its Currency Info page, behind [AppRoute.Sheets.CurrencySend]:
 * the amount, a recipient from the viewer's DMs, then the swipe. The currency is fixed to [mint].
 * After the checkmark the screen pops back to Currency Info.
 */
@Composable
fun CurrencySendScreen(mint: Mint) {
    val viewModel = hiltViewModel<CurrencySendViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    LaunchedEffect(viewModel, mint) { viewModel.start(mint) }
    LaunchedEffect(viewModel) { viewModel.done.collect { navigator.pop() } }

    BackHandler(enabled = state.stage == CurrencySendViewModel.Stage.Recipients && state.selected == null) {
        viewModel.onBackToAmount()
    }

    AnimatedContent(
        targetState = state.stage,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "currencySendStage",
    ) { stage ->
        when (stage) {
            CurrencySendViewModel.Stage.Amount -> AmountEntryScreen(
                controller = viewModel.amountDelegate,
                onConfirm = viewModel::onAmountConfirmed,
                largeHeader = true,
                appBar = {
                    AppBarWithTitle(
                        titleAlignment = Alignment.CenterHorizontally,
                        titleContent = {
                            // Locked to this currency: no dropdown mark, and nothing to tap.
                            TokenSelectionPill(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                token = state.token,
                                showsPicker = false,
                            ) {}
                        },
                        onBackIconClicked = { navigator.pop() },
                    )
                },
            )

            CurrencySendViewModel.Stage.Recipients -> SelectRecipientContent(
                recipients = state.visibleRecipients,
                onQueryChanged = viewModel::onQueryChanged,
                onBack = viewModel::onBackToAmount,
                onPick = viewModel::onRecipientTapped,
                onGiveAsCash = {
                    val amount = state.amount ?: return@SelectRecipientContent
                    navigator.push(
                        AppRoute.Sheets.Give(
                            mint = mint,
                            fromTokenInfo = true,
                            amount = amount.decimalValue,
                        )
                    )
                },
            )
        }
    }

    val selected = state.selected
    val amount = state.amount
    if (selected != null && amount != null) {
        SwipeToSendSheet(
            recipient = selected,
            amountText = amount.formatted(),
            token = state.token,
            sendProgress = state.sendProgress,
            onConfirm = viewModel::send,
            onDismiss = viewModel::onConfirmDismissed,
        )
    }
}

@Composable
private fun SelectRecipientContent(
    recipients: List<ConversationReference>?,
    onQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
    onPick: (ConversationReference) -> Unit,
    onGiveAsCash: () -> Unit,
) {
    val query: TextFieldState = rememberTextFieldState()
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect(onQueryChanged)
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_selectRecipient),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = onBack,
            )
        },
        bottomBar = {
            // Pinned, and present with an empty list too: it is the way out when there is no one to pay.
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2),
                text = stringResource(R.string.action_orGiveAsCash),
                buttonState = ButtonState.Filled10,
                onClick = onGiveAsCash,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(vertical = CodeTheme.dimens.grid.x2),
                state = query,
            )
            LazyColumn(contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x4)) {
                items(recipients.orEmpty(), key = { it.chatId.toString() }) { chat ->
                    RecipientRow(chat = chat, onClick = { onPick(chat) })
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(chat: ConversationReference, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        ContactAvatar(
            image = chat.image,
            displayName = chat.name.orEmpty(),
            access = chat.avatarAccess,
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3)) {
                Text(
                    text = chat.name.orEmpty(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = CodeTheme.colors.divider)
        }
    }
}

/**
 * The confirm sheet over Select Recipient: who is being paid, the amount, the currency with no
 * picker, and the swipe. Closed by a swipe down, a tap on the scrim or back, which returns to the list and sends nothing; locked once the send starts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToSendSheet(
    recipient: ConversationReference,
    amountText: String,
    token: com.getcode.opencode.model.financial.Token?,
    sendProgress: com.getcode.view.LoadingSuccessState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locked by rememberUpdatedState(!sendProgress.isIdle)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !locked },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!locked) onDismiss() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = CodeTheme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x6, bottom = CodeTheme.dimens.grid.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            ContactAvatar(
                image = recipient.image,
                displayName = recipient.name.orEmpty(),
                access = recipient.avatarAccess,
                modifier = Modifier
                    .requiredSize(CodeTheme.dimens.staticGrid.x12)
                    .clip(CircleShape),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = recipient.name.orEmpty(),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                recipient.handle?.let {
                    Text(
                        text = "@${it.removePrefix("@")}",
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
            }
            Text(
                text = amountText,
                style = CodeTheme.typography.displayMedium,
                color = CodeTheme.colors.textMain,
            )
            TokenSelectionPill(
                token = token,
                background = White10,
                textStyle = CodeTheme.typography.textMedium,
                imageSize = CodeTheme.dimens.staticGrid.x3,
                contentPadding = PaddingValues(
                    horizontal = CodeTheme.dimens.grid.x1 + 1.dp,
                    vertical = CodeTheme.dimens.grid.x1 - 1.dp,
                ),
                showsPicker = false,
                onClick = {},
            )
            SlideToConfirm(
                onConfirm = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CodeTheme.dimens.grid.x3),
                enabled = sendProgress.isIdle,
                isLoading = sendProgress.loading,
                isSuccess = sendProgress.success,
                label = stringResource(R.string.action_swipeToSend),
            )
        }
    }
}
