package com.flipcash.app.messenger.internal.request

import com.getcode.theme.White05
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.navigation3.runtime.entryProvider
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.results.NavResultStateRegistry
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.getcode.util.resources.LocalResources
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.messenger.internal.screens.cash.GroupSelectPeopleContent
import com.flipcash.app.messenger.internal.screens.profile.edit.NameEditScaffold
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.app.core.AppRoute
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.getcode.utils.base58
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Clock
import kotlin.time.toJavaInstant

/**
 * Request Money, frames 03–06 and 25 of the group requests spec: history, then the create flow
 * behind New Request. Its stages live in [RequestMoneyViewModel] rather than as steps of their
 * own, so the draft has one owner and leaving the screen drops it. The screen hosts its own nav
 * host so each stage slides in over the last; the stage decides which step is on top.
 */
@Composable
internal fun RequestMoneyScreen(
    chatId: ChatId,
    resultStateRegistry: NavResultStateRegistry,
    onClose: () -> Unit,
    onOpenScreen: (AppRoute) -> Unit,
) {
    // Read here, not inside an entry: an entry's hiltViewModel would be scoped to that step.
    val viewModel = hiltViewModel<RequestMoneyViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(chatId) { viewModel.open(chatId) }
    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                RequestMoneyViewModel.Effect.Sent -> onClose()
            }
        }
    }
    val back = { if (!viewModel.back()) onClose() }
    val target = stackFor(state.stage)
    // Seeded once. FlowHost re-seeds, without a transition, when initialStack changes before
    // the stack has moved, so passing the live target swapped History for Amount unanimated.
    // FollowStage makes every move after the first frame.
    val initialStack = remember { target }

    FlowHost<RequestStep, Parcelable>(
        initialStack = initialStack,
        resultStateRegistry = resultStateRegistry,
        onExit = { _, _ -> onClose() },
        entryProvider = entryProvider {
            annotatedEntry<RequestStep.History> {
                FollowStage(RequestStep.History, target)
                val flowNavigator = rememberFlowNavigator<RequestStep, Parcelable>()
                val requests by viewModel.chatRequests.collectAsStateWithLifecycle()
                RequestHistoryContent(
                    requests = requests,
                    loadingMore = state.loadingPrevious,
                    canLoadMore = !state.previousEnd,
                    onBack = onClose,
                    onNewRequest = viewModel::newRequest,
                    onOpen = { flowNavigator.navigateTo(RequestStep.Details(it.id.base58)) },
                    onLoadMore = viewModel::loadMorePrevious,
                )
            }
            annotatedEntry<RequestStep.Amount> {
                FollowStage(RequestStep.Amount, target, onBack = back)
                AmountEntryScreen(
                    controller = viewModel.amountDelegate,
                    onConfirm = viewModel::amountEntered,
                    largeHeader = true,
                    appBar = {
                        AppBarWithTitle(
                            title = stringResource(R.string.title_totalAmount),
                            titleAlignment = Alignment.CenterHorizontally,
                            onBackIconClicked = back,
                        )
                    },
                )
            }
            annotatedEntry<RequestStep.Summary> {
                FollowStage(RequestStep.Summary, target, onBack = back)
                state.draft?.let { draft ->
                    RequestSummaryContent(
                        draft = draft,
                        sending = state.sendProgress.loading,
                        onBack = back,
                        onName = viewModel::editName,
                        onPeople = viewModel::editPeople,
                        onTiming = viewModel::editDeadline,
                        onRequest = viewModel::requestConfirm,
                    )
                }
                RequestSheets(state, viewModel)
            }
            annotatedEntry<RequestStep.People> {
                FollowStage(RequestStep.People, target, onBack = back)
                state.draft?.let { draft ->
                    val members by viewModel.members.collectAsStateWithLifecycle()
                    GroupSelectPeopleContent(
                        candidates = members,
                        selected = draft.shares,
                        onBack = back,
                        onToggle = { viewModel.toggle(it.userId) },
                        onSend = back,
                        title = stringResource(R.string.title_splitBetween),
                        buttonText = stringResource(R.string.action_done),
                        buttonEnabled = draft.canContinue,
                        viewerId = viewModel.viewerId,
                    )
                }
            }
            annotatedEntry<RequestStep.Details> { step ->
                val flowNavigator = rememberFlowNavigator<RequestStep, Parcelable>()
                RequestDetailsScreen(
                    chatId = chatId,
                    requestId = step.requestId,
                    onBack = { flowNavigator.back() },
                    onOpenScreen = onOpenScreen,
                    pushed = true,
                )
            }
            annotatedEntry<RequestStep.Name> {
                FollowStage(RequestStep.Name, target, onBack = back)
                state.draft?.let { draft ->
                    RequestNameContent(title = draft.title, onBack = back, onDone = viewModel::rename)
                }
            }
        },
    )
}

/**
 * Moves the nav host one step towards [target] from [step]'s entry (see [moveFrom]). Back, when
 * [onBack] is given, goes through the view model, whose stage change pops the step here; the nav
 * host popping on its own would leave the stage behind.
 */
@Composable
private fun FollowStage(step: RequestStep, target: List<RequestStep>, onBack: (() -> Unit)? = null) {
    val flowNavigator = rememberFlowNavigator<RequestStep, Parcelable>()
    LaunchedEffect(target) {
        when (val move = moveFrom(step, target)) {
            StackMove.None -> Unit
            StackMove.Pop -> flowNavigator.back()
            is StackMove.Push -> flowNavigator.navigateTo(move.step)
        }
    }
    if (onBack != null) BackHandler { onBack() }
}

@Composable
private fun RequestSheets(state: RequestMoneyViewModel.State, viewModel: RequestMoneyViewModel) {
    when (state.sheet) {
        RequestMoneyViewModel.Sheet.None -> Unit
        RequestMoneyViewModel.Sheet.Deadline -> state.draft?.let { draft ->
            TimeToPaySheet(
                current = draft.deadline,
                onChoose = viewModel::chooseDeadline,
                onDismiss = viewModel::dismissSheet,
            )
        }
        RequestMoneyViewModel.Sheet.Confirm -> ConfirmSendSheet(
            sending = state.sendProgress.loading,
            onSend = viewModel::send,
            onDismiss = viewModel::dismissSheet,
        )
    }
}

@Composable
private fun RequestHistoryContent(
    requests: List<PaymentRequest>,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    onBack: () -> Unit,
    onNewRequest: () -> Unit,
    onOpen: (PaymentRequest) -> Unit,
    onLoadMore: () -> Unit,
) {
    val now = remember(requests) { Clock.System.now() }
    val open = requests.filter { it.split != null && it.isOpen(now) }.sortedBy { it.split?.endsAt }
    val previous = requests
        .filter { it.split != null && !it.isOpen(now) }
        .sortedByDescending { it.resolvedClosedAt(now) }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_requestMoneyHistory),
                titleAlignment = Alignment.CenterHorizontally,
                // The sheet's root, closed the way the share sheet is.
                endContent = { AppBarDefaults.Close(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(CodeTheme.dimens.grid.x5),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            item {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = CodeTheme.dimens.grid.x3)
                        .testTag("request_money_new"),
                    text = stringResource(R.string.action_newRequest),
                    onClick = onNewRequest,
                )
            }
            if (open.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.header_openRequests)) }
                items(open, key = { it.id }) { RequestHistoryRow(it, onClick = { onOpen(it) }) }
            }
            if (previous.isNotEmpty()) {
                item {
                    SectionHeader(
                        text = stringResource(R.string.header_previousRequests),
                        modifier = Modifier.padding(top = if (open.isEmpty()) 0.dp else CodeTheme.dimens.grid.x3),
                    )
                }
                items(previous, key = { it.id }) { RequestHistoryRow(it, onClick = { onOpen(it) }) }
            }
            if (canLoadMore && !loadingMore) {
                // Reaching the end of what is loaded asks for the next page of Previous Requests.
                item { LaunchedEffect(previous.size) { onLoadMore() } }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
    )
}

/** A card per request, as on iOS: title and date on one line, amounts under, a chevron at the end. */
@Composable
private fun RequestHistoryRow(request: PaymentRequest, onClick: () -> Unit) {
    val split = request.split ?: return
    val title = (split.text as? PaymentRequestText.Plain)?.title.orEmpty()
    val date = remember(request.createdAt) {
        DateTimeFormatter.ofPattern(monthDayPattern())
            .format(request.createdAt.toJavaInstant().atZone(ZoneId.systemDefault()))
    }
    val progress = if (request.status == PaymentRequestStatus.Completed) {
        stringResource(R.string.label_requestCompleted, split.requested.text())
    } else {
        stringResource(R.string.label_requestProgress, split.paid.text(), split.requested.text())
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(CodeTheme.dimens.grid.x4)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                Text(
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                    text = title,
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = date,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
            Text(text = progress, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textSecondary)
        }
        Icon(
            modifier = Modifier.size(14.dp),
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun RequestSummaryContent(
    draft: NewRequestDraft,
    sending: Boolean,
    onBack: () -> Unit,
    onName: () -> Unit,
    onPeople: () -> Unit,
    onTiming: () -> Unit,
    onRequest: () -> Unit,
) {
    val amount = draft.total?.text().orEmpty()
    val share = (draft.largestShare ?: draft.total)?.text().orEmpty()
    val resources = LocalResources.current
    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_newRequest),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = onBack,
            )
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .testTag("request_summary_send"),
                text = requestButtonText(share, resources),
                enabled = draft.canSend,
                isLoading = sending,
                onClick = onRequest,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset),
        ) {
            Spacer(Modifier.height(CodeTheme.dimens.grid.x6))
            Text(
                text = amount,
                style = CodeTheme.typography.displayLarge,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = stringResource(R.string.label_requestTotal),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(CodeTheme.dimens.grid.x6))
            SummaryRow(
                label = stringResource(R.string.label_requestName),
                value = draft.title,
                detail = null,
                onClick = onName,
            )
            SummaryRow(
                label = stringResource(R.string.label_requestPeople),
                value = peopleValue(draft.people, resources),
                detail = peopleDetail(draft.people, resources),
                onClick = onPeople,
            )
            SummaryRow(
                label = stringResource(R.string.label_requestTiming),
                value = deadlineValue(draft.deadline),
                detail = stringResource(R.string.subtitle_requestAutoCloses),
                onClick = onTiming,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, detail: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CodeTheme.dimens.grid.x3)
            .clip(CodeTheme.shapes.medium)
            .background(White05)
            .clickable(onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)) {
            Text(text = label, style = CodeTheme.typography.caption, color = CodeTheme.colors.textSecondary)
            Text(
                text = value,
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(text = detail, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textSecondary)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun deadlineValue(deadline: NewRequestDraft.Deadline): String = when (deadline) {
    is NewRequestDraft.Deadline.Preset ->
        pluralStringResource(R.plurals.value_requestDaysToPay, deadline.daysToPay, deadline.daysToPay)
    is NewRequestDraft.Deadline.Custom -> {
        val at = deadline.at.toJavaInstant().atZone(ZoneId.systemDefault())
        val date = DateTimeFormatter.ofPattern(monthDayPattern(abbreviated = true)).format(at)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at)
        stringResource(R.string.value_requestUntil, "$date, $time")
    }
}

/** The request's name, in the editor the group's name uses. */
@Composable
private fun RequestNameContent(title: String, onBack: () -> Unit, onDone: (String) -> Unit) {
    val field = rememberTextFieldState(initialText = title)
    var length by remember { mutableIntStateOf(field.text.length) }
    var valid by remember { mutableStateOf(field.text.isNotBlank()) }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { text ->
            if (text.length > RequestMoneyViewModel.MAX_TITLE) {
                field.edit { replace(0, length, text.take(RequestMoneyViewModel.MAX_TITLE)) }
            }
            length = field.text.length
            valid = text.isNotBlank()
        }
    }
    NameEditScaffold(
        heading = stringResource(R.string.title_requestName),
        placeholder = stringResource(R.string.label_requestName),
        field = field,
        buttonText = stringResource(R.string.action_done),
        canSubmit = valid,
        remaining = RequestMoneyViewModel.MAX_TITLE - length,
        capitalization = KeyboardCapitalization.Sentences,
        onBack = onBack,
        onSubmit = { onDone(field.text.toString()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSendSheet(sending: Boolean, onSend: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !sending },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        sheetState = sheetState,
        containerColor = CodeTheme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            Text(
                text = stringResource(R.string.title_sendRequestConfirm),
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = stringResource(R.string.description_sendRequestConfirm),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
            )
            CodeButton(
                modifier = Modifier.fillMaxWidth().testTag("request_confirm_send"),
                text = stringResource(R.string.action_sendRequest),
                isLoading = sending,
                onClick = onSend,
            )
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_cancel),
                buttonState = ButtonState.Subtle,
                enabled = !sending,
                onClick = onDismiss,
            )
        }
    }
}

private fun Fiat.text(): String = formatted(Fiat.FormattingRule.Truncated)

/** The locale's month-and-day pattern ("October 10"), without an ordinal or a year. */
private fun monthDayPattern(abbreviated: Boolean = false): String =
    android.text.format.DateFormat.getBestDateTimePattern(
        java.util.Locale.getDefault(),
        if (abbreviated) "MMMd" else "MMMMd",
    )
