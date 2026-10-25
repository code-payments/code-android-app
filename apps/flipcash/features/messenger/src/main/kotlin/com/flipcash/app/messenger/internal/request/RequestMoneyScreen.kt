package com.flipcash.app.messenger.internal.request

import com.getcode.theme.White05
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.messenger.internal.screens.cash.GroupSelectPeopleContent
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Request Money, frames 03–06 and 25 of the group requests spec: history, then the create flow
 * behind New Request. Its stages live in [RequestMoneyViewModel] rather than as steps of their
 * own, so the draft has one owner and leaving the screen drops it.
 */
@Composable
internal fun RequestMoneyScreen(
    chatId: ChatId,
    onClose: () -> Unit,
    onOpenRequest: (PaymentRequest) -> Unit,
) {
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
    BackHandler(enabled = state.stage != RequestMoneyViewModel.Stage.History) { back() }

    when (state.stage) {
        RequestMoneyViewModel.Stage.History -> {
            val requests by viewModel.chatRequests.collectAsStateWithLifecycle()
            RequestHistoryContent(
                requests = requests,
                loadingMore = state.loadingPrevious,
                canLoadMore = !state.previousEnd,
                onBack = onClose,
                onNewRequest = viewModel::newRequest,
                onOpen = onOpenRequest,
                onLoadMore = viewModel::loadMorePrevious,
            )
        }
        RequestMoneyViewModel.Stage.Amount -> AmountEntryScreen(
            controller = viewModel.amountDelegate,
            onConfirm = viewModel::amountEntered,
            largeHeader = true,
            appBar = {
                AppBarWithTitle(
                    title = stringResource(R.string.title_amountPerPerson),
                    titleAlignment = Alignment.CenterHorizontally,
                    onBackIconClicked = back,
                )
            },
        )
        RequestMoneyViewModel.Stage.Summary -> state.draft?.let { draft ->
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
        RequestMoneyViewModel.Stage.People -> state.draft?.let { draft ->
            val members by viewModel.members.collectAsStateWithLifecycle()
            GroupSelectPeopleContent(
                candidates = members,
                selected = draft.shares,
                onBack = back,
                onToggle = { viewModel.toggle(it.userId) },
                onSend = back,
                buttonText = stringResource(R.string.action_done),
                buttonEnabled = draft.canContinue,
                viewerId = viewModel.viewerId,
            )
        }
        RequestMoneyViewModel.Stage.Name -> state.draft?.let { draft ->
            RequestNameContent(title = draft.title, onBack = back, onDone = viewModel::rename)
        }
    }

    when (state.sheet) {
        RequestMoneyViewModel.Sheet.None -> Unit
        RequestMoneyViewModel.Sheet.Deadline -> state.draft?.let { draft ->
            DeadlineSheet(
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
                onBackIconClicked = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x4),
        ) {
            item {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(vertical = CodeTheme.dimens.grid.x2)
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
                item { SectionHeader(stringResource(R.string.header_previousRequests)) }
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
private fun SectionHeader(text: String) {
    Text(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = CodeTheme.dimens.grid.x4, bottom = CodeTheme.dimens.grid.x1),
        text = text,
        style = CodeTheme.typography.caption,
        color = CodeTheme.colors.textSecondary,
    )
}

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.inset),
    ) {
        Row(
            modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = date, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textSecondary)
            }
            Text(text = progress, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textSecondary)
        }
        HorizontalDivider(color = CodeTheme.colors.divider)
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
    val amount = draft.perPerson?.text().orEmpty()
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
                text = pluralStringResource(
                    R.plurals.action_requestFromPeople,
                    draft.requestedCount,
                    draft.requestedCount,
                    amount,
                ),
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
                text = stringResource(R.string.label_perPerson),
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
                value = peopleValue(draft.people),
                detail = stringResource(R.string.subtitle_requestMarkedPaid).takeIf { draft.people.markedPaid },
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
private fun peopleValue(people: NewRequestDraft.People): String = when (people) {
    NewRequestDraft.People.Choose -> stringResource(R.string.value_requestPeople_choose)
    NewRequestDraft.People.EveryoneIncludingYou -> stringResource(R.string.value_requestPeople_everyoneIncludingYou)
    NewRequestDraft.People.EveryoneExceptYou -> stringResource(R.string.value_requestPeople_everyoneExceptYou)
    is NewRequestDraft.People.SomeIncludingYou ->
        pluralStringResource(R.plurals.value_requestPeople_someIncludingYou, people.others, people.others)
    is NewRequestDraft.People.Some -> pluralStringResource(R.plurals.value_requestPeople_some, people.count, people.count)
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

@Composable
private fun RequestNameContent(title: String, onBack: () -> Unit, onDone: (String) -> Unit) {
    val field = rememberTextFieldState(initialText = title)
    val focusRequester = remember { FocusRequester() }
    val trimmed = field.text.trim()
    var valid by remember { mutableStateOf(trimmed.isNotEmpty()) }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { text ->
            if (text.length > RequestMoneyViewModel.MAX_TITLE) {
                field.edit { replace(0, length, text.take(RequestMoneyViewModel.MAX_TITLE)) }
            }
            valid = text.isNotBlank()
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column {
        AppBarWithTitle(onBackIconClicked = onBack)
        CodeScaffold(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            topBar = {
                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.inset),
                    text = stringResource(R.string.label_requestName),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                )
            },
            bottomBar = {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    text = stringResource(R.string.action_done),
                    enabled = valid,
                    onClick = { onDone(field.text.toString()) },
                )
            },
        ) { padding ->
            DisplayTextInput(
                state = field,
                placeholder = stringResource(R.string.label_requestName),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = { if (valid) onDone(field.text.toString()) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineSheet(
    current: NewRequestDraft.Deadline,
    onChoose: (NewRequestDraft.Deadline) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingCustom by rememberSaveable { mutableStateOf(false) }
    if (pickingCustom) {
        CustomDeadlinePicker(
            initial = (current as? NewRequestDraft.Deadline.Custom)?.at,
            onPicked = { pickingCustom = false; onChoose(NewRequestDraft.Deadline.Custom(it)) },
            onDismiss = { pickingCustom = false },
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CodeTheme.colors.background,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = CodeTheme.dimens.grid.x4)) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                text = stringResource(R.string.title_timeToPay),
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
            )
            val presets = listOf(
                NewRequestDraft.Deadline.Preset.Day to R.string.option_timeToPay_day,
                NewRequestDraft.Deadline.Preset.ThreeDays to R.string.option_timeToPay_threeDays,
                NewRequestDraft.Deadline.Preset.Week to R.string.option_timeToPay_week,
                NewRequestDraft.Deadline.Preset.TwoWeeks to R.string.option_timeToPay_twoWeeks,
                NewRequestDraft.Deadline.Preset.FourWeeks to R.string.option_timeToPay_fourWeeks,
            )
            presets.forEach { (preset, label) ->
                DeadlineRow(stringResource(label), checked = current == preset) { onChoose(preset) }
            }
            DeadlineRow(
                stringResource(R.string.option_timeToPay_custom),
                checked = current is NewRequestDraft.Deadline.Custom,
            ) { pickingCustom = true }
        }
    }
}

@Composable
private fun DeadlineRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
        if (checked) Image(painter = painterResource(R.drawable.ic_checked), contentDescription = null)
    }
}

/**
 * The platform date picker, then its time picker, bounded to the spec's custom range. Not in the
 * design; the spec asks for the standard pickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDeadlinePicker(initial: Instant?, onPicked: (Instant) -> Unit, onDismiss: () -> Unit) {
    val range = remember { NewRequestDraft.Deadline.customRange(Clock.System.now()) }
    val zone = ZoneId.systemDefault()
    val start = remember { (initial ?: range.start).toJavaInstant().atZone(zone) }
    var pickedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }

    val date = pickedDate
    if (date == null) {
        val earliest = range.start.toJavaInstant().atZone(zone).toLocalDate()
        val latest = range.endInclusive.toJavaInstant().atZone(zone).toLocalDate()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    return !day.isBefore(earliest) && !day.isAfter(latest)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        pickedDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = dateState) }
        return
    }

    val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Column(
            modifier = Modifier.padding(CodeTheme.dimens.inset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimePicker(state = timeState)
            Row(horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = {
                    val at = ZonedDateTime.of(date, java.time.LocalTime.of(timeState.hour, timeState.minute), zone)
                        .toInstant()
                        .toKotlinInstant()
                    onPicked(at.coerceIn(range.start, range.endInclusive))
                }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
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
