package com.flipcash.app.tokens.internal.components.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.tokens.Period
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.minus
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.charts.ChartPoint
import com.getcode.ui.components.charts.LineTrend
import com.getcode.ui.components.charts.TrendType
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.utils.calculateEndPadding
import com.getcode.ui.utils.calculateHorizontalPadding
import com.getcode.ui.utils.calculateStartPadding

@Composable
internal fun MarketCapSection(
    marketCap: Fiat,
    chartEnabled: Boolean,
    historicalData: List<ChartPoint<Long, Long>>,
    selectedPeriod: Period,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onPeriodSelected: (Period) -> Unit
) {
    var highlightedCapPoint by remember {
        mutableStateOf<ChartPoint<Long, Long>?>(null)
    }

    val marketCapAtPoint by remember(marketCap, highlightedCapPoint) {
        derivedStateOf {
            val point = highlightedCapPoint ?: return@derivedStateOf marketCap
            val capAtPoint = Fiat(point.y, marketCap.currencyCode)
            capAtPoint
        }
    }

    val marketCapDiff by remember(marketCap, historicalData) {
        derivedStateOf {
            val startCapQuarks = historicalData.firstOrNull()?.y ?: return@derivedStateOf null
            val startCap = Fiat(startCapQuarks, marketCap.currencyCode)
            marketCap - startCap
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
            style = CodeTheme.typography.displaySmall,
            color = CodeTheme.colors.textMain,
        )

        if (chartEnabled) {
            marketCapDiff?.let { change ->
                val isPositiveChange = change.decimalValue >= 0
                val changeColor by animateColorAsState(
                    if (isPositiveChange) LineTrend.Up.color else LineTrend.Down.color
                )

                val alpha by animateFloatAsState(
                    if (highlightedCapPoint == null) 1f else 0f
                )

                Text(
                    modifier = Modifier
                        .padding(start = contentPadding.calculateStartPadding())
                        .padding(top = CodeTheme.dimens.grid.x2)
                        .alpha(alpha)
                        .background(
                            color = changeColor.copy(0.20f),
                            shape = MaterialTheme.shapes.extraSmall,
                        ).padding(
                            vertical = 2.dp,
                            horizontal = CodeTheme.dimens.grid.x1
                        ),
                    text = change.formatted(
                        extraPrefix = if (change.decimalValue >= 0) "+" else "-",
                        suffix = when (selectedPeriod) {
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

                AnimatedVisibility(
                    visible = highlightedCapPoint == null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {

                }
            }

            MarketCapChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(240.dp),
                chartPadding = PaddingValues(end = contentPadding.calculateEndPadding()),
                periodPadding = PaddingValues(
                    horizontal = contentPadding.calculateHorizontalPadding().times(0.75f)
                ),
                data = historicalData,
                trendType = TrendType.FirstVsLast,
                selectedPeriod = selectedPeriod,
                onPointHighlighted = { highlightedCapPoint = it },
                onPeriodSelected = onPeriodSelected,
            )
        }
    }
}