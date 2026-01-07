package com.flipcash.app.tokens.internal.components.info

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.app.tokens.Period
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.charts.ChartPoint
import com.getcode.ui.components.charts.LineTrend
import com.getcode.ui.components.charts.TrendType
import com.getcode.ui.components.charts.yValues
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberShowOnPress
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shape.markerCorneredShape
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.component.Shadow
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlinx.coroutines.runBlocking
import java.util.Random
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

enum class MarketTrend {
    Bullish,
    Bearish,
    Sideways,
    Volatile,
    ;
}

@Composable
internal fun MarketCapChart(
    data: List<ChartPoint<Long, Long>>,
    trendType: TrendType,
    selectedPeriod: Period,
    modifier: Modifier = Modifier,
    chartPadding: PaddingValues = PaddingValues(),
    periodPadding: PaddingValues = PaddingValues(),
    onPointHighlighted: (ChartPoint<Long, Long>?) -> Unit,
    onPeriodSelected: (Period) -> Unit,
) {
    var seriesData by remember {
        mutableStateOf<List<ChartPoint<Long, Long>>>(emptyList())
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data, selectedPeriod) {
        if (data.isNotEmpty()) {
            seriesData = data
            modelProducer.runTransaction {
                lineSeries { series(data.yValues) }
            }
        }
    }

    val trend = remember(seriesData) {
        trendType.determineTrend(seriesData.yValues)
    }

    MarketCapChart(
        modelProducer = modelProducer,
        trend = trend,
        selectedPeriod = selectedPeriod,
        modifier = modifier,
        chartPadding = chartPadding,
        periodPadding = periodPadding,
        lastPoint = seriesData.lastOrNull(),
        onPeriodSelected = onPeriodSelected,
        onPointHighlighted = { target ->
            val datum = seriesData.getOrNull(target?.x?.toInt() ?: -1)
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
    lastPoint: ChartPoint<Long, Long>?,
    onPointHighlighted: (CartesianMarker.Target?) -> Unit,
    onPeriodSelected: (Period) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6)
    ) {
        val trendColor = trend.color
        val trendAlpha = if (trend is LineTrend.Up) 0.10f else 0.25f

        var isDragging by remember { mutableStateOf(false) }
        val markerVisibilityListener = remember {
            object : CartesianMarkerVisibilityListener {
                override fun onShown(
                    marker: CartesianMarker,
                    targets: List<CartesianMarker.Target>
                ) {
                    isDragging = true
                    onPointHighlighted(targets.firstOrNull())
                }

                override fun onHidden(marker: CartesianMarker) {
                    isDragging = false
                    onPointHighlighted(null)
                }

                override fun onUpdated(
                    marker: CartesianMarker,
                    targets: List<CartesianMarker.Target>
                ) {
                    onPointHighlighted(targets.firstOrNull())
                }
            }
        }

        val markerFill = CodeTheme.colors.background

        val marker = rememberDefaultCartesianMarker(
            label = rememberTextComponent(
                color = Color.Transparent,
            ),
            valueFormatter = remember {
                DefaultCartesianMarker.ValueFormatter.default(colorCode = false)
            },
            indicator = { color ->
                shapeComponent(
                    fill = fill(markerFill),
                    strokeFill = fill(color),
                    strokeThickness = 2.dp,
                    margins = Insets(4f),
                    shape = markerCorneredShape(CorneredShape.Corner.Rounded),
                    shadow = Shadow(
                        radiusDp = 20f,
                        color = color.copy(0.20f).toArgb()
                    )
                )
            },
            indicatorSize = CodeTheme.dimens.grid.x4,
            guideline = null,
        )

        val chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = remember(trend) { LineCartesianLayer.LineFill.single(fill(trendColor)) },
                        stroke = LineCartesianLayer.LineStroke.continuous(thickness = CodeTheme.dimens.thickBorder * 1.5f),
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
                        pointConnector = LineCartesianLayer.PointConnector.cubic(1f)
                    )
                ),
            ),
            persistentMarkers = {
                emptyMap<Long, CartesianMarker>()
            },
            markerVisibilityListener = markerVisibilityListener,
            markerController = CartesianMarkerController.rememberShowOnPress(consumeMoveEvents = true),
            marker = marker,
            startAxis = null,
            bottomAxis = null,
        )

        CartesianChartHost(
            modifier = Modifier
                .fillMaxWidth()
                .padding(chartPadding)
                .weight(1f),
            chart = chart,
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false)
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

internal fun generateMarketCapData(
    period: Period,
    currentMarketCap: Fiat,
    trend: MarketTrend = MarketTrend.Bullish
): List<ChartPoint<Long, Long>> {
    return generateMarketCapData(
        points = pointsPerPeriod(period),
        trend = trend,
        endValue = currentMarketCap.quarks,
        duration = when (period) {
            Period.All -> Duration.INFINITE
            Period.Day -> 1.days
            Period.Week -> 7.days
            Period.Month -> 30.days
            Period.Year -> 365.days
        }
    )
}

internal fun generateMarketCapData(
    points: Int = 100,
    endValue: Long = 1_000_000L,
    trend: MarketTrend = MarketTrend.Bullish,
    duration: Duration = 30.days,
): List<ChartPoint<Long, Long>> {
    val random = Random(System.currentTimeMillis())

    val isAllTime = duration == Duration.INFINITE

    val changePercent = when {
        isAllTime -> 0.95..0.99
        duration <= 1.days -> 0.02..0.08
        duration <= 7.days -> 0.05..0.20
        duration <= 30.days -> 0.15..0.50
        else -> 0.30..1.0
    }.let { range ->
        range.start + random.nextDouble() * (range.endInclusive - range.start)
    }

    val (startValue, baseVolatility) = when {
        isAllTime -> endValue * 0.00 to 0.05
        else -> when (trend) {
            MarketTrend.Bullish -> endValue / (1 + changePercent) to 0.33
            MarketTrend.Bearish -> endValue * (1 + changePercent) to 0.16
            MarketTrend.Sideways -> endValue * (1 - changePercent * 0.1) to 0.04
            MarketTrend.Volatile -> endValue / (1 + changePercent * 0.5) to 0.7
        }
    }

    var momentum = 0.0

    val endTime = Clock.System.now().toEpochMilliseconds()
    val effectiveDuration = if (isAllTime) 365.days else duration
    val startTime = endTime - effectiveDuration.inWholeMilliseconds
    val timeStep = effectiveDuration.inWholeMilliseconds / (points - 1).coerceAtLeast(1)

    return List(points) { i ->
        val progress = i.toDouble() / (points - 1).coerceAtLeast(1)

        // Last point is always exactly endValue
        if (i == points - 1) {
            return@List ChartPoint(
                x = startTime + (timeStep * i),
                y = endValue
            )
        }

        val easedProgress = when {
            isAllTime -> 1 - (1 - progress).pow(3.0)
            else -> when (trend) {
                MarketTrend.Bullish -> 1 - (1 - progress).pow(2.5)
                MarketTrend.Bearish -> progress.pow(2.5)
                MarketTrend.Sideways -> progress
                MarketTrend.Volatile -> progress
            }
        }

        val target = startValue + (endValue - startValue) * easedProgress
        val noiseScale = target.coerceAtLeast(1.0)
        val noise = random.nextGaussian() * noiseScale * baseVolatility
        momentum = 0.7 * momentum + 0.3 * noise

        val value = (target + momentum).coerceAtLeast(0.0).roundToLong()

        ChartPoint(
            x = startTime + (timeStep * i),
            y = value
        )
    }
}

private fun pointsPerPeriod(period: Period): Int {
    val pointsPerHour = 4
    return when (period) {
        Period.All -> 100
        Period.Day -> 1.hours.inWholeHours.toInt() * pointsPerHour
        Period.Week -> 7
        Period.Month -> 30
        Period.Year -> 365
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
                points = pointsPerPeriod(selectedPeriod),
                trend = trend,
                duration = when (selectedPeriod) {
                    Period.All -> 100.days
                    Period.Day -> 1.days
                    Period.Week -> 7.days
                    Period.Month -> 30.days
                    Period.Year -> 365.days
                }
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
                .padding(bottom = 16.dp),
            trend = trend,
            modelProducer = modelProducer,
            selectedPeriod = selectedPeriod,
            lastPoint = data.last(),
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