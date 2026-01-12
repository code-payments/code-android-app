package com.flipcash.app.tokens.internal.components.marketcap

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.app.tokens.data.MarketCapPoint
import com.flipcash.app.tokens.data.MarketTrend
import com.flipcash.app.tokens.data.Period
import com.flipcash.app.tokens.data.collapse
import com.flipcash.features.tokens.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.charts.ChartPoint
import com.getcode.ui.components.charts.LineTrend
import com.getcode.ui.components.charts.TrendType
import com.getcode.ui.components.charts.yValues
import com.getcode.util.vibration.LocalVibrator
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration

@Composable
internal fun MarketCapChart(
    data: List<MarketCapPoint>,
    trendType: TrendType,
    selectedPeriod: Period,
    modifier: Modifier = Modifier,
    chartPadding: PaddingValues = PaddingValues(),
    periodPadding: PaddingValues = PaddingValues(),
    onPointHighlighted: (MarketCapPoint?) -> Unit,
    onPeriodSelected: (Period) -> Unit,
) {
    var historicalData by remember {
        mutableStateOf<List<MarketCapPoint>>(emptyList())
    }

    LaunchedEffect(data) {
        if (data.isNotEmpty()) {
            historicalData = data
        }
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    val windowedData by remember(historicalData, selectedPeriod) {
        derivedStateOf {
            historicalData.collapse(selectedPeriod)
        }
    }

    // Update the model when the window changes
    LaunchedEffect(windowedData) {
        if (windowedData.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        x = windowedData.indices.map { it.toDouble() },
                        y = windowedData.map { it.y },
                    )
                }
            }
        }
    }

    val trend = remember(windowedData) {
        trendType.determineTrend(windowedData.yValues)
    }

    MarketCapChart(
        modelProducer = modelProducer,
        trend = trend,
        selectedPeriod = selectedPeriod,
        modifier = modifier,
        chartPadding = chartPadding,
        periodPadding = periodPadding,
        onPeriodSelected = onPeriodSelected,
        onPointHighlighted = { target ->
            val datum = windowedData.getOrNull(target?.x?.toInt() ?: -1)
            onPointHighlighted(datum)
        }
    )
}

@Composable
private fun MarketCapChart(
    modelProducer: CartesianChartModelProducer,
    trend: LineTrend,
    selectedPeriod: Period,
    modifier: Modifier = Modifier,
    chartPadding: PaddingValues = PaddingValues(),
    periodPadding: PaddingValues = PaddingValues(),
    onPointHighlighted: (CartesianMarker.Target?) -> Unit,
    onPeriodSelected: (Period) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6)
    ) {
        MarketCapChartContent(
            producer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(chartPadding)
                .weight(1f),
            trend = trend,
            onPointHighlighted = onPointHighlighted,
        )

        TabRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(periodPadding),
            selectedTabIndex = Period.entries.indexOf(selectedPeriod),
            backgroundColor = CodeTheme.colors.background,
            indicator = {},
            divider = {},
        ) {
            Period.entries.fastForEach { period ->
                val isSelected = remember(period, selectedPeriod) {
                    period == selectedPeriod
                }
                val textColor by animateColorAsState(
                    if (isSelected) CodeTheme.colors.textMain else CodeTheme.colors.textSecondary
                )
                val backgroundColor by animateColorAsState(
                    if (isSelected) CodeTheme.colors.trackColor else Color.Transparent
                )

                Text(
                    modifier = Modifier
                        .background(
                            color = backgroundColor,
                            shape = CodeTheme.shapes.extraSmall
                        )
                        .clip(CodeTheme.shapes.extraSmall)
                        .clickable { onPeriodSelected(period) }
                        .padding(
                            vertical = CodeTheme.dimens.grid.x2
                        ),
                    text = when (period) {
                        Period.Day -> stringResource(R.string.label_marketCapDayShort)
                        Period.Week -> stringResource(R.string.label_marketCapWeekShort)
                        Period.Month -> stringResource(R.string.label_marketCapMonthShort)
                        Period.Year -> stringResource(R.string.label_marketCapYearShort)
                        Period.All -> stringResource(R.string.label_marketCapAllTimeShort)
                    },
                    color = textColor,
                    style = CodeTheme.typography.textSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MarketCapChartContent(
    producer: CartesianChartModelProducer,
    trend: LineTrend,
    modifier: Modifier = Modifier,
    onPointHighlighted: (CartesianMarker.Target?) -> Unit
) {
    val trendColor = trend.color
    val trendAlpha = if (trend is LineTrend.Up) 0.10f else 0.25f

    var markerFraction by remember { mutableFloatStateOf(1f) }
    val vibrator = LocalVibrator.current

    var isDragging by remember { mutableStateOf(false) }
    val markerVisibilityListener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(
                marker: CartesianMarker,
                targets: List<CartesianMarker.Target>
            ) {
                val x = targets.firstOrNull()?.x ?: return
                // tick on scrubbing start
                vibrator.tick()
                isDragging = true

                markerFraction = (x.toFloat() / 100f).coerceIn(0f, 1f)
                onPointHighlighted(targets.firstOrNull())
            }

            override fun onHidden(marker: CartesianMarker) {
                markerFraction = 1f
                isDragging = false
                onPointHighlighted(null)
            }

            override fun onUpdated(
                marker: CartesianMarker,
                targets: List<CartesianMarker.Target>
            ) {
                val x = targets.firstOrNull()?.x ?: return
                markerFraction = (x.toFloat() / 100f).coerceIn(0f, 1f)
                onPointHighlighted(targets.firstOrNull())
            }
        }
    }

    val lineFill = rememberSplitLineFill(
        leftColor = trendColor,
        rightColor = trend.pressedColor,
        splitFraction = markerFraction + 0.005f,
    )

    val activeMarker = rememberChartMarker(strokeFill = trendColor)
    val persistentMarker = rememberChartMarker()

    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = lineFill,
                    areaFill = remember(trend) {
                        LineCartesianLayer.AreaFill.single(
                            fill(
                                ShaderProvider.verticalGradient(
                                    trendColor.copy(alpha = trendAlpha).toArgb(),
                                    trendColor.copy(alpha = 0f).toArgb(),
                                )
                            )
                        )
                    },
                    pointConnector = LineCartesianLayer.PointConnector.cubic(1f),
                ),
            ),
        ),
        persistentMarkers = remember(isDragging, persistentMarker, activeMarker) {
            if (isDragging) {
                { persistentMarker at 99.0 }
            } else {
                { activeMarker at 99.0 }
            }
        },
        markerVisibilityListener = markerVisibilityListener,
        markerController = CartesianMarkerController.rememberShowOnLongPress(),
        marker = activeMarker,
        startAxis = null,
        bottomAxis = null,
    )

    CartesianChartHost(
        modifier = modifier,
        chart = chart,
        modelProducer = producer,
        animationSpec = tween(durationMillis = 300),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

@Composable
private fun rememberChartMarker(
    strokeFill: Color? = null,
): CartesianMarker {
    val markerFill = CodeTheme.colors.background
    val outerFill: Color = strokeFill?.copy(0.20f) ?: Color.Transparent

    return rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            color = Color.Transparent,
        ),
        valueFormatter = remember {
            DefaultCartesianMarker.ValueFormatter.default(colorCode = false)
        },
        indicator = remember(strokeFill) {
            { color ->
                outerFillShapeComponent(
                    outerFill = fill(outerFill),
                    margins = Insets(6f),
                    innerFill = fill(markerFill),
                    strokeFill = fill(strokeFill ?: color),
                    strokeThickness = 2.dp,
                )
            }
        },
        indicatorSize = CodeTheme.dimens.grid.x3,
        guideline = null,
    )
}

@Composable
private fun rememberSplitLineFill(
    leftColor: Color,
    rightColor: Color,
    splitFraction: Float, // 0-1, where the split occurs
): LineCartesianLayer.LineFill {
    return remember(leftColor, rightColor, splitFraction) {
        LineCartesianLayer.LineFill.single(
            fill(
                ShaderProvider.horizontalGradient(
                    intArrayOf(
                        leftColor.toArgb(),
                        leftColor.toArgb(),
                        rightColor.toArgb(),
                        rightColor.toArgb(),
                    ),
                    positions = floatArrayOf(0f, splitFraction, splitFraction, 1f)
                )
            )
        )
    }
}

@Composable
@Preview
private fun PreviewMarketCapChart() {
    @Composable
    fun SampleChart(
        trend: MarketTrend
    ) {
        var selectedPeriod by remember {
            mutableStateOf(Period.All)
        }

        val data = remember(selectedPeriod) {
            generateMarketCapData(
                trend = trend,
                duration = Duration.INFINITE
            )
        }
        val trendType = TrendType.LinearRegression
        val trend = remember(data) {
            trendType.determineTrend(data.yValues)
        }

        var highlightedPoint by remember {
            mutableStateOf<ChartPoint<Long, Long>?>(null)
        }

        val modelProducer = remember { CartesianChartModelProducer() }
        // Use `runBlocking` only for previews, which don’t support asynchronous execution.
        runBlocking {
            modelProducer.runTransaction {
                lineSeries { series(data.yValues) }
            }
        }

        MarketCapChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(bottom = CodeTheme.dimens.grid.x4),
            trend = trend,
            modelProducer = modelProducer,
            selectedPeriod = selectedPeriod,
            onPointHighlighted = { target ->
                val datum = data.find { it.x.toDouble() == target?.x }
                highlightedPoint = datum
            },
            onPeriodSelected = { selectedPeriod = it }
        )
    }
    FlipcashDesignSystem {
        Column(
            modifier = Modifier.background(CodeTheme.colors.background),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x8)
        ) {
            SampleChart(trend = MarketTrend.Bullish)
//            SampleChart(trend = MarketTrend.Bearish)
        }
    }
}