package com.flipcash.app.messenger

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import com.getcode.ui.components.glass.floatingGlass
import com.getcode.ui.components.BottomScrollEdge
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.currencysend.CurrencySendViewModel
import com.flipcash.features.messenger.R
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.common.ui.ContactAvatar
import android.os.Parcelable
import androidx.navigation3.runtime.entryProvider
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.rememberFlowNavigator
import com.flipcash.app.messenger.internal.currencysend.CurrencySendStep
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySendScreen(mint: Mint, resultStateRegistry: NavResultStateRegistry) {
    val viewModel = hiltViewModel<CurrencySendViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    LaunchedEffect(viewModel, mint) { viewModel.start(mint) }
    val session = LocalSessionController.current
    LaunchedEffect(viewModel, session) {
        viewModel.bill.collect { session?.showBill(it) }
    }
    // Raised once the checkmark has held: from then on the sheet may close, and only by our hand.
    var hiding by remember { mutableStateOf(false) }
    val locked by rememberUpdatedState(!state.sendProgress.isIdle && !hiding)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !locked },
    )
    LaunchedEffect(viewModel, sheetState) {
        viewModel.done.collect {
            finishSend(
                hide = {
                    hiding = true
                    sheetState.hide()
                },
                navigateBack = { navigator.pop() },
            )
        }
    }

    // Select Recipient slides in over the amount, as Select People does in a group send: the sheet
    // has its own nav host, and the view model's stage decides which step is on top.
    val inRecipients = state.stage == CurrencySendViewModel.Stage.Recipients

    FlowHost<CurrencySendStep, Parcelable>(
        initialStack = listOf(CurrencySendStep.Amount),
        resultStateRegistry = resultStateRegistry,
        onExit = { _, _ -> navigator.pop() },
        entryProvider = entryProvider {
            annotatedEntry<CurrencySendStep.Amount> {
                val flowNavigator = rememberFlowNavigator<CurrencySendStep, Parcelable>()
                LaunchedEffect(inRecipients) {
                    if (inRecipients) flowNavigator.navigateTo(CurrencySendStep.Recipients)
                }
                AmountEntryScreen(
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
            }

            annotatedEntry<CurrencySendStep.Recipients> {
                val flowNavigator = rememberFlowNavigator<CurrencySendStep, Parcelable>()
                LaunchedEffect(inRecipients) {
                    if (!inRecipients) flowNavigator.back()
                }
                // Back goes through the view model, whose stage change pops this step above;
                // the nav host popping it alone would leave the stage behind.
                BackHandler(enabled = state.selected == null) { viewModel.onBackToAmount() }
                SelectRecipientContent(
                    recipients = state.visibleRecipients,
                    onQueryChanged = viewModel::onQueryChanged,
                    onBack = viewModel::onBackToAmount,
                    onPick = viewModel::onRecipientTapped,
                    onGiveAsCash = viewModel::giveAsCash,
                )
            }
        },
    )

    val selected = state.selected
    val amount = state.amount
    if (selected != null && amount != null) {
        SwipeToSendSheet(
            recipient = selected,
            amountText = amount.formatted(),
            token = state.token,
            sendProgress = state.sendProgress,
            sheetState = sheetState,
            locked = locked,
            onConfirm = viewModel::send,
            onDismiss = { if (!hiding) viewModel.onConfirmDismissed() },
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

    // Same arrangement as Edit Featured Groups: the list runs edge to edge behind the bottom
    // button, a progressive blur (BottomScrollEdge) fades what passes under it, and the button
    // sits on floatingGlass over the same haze source.
    val density = LocalDensity.current
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    var barHeight by remember { mutableStateOf(0.dp) }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_selectRecipient),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = onBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(vertical = CodeTheme.dimens.grid.x2),
                    state = query,
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .hazeSource(hazeState),
                    // The button's height and the nav bar, so the last row scrolls fully clear.
                    contentPadding = PaddingValues(bottom = barHeight),
                ) {
                    items(recipients.orEmpty(), key = { it.chatId.toString() }) { chat ->
                        RecipientRow(chat = chat, onClick = { onPick(chat) })
                    }
                }
            }

            BottomScrollEdge(
                hazeState = hazeState,
                visible = true,
                height = barHeight,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // Pinned, and present with an empty list too: it is the way out when there is no one to pay.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
            ) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(
                            top = CodeTheme.dimens.grid.x2,
                            bottom = CodeTheme.dimens.grid.x2,
                        )
                        .floatingGlass(hazeState, CodeTheme.shapes.small),
                    text = stringResource(R.string.action_orGiveAsCash),
                    buttonState = ButtonState.Filled10,
                    onClick = onGiveAsCash,
                )
            }
        }
    }
}

@Composable
private fun RecipientRow(chat: ConversationReference, onClick: () -> Unit) {
    // Design: 24 avatar, 16 gap, 48 row; the divider sits under the name column only.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CodeTheme.dimens.grid.x10)
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
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = chat.name.orEmpty(),
                    style = CodeTheme.typography.textMedium.copy(fontSize = 17.sp),
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
 * What follows a successful send, in order: the sheet finishes closing, and only then does the
 * back stack change. Navigating first would pull the sheet out mid-frame with no hide animation.
 */
internal suspend fun finishSend(hide: suspend () -> Unit, navigateBack: () -> Unit) {
    hide()
    navigateBack()
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
    sheetState: SheetState,
    locked: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                    horizontal = CodeTheme.dimens.grid.x1,
                    vertical = CodeTheme.dimens.grid.x1,
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
