package com.flipcash.app.notificationstatus.internal

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.auth.push.PushRegistrationOutcome
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.components.BottomScrollEdge
import com.getcode.ui.components.glass.floatingGlass
import com.getcode.ui.components.text.SectionHeader
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
internal fun NotificationStatusContent(
    state: NotificationStatusViewModel.State,
    topPadding: Dp,
    scrollState: ScrollState,
    hazeState: HazeState,
    onFix: (Fix) -> Unit,
    onToggleExpanded: () -> Unit,
    onSendTest: () -> Unit,
    onShareReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status
    val inset = CodeTheme.dimens.inset
    val density = LocalDensity.current
    // The pinned actions float over the page, so the page leaves room for them.
    var actionsHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(horizontal = inset),
        ) {
            // Settings' gap below its bar: the first section header's top padding.
            Spacer(Modifier.height(topPadding + CodeTheme.dimens.grid.x4))
            SummaryCard(status.summary)

            // All passing folds into one row; it stays as the toggle once expanded.
            AnimatedVisibility(
                visible = status.allPassing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(CodeTheme.dimens.grid.x3))
                    FoldedRow(count = status.rows.size, expanded = state.expanded, onClick = onToggleExpanded)
                }
            }
            AnimatedVisibility(
                visible = !status.allPassing || state.expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Group(
                        title = stringResource(R.string.header_notificationStatusPhone),
                        keys = PhoneRowKeys,
                        rows = status.phoneRows,
                        state = state,
                        onFix = onFix,
                    )
                    Group(
                        title = stringResource(R.string.header_notificationStatusFlipcash),
                        keys = listOf(RegistrationKey),
                        rows = status.flipcashRows,
                        state = state,
                        onFix = onFix,
                    )
                }
            }
            Spacer(Modifier.height(actionsHeight + CodeTheme.dimens.grid.x4))
        }

        // Content fades out under the actions while there is more to scroll to.
        BottomScrollEdge(
            hazeState = hazeState,
            scrollState = scrollState,
            height = actionsHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { actionsHeight = with(density) { it.height.toDp() } }
                .navigationBarsPadding()
                .padding(horizontal = inset)
                .padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    // "Send again" is translucent; the glass keeps it legible over the page.
                    .then(if (state.testSent) Modifier.floatingGlass(hazeState, CircleShape) else Modifier),
                text = stringResource(
                    if (state.testSent) R.string.action_sendTestNotificationAgain else R.string.action_sendTestNotification,
                ),
                buttonState = if (state.testSent) ButtonState.Filled10 else ButtonState.Filled,
                shape = CircleShape,
                enabled = status.canSendTest,
                onClick = onSendTest,
            )
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_shareReport),
                buttonState = ButtonState.Subtle,
                textColor = CodeTheme.colors.textMain,
                onClick = onShareReport,
            )
            val (note, color) = when {
                !status.canSendTest ->
                    stringResource(R.string.description_notificationStatusTestDisabled) to CodeTheme.colors.textSecondary
                state.testSent ->
                    stringResource(R.string.description_notificationStatusTestSent) to CodeTheme.colors.successText
                else ->
                    stringResource(R.string.description_notificationStatusFooter) to CodeTheme.colors.textSecondary
            }
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                text = note,
                style = CodeTheme.typography.textSmall,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: Summary) {
    val title = when (summary) {
        Summary.Working -> stringResource(R.string.title_notificationStatusWorking)
        Summary.Checking -> stringResource(R.string.title_notificationStatusChecking)
        Summary.NotificationsOff -> stringResource(R.string.title_notificationStatusOff)
        is Summary.ThingsToFix ->
            pluralStringResource(R.plurals.title_notificationStatusThingsToFix, summary.count, summary.count)
        is Summary.ThingsToCheck ->
            pluralStringResource(R.plurals.title_notificationStatusThingsToCheck, summary.count, summary.count)
    }
    val mark = when (summary) {
        Summary.Working -> CheckStatus.Pass
        Summary.Checking -> CheckStatus.Checking
        is Summary.ThingsToCheck -> CheckStatus.Warning
        Summary.NotificationsOff, is Summary.ThingsToFix -> CheckStatus.Fail
    }
    Card {
        Row(
            modifier = Modifier.padding(CodeTheme.dimens.grid.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(targetState = mark, label = "summary mark") { StatusMark(it, width = 26.dp, height = 40.dp) }
            Spacer(Modifier.width(CodeTheme.dimens.grid.x3))
            Column {
                Crossfade(targetState = title, label = "summary title") {
                    Text(it, style = CodeTheme.typography.textLarge, color = CodeTheme.colors.textMain)
                }
                Text(
                    stringResource(R.string.subtitle_notificationStatusCheckedJustNow),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun FoldedRow(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(CodeTheme.dimens.grid.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusMark(CheckStatus.Pass)
            Spacer(Modifier.width(CodeTheme.dimens.grid.x3))
            Text(
                modifier = Modifier.weight(1f),
                text = pluralStringResource(R.plurals.title_notificationStatusChecksPassed, count, count),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Icon(
                modifier = Modifier.rotate(chevron),
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
        }
    }
}

/** Holds the last row shown for a key, so a row leaving the list can animate out. */
private class LastRow(var row: CheckRow? = null)

@Composable
private fun Group(
    title: String,
    keys: List<String>,
    rows: List<CheckRow>,
    state: NotificationStatusViewModel.State,
    onFix: (Fix) -> Unit,
) {
    SectionHeader(title = title.uppercase())
    Spacer(Modifier.height(CodeTheme.dimens.grid.x1))
    Card {
        Column(modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2)) {
            val byKey = rows.associateBy { it.key }
            // Every possible row in a fixed order: one that comes or goes animates in place, and the
            // others never move.
            keys.forEach { key ->
                val current = byKey[key]
                val last = remember(key) { LastRow() }
                if (current != null) last.row = current
                AnimatedVisibility(
                    visible = current != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    last.row?.let { CheckRowItem(it, state, onFix) }
                }
            }
        }
    }
}

@Composable
private fun CheckRowItem(row: CheckRow, state: NotificationStatusViewModel.State, onFix: (Fix) -> Unit) {
    val text = row.text(state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // One height for the checking and settled states, so a result doesn't move the rows
            // below it.
            .heightIn(min = CodeTheme.dimens.grid.x15)
            .animateContentSize()
            .padding(horizontal = CodeTheme.dimens.grid.x4, vertical = CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(targetState = row.status, label = "row mark") { StatusMark(it) }
        Spacer(Modifier.width(CodeTheme.dimens.grid.x3))
        Crossfade(targetState = text, modifier = Modifier.weight(1f), label = "row text") { (title, detail) ->
            Column {
                Text(title, style = CodeTheme.typography.textMedium, color = CodeTheme.colors.textMain)
                Text(detail, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textSecondary)
            }
        }
        AnimatedContent(
            targetState = row.fix,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "row fix",
        ) { fix ->
            if (fix != null) FixButton(fix, onFix)
        }
    }
}

@Composable
private fun FixButton(fix: Fix, onFix: (Fix) -> Unit) {
    CodeButton(
        modifier = Modifier.padding(start = CodeTheme.dimens.grid.x2),
        text = stringResource(
            when (fix) {
                Fix.TurnOn -> R.string.action_turnOn
                Fix.TryAgain -> R.string.action_tryAgain
                else -> R.string.action_fix
            },
        ),
        buttonState = ButtonState.Filled,
        shape = CircleShape,
        style = CodeTheme.typography.textSmall,
        overrideContentPadding = true,
        contentPadding = PaddingValues(
            horizontal = CodeTheme.dimens.grid.x3,
            vertical = CodeTheme.dimens.grid.x2,
        ),
        onClick = { onFix(fix) },
    )
}

@Composable
private fun CheckRow.text(state: NotificationStatusViewModel.State): Pair<String, String> = when (this) {
    is CheckRow.Pending -> stringResource(
        when (key) {
            PermissionKey -> R.string.title_notificationCheckAllowed
            OtherChannelsKey -> R.string.title_notificationCheckOtherCategories
            BatteryKey -> R.string.title_notificationCheckBattery
            BackgroundKey -> R.string.title_notificationCheckBackground
            else -> R.string.title_notificationCheckPlayServices
        },
    ) to stringResource(R.string.title_notificationStatusChecking)

    is CheckRow.Permission -> if (allowed) {
        stringResource(R.string.title_notificationCheckAllowed) to stringResource(R.string.subtitle_notificationCheckAllowed)
    } else {
        stringResource(R.string.title_notificationCheckNotAllowed) to stringResource(R.string.subtitle_notificationCheckNotAllowed)
    }

    is CheckRow.BlockedChannel -> when (channel) {
        StatusChannel.Chat -> R.string.title_notificationCheckChatOff to R.string.subtitle_notificationCheckChatOff
        StatusChannel.DepositWithdrawal -> R.string.title_notificationCheckDepositsOff to R.string.subtitle_notificationCheckDepositsOff
        StatusChannel.BuySell -> R.string.title_notificationCheckBuySellOff to R.string.subtitle_notificationCheckBuySellOff
        StatusChannel.Gain -> R.string.title_notificationCheckGainOff to R.string.subtitle_notificationCheckGainOff
        StatusChannel.ContactJoin -> R.string.title_notificationCheckContactsOff to R.string.subtitle_notificationCheckContactsOff
        StatusChannel.General -> R.string.title_notificationCheckGeneralOff to R.string.subtitle_notificationCheckGeneralOff
    }.let { (title, detail) -> stringResource(title) to stringResource(detail) }

    is CheckRow.OtherChannels ->
        stringResource(R.string.title_notificationCheckOtherCategories) to
            stringResource(R.string.subtitle_notificationCheckOtherCategories, categoryList(covered))

    is CheckRow.Battery -> if (optimized) {
        stringResource(R.string.title_notificationCheckBatteryOn) to stringResource(R.string.subtitle_notificationCheckBatteryOn)
    } else {
        stringResource(R.string.title_notificationCheckBattery) to stringResource(R.string.subtitle_notificationCheckBattery)
    }

    is CheckRow.Background -> when (status) {
        CheckStatus.Fail -> stringResource(R.string.title_notificationCheckBackgroundRestricted) to
            stringResource(R.string.subtitle_notificationCheckBackgroundRestricted)
        CheckStatus.Warning -> stringResource(R.string.title_notificationCheckBackground) to
            stringResource(R.string.subtitle_notificationCheckBackgroundLimited)
        else -> stringResource(R.string.title_notificationCheckBackground) to
            stringResource(R.string.subtitle_notificationCheckBackground, bucketName(bucket))
    }

    is CheckRow.GooglePlay -> when (status) {
        CheckStatus.Fail -> stringResource(R.string.title_notificationCheckPlayServicesMissing) to
            stringResource(R.string.subtitle_notificationCheckPlayServicesMissing)
        CheckStatus.Warning -> stringResource(R.string.title_notificationCheckPlayServices) to
            stringResource(R.string.subtitle_notificationCheckPlayServicesUpdate)
        else -> stringResource(R.string.title_notificationCheckPlayServices) to
            stringResource(R.string.subtitle_notificationCheckPlayServices)
    }

    is CheckRow.Registration -> when (val registration = this.state) {
        RegistrationState.Checking -> stringResource(R.string.title_notificationCheckRegistering) to
            stringResource(R.string.subtitle_notificationCheckRegistering)
        RegistrationState.Unknown -> stringResource(R.string.title_notificationCheckRegistered) to
            stringResource(R.string.subtitle_notificationCheckInterrupted)
        is RegistrationState.Finished -> when (registration.outcome) {
            PushRegistrationOutcome.Ok ->
                stringResource(R.string.title_notificationCheckRegistered) to
                    stringResource(R.string.subtitle_notificationCheckRegistered)
            PushRegistrationOutcome.NoToken ->
                stringResource(R.string.title_notificationCheckRegistered) to
                    stringResource(R.string.subtitle_notificationCheckNoToken)
            PushRegistrationOutcome.InvalidPushToken ->
                stringResource(R.string.title_notificationCheckRegistered) to
                    stringResource(R.string.subtitle_notificationCheckTokenRejected)
            PushRegistrationOutcome.Transport ->
                stringResource(R.string.title_notificationCheckUnreachable) to
                    (registration.lastConfirmedAt?.let {
                        stringResource(
                            R.string.subtitle_notificationCheckLastConfirmed,
                            DateUtils.getRelativeTimeSpanString(it.toEpochMilliseconds()).toString(),
                        )
                    } ?: stringResource(R.string.subtitle_notificationCheckUnreachable))
            PushRegistrationOutcome.Failed ->
                stringResource(R.string.title_notificationCheckRegistered) to
                    stringResource(R.string.subtitle_notificationCheckFailed)
        }
    }
}

// "Payments, gains and contacts". The general channel is the fallback, so it's named only when
// nothing else is on.
@Composable
private fun categoryList(covered: List<StatusChannel>): String {
    val labels = covered
        .filter { it != StatusChannel.General || covered.size == 1 }
        .map {
            when (it) {
                StatusChannel.Chat -> R.string.label_notificationCategoryChat
                StatusChannel.DepositWithdrawal, StatusChannel.BuySell -> R.string.label_notificationCategoryPayments
                StatusChannel.Gain -> R.string.label_notificationCategoryGains
                StatusChannel.ContactJoin -> R.string.label_notificationCategoryContacts
                StatusChannel.General -> R.string.label_notificationCategoryGeneral
            }
        }
        .distinct()
        .map { stringResource(it) }
    val joined = if (labels.size < 2) {
        labels.joinToString()
    } else {
        stringResource(R.string.label_listAnd, labels.dropLast(1).joinToString(), labels.last())
    }
    return joined.replaceFirstChar { it.uppercase() }
}

// "working_set" -> "Working set"
private fun bucketName(bucket: String): String =
    bucket.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(White05, CodeTheme.shapes.medium)
            .then(modifier),
    ) { content() }
}

@Composable
private fun StatusMark(status: CheckStatus, width: Dp = 16.dp, height: Dp = 24.dp) {
    if (status == CheckStatus.Checking) {
        Box(Modifier.size(width = width, height = height), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(width),
                color = CodeTheme.colors.textSecondary,
                strokeWidth = 2.dp,
            )
        }
        return
    }
    val (fill: Color, icon) = when (status) {
        CheckStatus.Pass -> CodeTheme.colors.successText to Icons.Rounded.Check
        CheckStatus.Warning -> CodeTheme.colors.warningText to Icons.Rounded.PriorityHigh
        CheckStatus.Fail -> CodeTheme.colors.error to Icons.Rounded.Close
        CheckStatus.Unknown, CheckStatus.Checking -> CodeTheme.colors.textSecondary to Icons.Rounded.Remove
    }
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(width * 0.75f),
            tint = CodeTheme.colors.background,
        )
    }
}
