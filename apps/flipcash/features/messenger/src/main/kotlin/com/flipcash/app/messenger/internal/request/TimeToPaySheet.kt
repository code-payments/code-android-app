package com.flipcash.app.messenger.internal.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flipcash.app.messenger.internal.request.NewRequestDraft.Deadline
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.ui.theme.CodeButton
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.toJavaInstant

/**
 * Time To Pay: a readout, the preset chips, a grid of the days a request can run to, a Time row
 * for a custom day and Done. Figma row H (nodes 12704:67, 12704:204, 12708:236). Done applies the
 * choice; any other dismissal keeps [current].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeToPaySheet(
    current: Deadline,
    onChoose: (Deadline) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var sheet by remember { mutableStateOf(TimeToPay.of(current, Clock.System.now(), zone)) }
    var pickingTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.title_timeToPay),
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
            )
            Readout(sheet)
            PresetChips(
                selected = (sheet.choice as? TimeToPay.Choice.Preset)?.preset,
                onChoose = { sheet = sheet.choosePreset(it) },
            )
            DayGrid(sheet, onChoose = { sheet = sheet.chooseDay(it) })
            (sheet.choice as? TimeToPay.Choice.Custom)?.let { custom ->
                TimeRow(time = custom.time, onClick = { pickingTime = true })
            }
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_done),
                onClick = { onChoose(sheet.toDeadline()) },
            )
        }
    }

    val custom = sheet.choice as? TimeToPay.Choice.Custom
    if (pickingTime && custom != null) {
        TimeDialog(
            initial = custom.time,
            onPicked = { pickingTime = false; sheet = sheet.chooseTime(it) },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun Readout(sheet: TimeToPay) {
    val (large, small) = when (val choice = sheet.choice) {
        is TimeToPay.Choice.Preset -> {
            val around = sheet.selectedDay.format(DateTimeFormatter.ofPattern(bestPattern("EEEMMMd")))
            stringResource(choice.preset.label) to pluralStringResource(
                R.plurals.description_timeToPayPreset,
                choice.preset.daysToPay,
                choice.preset.daysToPay,
                around,
            )
        }
        is TimeToPay.Choice.Custom -> {
            val days = sheet.daysFromNow.toInt()
            untilText(sheet.customAt!!) to
                if (days == 0) stringResource(R.string.description_timeToPayCustomToday)
                else pluralStringResource(R.plurals.description_timeToPayCustom, days, days)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Text(text = large, style = CodeTheme.typography.displaySmall, color = CodeTheme.colors.textMain, maxLines = 1)
        Text(
            text = small,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PresetChips(selected: Deadline.Preset?, onChoose: (Deadline.Preset) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Deadline.Preset.entries.forEach { preset ->
            val isSelected = preset == selected
            Text(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) White else White10)
                    .clickable(role = Role.Button) { onChoose(preset) }
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                text = stringResource(preset.label),
                style = CodeTheme.typography.caption,
                color = if (isSelected) Color.Black else CodeTheme.colors.textMain,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayGrid(sheet: TimeToPay, onChoose: (LocalDate) -> Unit) {
    val locale = Locale.getDefault()
    val days = remember(sheet.today) { TimeToPay.grid(sheet.today, WeekFields.of(locale).firstDayOfWeek) }
    val selected = sheet.selectedDay
    val monthLabel = remember { DateTimeFormatter.ofPattern("MMM", locale) }
    val fullDate = remember { DateTimeFormatter.ofPattern(bestPattern("EEEEMMMMd"), locale) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(White05)
            .padding(CodeTheme.dimens.grid.x2),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Row(modifier = Modifier.fillMaxWidth().semantics { invisibleToUser() }) {
            days.take(7).forEach { day ->
                Text(
                    modifier = Modifier.weight(1f),
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        modifier = Modifier.weight(1f),
                        day = day,
                        enabled = sheet.enabled(day),
                        today = day == sheet.today,
                        selected = day == selected,
                        between = day.isAfter(sheet.today) && day.isBefore(selected),
                        month = if (day.dayOfMonth == 1) day.format(monthLabel).uppercase(locale) else null,
                        label = day.format(fullDate),
                        onClick = { onChoose(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    enabled: Boolean,
    today: Boolean,
    selected: Boolean,
    between: Boolean,
    month: String?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .then(if (between) Modifier.clip(RoundedCornerShape(6.dp)).background(White10) else Modifier)
            .then(
                if (enabled) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .clearAndSetSemantics {
                            contentDescription = label
                            role = Role.Button
                            this.selected = selected
                        }
                } else {
                    Modifier.clearAndSetSemantics { invisibleToUser() }
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    when {
                        selected -> Modifier.clip(CircleShape).background(White)
                        today -> Modifier.border(1.dp, CodeTheme.colors.textSecondary, CircleShape)
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = CodeTheme.typography.textMedium,
                color = when {
                    selected -> Color.Black
                    enabled -> CodeTheme.colors.textMain
                    else -> CodeTheme.colors.textSecondary.copy(alpha = 0.5f)
                },
            )
        }
        if (month != null && !selected) {
            Text(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 1.dp),
                text = month,
                style = CodeTheme.typography.caption.copy(fontSize = CodeTheme.typography.caption.fontSize * 0.75f),
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TimeRow(time: LocalTime, onClick: () -> Unit) {
    val text = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(White05)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.label_timeToPayTime),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(White10)
                .padding(horizontal = CodeTheme.dimens.grid.x2, vertical = CodeTheme.dimens.grid.x1),
            text = text,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
    }
}

/** Material 3's time picker in a dialog, dial or keyboard. Cancel keeps the old time; OK applies it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) = PickerTheme {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    var typing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(CodeTheme.shapes.large)
                // surfaceVariant is translucent; lay it on the background so the sheet doesn't show through.
                .background(CodeTheme.colors.background)
                .background(CodeTheme.colors.surfaceVariant)
                .padding(CodeTheme.dimens.inset),
        ) {
            Text(
                modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x3),
                text = stringResource(R.string.title_timeToPaySelectTime),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                if (typing) TimeInput(state = state) else TimePicker(state = state)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { typing = !typing }) {
                    Icon(
                        imageVector = if (typing) Icons.Outlined.Schedule else Icons.Outlined.Keyboard,
                        contentDescription = stringResource(
                            if (typing) R.string.cd_timeToPayDial else R.string.cd_timeToPayKeyboard,
                        ),
                        tint = CodeTheme.colors.textSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) {
                    Text(stringResource(R.string.action_timeToPayOk))
                }
            }
        }
    }
}

private val Deadline.Preset.label: Int
    get() = when (this) {
        Deadline.Preset.Day -> R.string.option_timeToPay_day
        Deadline.Preset.ThreeDays -> R.string.option_timeToPay_threeDays
        Deadline.Preset.Week -> R.string.option_timeToPay_week
        Deadline.Preset.TwoWeeks -> R.string.option_timeToPay_twoWeeks
        Deadline.Preset.FourWeeks -> R.string.option_timeToPay_fourWeeks
    }

/** "Until Nov 5, 6:00 PM": the summary's custom value and the custom readout. */
@Composable
internal fun untilText(at: kotlin.time.Instant): String {
    val local = at.toJavaInstant().atZone(ZoneId.systemDefault())
    val date = DateTimeFormatter.ofPattern(bestPattern("MMMd")).format(local)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(local)
    return stringResource(R.string.value_requestUntil, "$date, $time")
}

private fun bestPattern(skeleton: String): String =
    android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)

/**
 * Material 3's pickers read `MaterialTheme`, which the app never sets up (it runs on Material 2).
 * This maps the app's colors and type onto it so the pickers look like the rest of the app.
 */
@Composable
private fun PickerTheme(content: @Composable () -> Unit) {
    val colors = CodeTheme.colors
    val type = CodeTheme.typography
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colors.textMain,
            onPrimary = colors.background,
            primaryContainer = colors.textMain,
            onPrimaryContainer = colors.background,
            secondaryContainer = White10,
            onSecondaryContainer = colors.textMain,
            tertiaryContainer = White10,
            onTertiaryContainer = colors.textMain,
            background = colors.background,
            onBackground = colors.textMain,
            surface = colors.surfaceVariant,
            onSurface = colors.textMain,
            surfaceVariant = White10,
            onSurfaceVariant = colors.textMain,
            surfaceContainer = colors.surfaceVariant,
            surfaceContainerLow = colors.surfaceVariant,
            surfaceContainerHigh = colors.surfaceVariant,
            surfaceContainerHighest = White10,
            surfaceTint = Color.Transparent,
            outline = colors.border,
            outlineVariant = colors.divider,
            error = colors.errorText,
        ),
        typography = Typography(
            displayLarge = type.displaySmall,
            headlineLarge = type.screenTitleLarge,
            headlineMedium = type.screenTitle,
            headlineSmall = type.screenTitle,
            titleLarge = type.screenTitle,
            titleMedium = type.textLarge,
            titleSmall = type.textMedium,
            bodyLarge = type.textLarge,
            bodyMedium = type.textMedium,
            bodySmall = type.textSmall,
            labelLarge = type.textMedium,
            labelMedium = type.textSmall,
            labelSmall = type.caption,
        ),
        content = content,
    )
}
