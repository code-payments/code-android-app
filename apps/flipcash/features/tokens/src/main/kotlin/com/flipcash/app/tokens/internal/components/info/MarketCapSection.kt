package com.flipcash.app.tokens.internal.components.info

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.tokens.data.MarketCapPoint
import com.flipcash.app.tokens.data.Period
import com.flipcash.app.tokens.data.collapse
import com.flipcash.app.tokens.internal.components.marketcap.MarketCapChart
import com.flipcash.app.tokens.internal.components.marketcap.TARGET_POINTS
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.minus
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.charts.LineTrend
import com.getcode.ui.components.charts.TrendType
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.utils.calculateEndPadding
import com.getcode.ui.utils.calculateStartPadding
import com.getcode.util.format
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun MarketCapSection(
    marketCap: Fiat,
    chartEnabled: Boolean,
    rawHistoricalData: List<MarketCapPoint>,
    selectedPeriod: Period,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onPeriodSelected: (Period) -> Unit
) {
    var highlightedCapPoint by remember {
        mutableStateOf<MarketCapPoint?>(null)
    }

    val startCap by remember(selectedPeriod, rawHistoricalData) {
        derivedStateOf {
            val startQuarks =
                rawHistoricalData.collapse(
                    period = selectedPeriod,
                    targetPoints = TARGET_POINTS.toInt()
                ).firstOrNull()?.y ?: return@derivedStateOf null
            Fiat(startQuarks, marketCap.currencyCode)
        }
    }

    val marketCapAtPoint by remember(marketCap, highlightedCapPoint) {
        derivedStateOf {
            val point = highlightedCapPoint ?: return@derivedStateOf marketCap
            val capAtPoint = Fiat(point.y, marketCap.currencyCode)
            capAtPoint
        }
    }

    val marketCapDiff by remember(marketCap, startCap) {
        derivedStateOf {
            val start = startCap ?: return@derivedStateOf null
            marketCap - start
        }
    }

    Column(
        modifier = modifier,
    ) {
        Text(
            modifier = Modifier.padding(start = contentPadding.calculateStartPadding()),
            text = stringResource(R.string.subtitle_marketCap),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textSecondary,
        )

        AnimatedNumberText(
            modifier = Modifier.padding(start = contentPadding.calculateStartPadding()),
            value = marketCapAtPoint.formatted(),
            style = CodeTheme.typography.displayMedium,
            color = CodeTheme.colors.textMain,
        )

        if (chartEnabled) {
            marketCapDiff?.let { change ->
                Box(
                    modifier = Modifier
                        .padding(
                            start = contentPadding.calculateStartPadding(),
                            top = CodeTheme.dimens.grid.x1,
                        )
                ) {
                    MarketCapChangeLabel(
                        change = change,
                        isVisible = highlightedCapPoint == null,
                        period = selectedPeriod,
                    )

                    HighlightedPointLabel(
                        point = highlightedCapPoint,
                        isVisible = highlightedCapPoint != null,
                        period = selectedPeriod,
                    )
                }
            }

            MarketCapChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(240.dp),
                chartPadding = PaddingValues(end = contentPadding.calculateEndPadding()),
                periodPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(),
                    end = contentPadding.calculateEndPadding(),
                ),
                data = rawHistoricalData,
                trendType = TrendType.FirstVsLast,
                selectedPeriod = selectedPeriod,
                onPointHighlighted = { highlightedCapPoint = it },
                onPeriodSelected = onPeriodSelected,
            )
        }
    }
}

@Composable
private fun MarketCapChangeLabel(
    change: Fiat,
    isVisible: Boolean,
    period: Period,
) {
    val isPositiveChange = change.decimalValue >= 0
    val changeColor by animateColorAsState(
        if (isPositiveChange) LineTrend.Up.color else LineTrend.Down.color
    )

    val alpha by animateFloatAsState(
        if (isVisible) 1f else 0f
    )

    Text(
        modifier = Modifier
            .alpha(alpha)
            .background(
                color = changeColor.copy(0.20f),
                shape = MaterialTheme.shapes.extraSmall,
            )
            .padding(
                vertical = 2.dp,
                horizontal = CodeTheme.dimens.grid.x1
            ),
        text = change.formatted(
            extraPrefix = if (change.decimalValue >= 0) "+" else "-",
            suffix = when (period) {
                Period.All -> stringResource(R.string.label_marketCapAllTime)
                Period.Day -> stringResource(R.string.label_marketCapDay)
                Period.Week -> stringResource(R.string.label_marketCapWeek)
                Period.Month -> stringResource(R.string.label_marketCapMonth)
                Period.Year -> stringResource(R.string.label_marketCapYear)
            }
        ),
        style = CodeTheme.typography.textSmall,
        color = changeColor,
    )
}

@Composable
private fun HighlightedPointLabel(
    point: MarketCapPoint?,
    period: Period,
    isVisible: Boolean,
) {
    val alpha by animateFloatAsState(
        if (isVisible) 1f else 0f
    )

    val context = LocalContext.current

    val timeLabel = remember(point, period) {
        val epoch = point?.x ?: return@remember null
        val instant = Instant.fromEpochMilliseconds(epoch)
        val now = Clock.System.now()
        val isCurrentYear = instant.toLocalDateTime(TimeZone.currentSystemDefault()).year ==
                now.toLocalDateTime(TimeZone.currentSystemDefault()).year

        val is24Hour = DateFormat.is24HourFormat(context)

        fun Instant.formatLocalized(if12Hour: String, if24Hour: String = if12Hour): String {
            return if (is24Hour) {
                format(if24Hour, Locale.US)
            } else {
                format(if12Hour, Locale.US)
            }
        }

        when (period) {
            Period.All -> instant.formatLocalized("MMMM d, yyyy")
            Period.Day -> instant.formatLocalized("hh:mm a", "hh:mm")
            Period.Week -> {
                if (isCurrentYear) {
                    instant.formatLocalized("MMMM d, hh:mm a", "MMMM d, hh:mm")
                } else {
                    instant.formatLocalized("MMMM d, yyyy, hh:mm a", "MMMM d, yyyy, hh:mm")
                }
            }

            Period.Month -> {
                if (isCurrentYear) {
                    instant.formatLocalized("MMMM d")
                } else {
                    instant.formatLocalized("MMMM d, yyyy")
                }
            }

            Period.Year -> {
                if (isCurrentYear) {
                    instant.formatLocalized("MMMM d")
                } else {
                    instant.formatLocalized("MMMM d, yyyy")
                }
            }
        }
    }

    if (timeLabel != null) {
        Text(
            modifier = Modifier
                .alpha(alpha),
            text = timeLabel,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}