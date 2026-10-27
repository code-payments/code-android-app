package com.flipcash.app.messenger.internal.request

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLocale
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flipcash.app.messenger.internal.request.NewRequestDraft.Deadline
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.ui.theme.CodeButton
import com.flipcash.shared.chat.ui.media.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.hypot
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
 * Time To Pay: Starts and Closes with a summary, the preset chips, a grid of the days a request can
 * run to with the range drawn across it, the Time row and Done. The chip highlight, the closing-day
 * circle and the band move to a new choice rather than swapping. Figma row H (nodes
 * 12704:67, 12704:204, 12708:236). Done applies the choice; any other dismissal keeps [current].
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
    val reducedMotion = rememberReducedMotion()

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
            RangeFields(sheet)
            PresetChips(
                selected = (sheet.choice as? TimeToPay.Choice.Preset)?.preset,
                reducedMotion = reducedMotion,
                onChoose = { sheet = sheet.choosePreset(it) },
            )
            DayGrid(sheet, reducedMotion, onChoose = { sheet = sheet.chooseDay(it) })
            TimeRow(time = sheet.time, onClick = { pickingTime = true })
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_done),
                onClick = { onChoose(sheet.toDeadline()) },
            )
        }
    }

    if (pickingTime) {
        TimeDialog(
            initial = sheet.time,
            onPicked = { pickingTime = false; sheet = sheet.chooseTime(it) },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun RangeFields(sheet: TimeToPay) {
    val closes: String
    val summary: String
    when (val choice = sheet.choice) {
        is TimeToPay.Choice.Preset -> {
            val around = sheet.selectedDay.format(DateTimeFormatter.ofPattern(bestPattern("EEEMMMd")))
            closes = stringResource(R.string.value_timeToPayClosesAround, around)
            summary = pluralStringResource(
                R.plurals.description_timeToPayPreset,
                choice.preset.daysToPay,
                choice.preset.daysToPay,
                stringResource(choice.preset.label),
            )
        }
        is TimeToPay.Choice.Custom -> {
            val local = sheet.customAt!!.toJavaInstant().atZone(sheet.zone)
            val date = DateTimeFormatter.ofPattern(bestPattern("EEEMMMd")).format(local)
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(local)
            closes = "$date, $time"
            val days = sheet.daysFromNow.toInt()
            summary = if (days == 0) stringResource(R.string.description_timeToPayCustomToday)
            else pluralStringResource(R.plurals.description_timeToPayCustom, days, days)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
            RangeField(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.label_timeToPayStarts),
                value = stringResource(R.string.value_timeToPayStarts),
                outlined = false,
            )
            // Closes is the value this sheet edits, so it is the one read out when it changes.
            RangeField(
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                label = stringResource(R.string.label_timeToPayCloses),
                value = closes,
                outlined = true,
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = summary,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RangeField(label: String, value: String, outlined: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(White10)
            .then(if (outlined) Modifier.border(1.5.dp, White, shape) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = CodeTheme.dimens.grid.x2),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, style = CodeTheme.typography.caption, color = CodeTheme.colors.textSecondary)
        Text(
            text = value,
            style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CodeTheme.colors.textMain,
            // A custom day and time is wider than a phone-width half; shrink it before cutting it.
            autoSize = TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = CodeTheme.typography.textMedium.fontSize,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The preset chips. One white highlight slides to the chosen chip and fades out when the choice
 * becomes custom.
 */
@Composable
private fun PresetChips(selected: Deadline.Preset?, reducedMotion: Boolean, onChoose: (Deadline.Preset) -> Unit) {
    val bounds = remember { mutableStateMapOf<Deadline.Preset, Rect>() }
    val highlight = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val shown = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = selected?.let { bounds[it] }
    LaunchedEffect(selected, target) {
        val spec = TimeToPayMotion.spec<Float>(reducedMotion)
        when {
            selected == null -> shown.animateTo(0f, spec)
            target == null -> Unit
            !placed -> {
                highlight.snapTo(target)
                shown.snapTo(1f)
                placed = true
            }
            else -> {
                launch { highlight.animateTo(target, TimeToPayMotion.spec(reducedMotion)) }
                shown.animateTo(1f, spec)
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .drawBehind {
                bounds.values.forEach { chip ->
                    drawRoundRect(White10, chip.topLeft, chip.size, CornerRadius(chip.height / 2))
                }
                val chip = highlight.value
                drawRoundRect(White, chip.topLeft, chip.size, CornerRadius(chip.height / 2), alpha = shown.value)
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Deadline.Preset.entries.forEach { preset ->
            val isSelected = preset == selected
            val color by animateColorAsState(
                targetValue = if (isSelected) Color.Black else CodeTheme.colors.textMain,
                animationSpec = TimeToPayMotion.spec(reducedMotion),
                label = "chip text",
            )
            Text(
                modifier = Modifier
                    .onPlaced { bounds[preset] = Rect(it.positionInParent(), it.size.toSize()) }
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onChoose(preset) }
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                text = stringResource(preset.label),
                style = CodeTheme.typography.caption,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/**
 * The days a request can run to. The range draws over the grid as one layer: the band as one span
 * per week row, today's grey circle, and one white closing-day circle that slides to a new day,
 * across rows when the change crosses a week.
 */
@Composable
private fun DayGrid(sheet: TimeToPay, reducedMotion: Boolean, onChoose: (LocalDate) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val days = remember(sheet.today) { TimeToPay.grid(sheet.today, WeekFields.of(locale).firstDayOfWeek) }
    val monthLabel = remember { DateTimeFormatter.ofPattern("MMM", locale) }
    val fullDate = remember { DateTimeFormatter.ofPattern(bestPattern("EEEEMMMMd"), locale) }
    val startIndex = days.indexOf(sheet.today)
    val endIndex = days.indexOf(sheet.selectedDay)
    // The end as a grid index for the band, and as a column and row for the circle.
    val bandEnd = remember { Animatable(endIndex.toFloat()) }
    val circle = remember { Animatable(cellOf(endIndex), Offset.VectorConverter) }
    LaunchedEffect(endIndex) {
        launch { bandEnd.animateTo(endIndex.toFloat(), TimeToPayMotion.spec(reducedMotion)) }
        circle.animateTo(cellOf(endIndex), TimeToPayMotion.spec(reducedMotion))
    }
    val gap = CodeTheme.dimens.grid.x1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(White05)
            .padding(CodeTheme.dimens.grid.x2),
        verticalArrangement = Arrangement.spacedBy(gap),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { rangeLayer(startIndex, endIndex, bandEnd.value, circle.value) },
            verticalArrangement = Arrangement.spacedBy(TimeToPayGrid.RowGap),
        ) {
            days.chunked(7).forEachIndexed { row, week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEachIndexed { column, day ->
                        val index = row * 7 + column
                        // A day turns black as the moving circle settles over it, not as it passes.
                        val cover = coverOf(circle.value, column, row)
                        DayCell(
                            modifier = Modifier.weight(1f),
                            day = day,
                            enabled = sheet.enabled(day),
                            mark = when {
                                cover > 0f -> TimeToPay.RangeCell.Mark.End
                                index == startIndex && endIndex != startIndex -> TimeToPay.RangeCell.Mark.Start
                                else -> TimeToPay.RangeCell.Mark.None
                            },
                            selected = index == endIndex,
                            cover = cover,
                            month = if (day.dayOfMonth == 1) day.format(monthLabel).uppercase(locale) else null,
                            monthTone = sheet.monthTone(day),
                            label = day.format(fullDate),
                            onClick = { onChoose(day) },
                        )
                    }
                }
            }
        }
    }
}

private fun cellOf(index: Int) = Offset((index % 7).toFloat(), (index / 7).toFloat())

/** How far the circle at [circle] covers the day at [column], [row]: 1 within a quarter cell, 0 past half. */
private fun coverOf(circle: Offset, column: Int, row: Int): Float {
    val distance = hypot(circle.x - column, circle.y - row)
    return ((0.5f - distance) / 0.25f).coerceIn(0f, 1f)
}

/** The band, today's circle and the closing-day circle at [circle] (column, row), under the day numbers. */
private fun DrawScope.rangeLayer(startIndex: Int, endIndex: Int, bandEnd: Float, circle: Offset) {
    val cell = size.width / 7
    val cellHeight = TimeToPayGrid.CellHeight.toPx()
    val gap = TimeToPayGrid.RowGap.toPx()
    val height = TimeToPayGrid.Circle.toPx()
    val rtl = layoutDirection == LayoutDirection.Rtl
    fun x(column: Float) = if (rtl) size.width - column * cell else column * cell
    fun rowTop(row: Float) = row * (cellHeight + gap)
    TimeToPay.bandSpans(startIndex, bandEnd).forEach { span ->
        val left = minOf(x(span.from), x(span.to))
        val right = maxOf(x(span.from), x(span.to))
        // A span narrower than it is tall would draw as a thin capsule at the row's edge; fade it in.
        val top = rowTop(span.row.toFloat()) + (cellHeight - height) / 2
        val cap = CornerRadius(height / 2)
        val roundLeft = if (rtl) span.roundTo else span.roundFrom
        val roundRight = if (rtl) span.roundFrom else span.roundTo
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = top + height,
                        topLeftCornerRadius = if (roundLeft) cap else CornerRadius.Zero,
                        bottomLeftCornerRadius = if (roundLeft) cap else CornerRadius.Zero,
                        topRightCornerRadius = if (roundRight) cap else CornerRadius.Zero,
                        bottomRightCornerRadius = if (roundRight) cap else CornerRadius.Zero,
                    )
                )
            },
            color = RangeBand,
            alpha = ((right - left) / height).coerceIn(0f, 1f),
        )
    }
    if (endIndex != startIndex) {
        val start = cellOf(startIndex)
        drawCircle(RangeStart, height / 2, Offset(x(start.x + 0.5f), rowTop(start.y) + cellHeight / 2))
    }
    drawCircle(White, height / 2, Offset(x(circle.x + 0.5f), rowTop(circle.y) + cellHeight / 2))
}

@Composable
private fun DayCell(
    day: LocalDate,
    enabled: Boolean,
    mark: TimeToPay.RangeCell.Mark,
    selected: Boolean,
    cover: Float,
    month: String?,
    monthTone: TimeToPay.MonthTone,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(TimeToPayGrid.CellHeight)
            .then(
                if (enabled) {
                    Modifier
                        .clickable(
                            interactionSource = null,
                            indication = DayRipple,
                            role = Role.Button,
                            onClick = onClick,
                        )
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
        val text = when (mark) {
            TimeToPay.RangeCell.Mark.End -> lerp(CodeTheme.colors.textMain, Color.Black, cover)
            TimeToPay.RangeCell.Mark.Start -> White
            TimeToPay.RangeCell.Mark.None ->
                if (enabled) CodeTheme.colors.textMain else CodeTheme.colors.textSecondary.copy(alpha = 0.5f)
        }
        val number = CodeTheme.typography.textMedium.let {
            if (mark == TimeToPay.RangeCell.Mark.None) it else it.copy(fontWeight = FontWeight.Bold)
        }
        if (month == null) {
            Text(text = day.dayOfMonth.toString(), style = number, color = text)
        } else {
            // Label and number centre as one group in the circle, trimmed to their glyphs so the
            // space above the label matches the space below the number.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = month,
                    style = number.copy(
                        fontSize = MonthLabelSize,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = MonthLabelSize,
                        lineHeightStyle = TightLines,
                    ),
                    color = monthColor(monthTone, cover, enabled),
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    style = number.copy(lineHeight = number.fontSize, lineHeightStyle = TightLines),
                    color = text,
                )
            }
        }
    }
}

/** The month label's grey for [tone], blending to the closing circle's as it settles over the day. */
private fun monthColor(tone: TimeToPay.MonthTone, cover: Float, enabled: Boolean): Color = when {
    tone == TimeToPay.MonthTone.Start -> MonthOnStart
    tone == TimeToPay.MonthTone.End || cover > 0f ->
        lerp(if (tone == TimeToPay.MonthTone.Band) MonthInBand else MonthPlain, MonthOnEnd, cover)
    tone == TimeToPay.MonthTone.Band && enabled -> MonthInBand
    else -> MonthPlain
}

/** Clips the press ripple to the 34 dp selection circle; an unbounded ripple centres on the cell. */
private val DayRipple = ripple(bounded = false, radius = TimeToPayGrid.Circle / 2)

/** The day grid's geometry, matching iOS: rows of 38 dp cells 6 dp apart, a 34 dp circle centred in each. */
internal object TimeToPayGrid {
    val CellHeight = 38.dp
    val RowGap = 6.dp
    val Circle = 34.dp
}

private val MonthLabelSize = 8.sp
private val TightLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
private val MonthPlain = Color(0xFF7B7B7B)
private val MonthInBand = Color(0xFF9E9E9E)
private val MonthOnEnd = Color(0xFF595959)
private val MonthOnStart = Color(0xFFD9D9D9)

/** How the sheet's selection moves: a spring, or straight to the new place with reduce motion on. */
internal object TimeToPayMotion {
    fun <T> spec(reducedMotion: Boolean): AnimationSpec<T> =
        if (reducedMotion) snap() else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
}

private val RangeStart = Color(0xFF6B6B6E)
private val RangeBand = Color.White.copy(alpha = 0.12f)

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
