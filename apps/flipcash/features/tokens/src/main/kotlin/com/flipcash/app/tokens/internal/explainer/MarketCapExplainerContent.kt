package com.flipcash.app.tokens.internal.explainer

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import com.getcode.ui.core.verticalScrollStateGradient
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.core.money.formattedAppreciation
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.tokens.bondingcurve.ExplainerChart
import com.flipcash.app.tokens.bondingcurve.ExplainerChartPoint
import com.getcode.opencode.model.financial.Fiat
import com.flipcash.app.tokens.bondingcurve.ExplainerTick
import com.flipcash.app.tokens.ui.MarketCapExplainerViewModel
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.bolded
import com.getcode.theme.White05
import com.getcode.ui.components.text.AnimatedNumberText
import java.math.BigDecimal

/** Everything the screen draws, already formatted; the composables below do no math. */
internal data class ExplainerUiModel(
    val tokenName: String,
    val position: Float,
    val ticks: List<ExplainerTick>,
    val tickLabels: Map<ExplainerTick, String>,
    val reserveText: String,
    val worthText: String,
    val chart: ExplainerChart,
    val scrubLabel: String,
    val ownership: List<OwnershipRow>,
)

internal enum class Tone { Neutral, Positive, Negative }

internal fun appreciationTone(appreciation: Fiat?): Tone = when {
    appreciation == null || !appreciation.valueNonZero() -> Tone.Neutral
    appreciation.toDouble() > 0 -> Tone.Positive
    appreciation.toDouble() < 0 -> Tone.Negative
    else -> Tone.Neutral
}

internal data class OwnershipRow(@StringRes val label: Int, val value: String, val tone: Tone = Tone.Neutral)

private const val Dash = "–"

@Composable
internal fun MarketCapExplainerContent(state: MarketCapExplainerViewModel.State) {
    val projection = state.projection
    val token = state.token
    if (projection == null || token == null) return

    // The thumb follows the finger while it is down (`dragged`), then springs back to Today on
    // release; the readout and chart read the same position, so they roll back with it.
    val today = projection.todayPosition.toFloat()
    val scope = rememberCoroutineScope()
    var dragged by remember { mutableStateOf<Float?>(null) }
    val settle = remember(today) { Animatable(today) }
    val position = (dragged ?: settle.value).coerceIn(0f, 1f)

    // Keyed on position: the lambda captures the plain Float, so without it the snapshot (and the
    // chart built from it) would freeze at its first value.
    val snapshot by remember(projection, position) {
        derivedStateOf { projection.snapshotAt(position.toDouble()) }
    }
    val chart by remember(projection, snapshot) { derivedStateOf { projection.chart(snapshot) } }
    val ownership = remember(projection) { projection.ownership() }

    // The curve stays in USD; only what is drawn is converted to the preferred currency.
    val currency = remember(state.rate) { ExplainerCurrency(state.rate) }
    val reserveText = currency.reserve(snapshot.reserve)
    val scrubLabel = if (snapshot.isToday) stringResource(R.string.label_marketCapScrubToday, reserveText) else reserveText
    val appreciation = state.appreciation
    val appreciationText = appreciation?.formattedAppreciation() ?: Dash
    val appreciationTone = appreciationTone(appreciation)

    val model = ExplainerUiModel(
        tokenName = token.name,
        position = position,
        ticks = projection.ticks,
        tickLabels = projection.ticks.associateWith { currency.reserve(it.reserve) },
        reserveText = reserveText,
        worthText = snapshot.worth?.let { currency.convert(it).formatted() } ?: Dash,
        chart = chart,
        scrubLabel = scrubLabel,
        ownership = listOf(
            OwnershipRow(
                R.string.label_underlyingTokensYouOwn,
                ownership.tokensHeld
                    ?.let { java.text.NumberFormat.getIntegerInstance().format(it.setScale(0, java.math.RoundingMode.DOWN)) }
                    ?: Dash,
            ),
            OwnershipRow(R.string.label_currentPricePerToken, currency.price(ownership.price)),
            OwnershipRow(
                R.string.label_shareOfCirculatingSupply,
                ownership.shareOfCirculating?.let(ExplainerFormat::percent) ?: Dash,
            ),
            OwnershipRow(R.string.label_shareOfMaxSupply, ownership.shareOfMax?.let(ExplainerFormat::percent) ?: Dash),
            OwnershipRow(R.string.label_yourCurrentAppreciation, appreciationText, appreciationTone),
        ),
    )
    MarketCapExplainerBody(
        model = model,
        onPositionChange = { dragged = it },
        onDragEnd = {
            val from = dragged ?: return@MarketCapExplainerBody
            scope.launch {
                settle.snapTo(from)
                dragged = null
                settle.animateTo(today, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        },
    )
}

@Composable
internal fun MarketCapExplainerBody(
    model: ExplainerUiModel,
    onPositionChange: (Float) -> Unit,
    onDragEnd: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val inset = CodeTheme.dimens.inset
    val grid = CodeTheme.dimens.grid
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollStateGradient(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = inset)
            .padding(top = grid.x3, bottom = grid.x8),
        verticalArrangement = Arrangement.spacedBy(grid.x4),
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.title_marketCapExplainerHeadline, model.tokenName),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )

        Card {
            Text(
                text = stringResource(R.string.label_marketCapAmountPurchased, model.tokenName),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
            AnimatedNumberText(
                modifier = Modifier.padding(top = grid.x1),
                value = model.reserveText,
                style = CodeTheme.typography.displayCompact,
                color = CodeTheme.colors.textMain,
            )
            MarketCapExplainerChart(
                modifier = Modifier.padding(top = grid.x3),
                chart = model.chart,
                scrubLabel = model.scrubLabel,
                description = stringResource(
                    R.string.label_marketCapChartAccessibility, model.tokenName, model.reserveText,
                ),
            )
            MarketCapExplainerSlider(
                modifier = Modifier.padding(top = grid.x2),
                position = model.position,
                ticks = model.ticks,
                tickLabel = { model.tickLabels.getValue(it) },
                onPositionChange = onPositionChange,
                onDragEnd = onDragEnd,
            )
            Text(
                modifier = Modifier.padding(top = grid.x5),
                text = stringResource(R.string.label_marketCapBalanceWouldBeWorth, model.tokenName),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
            AnimatedNumberText(
                modifier = Modifier.padding(top = grid.x1),
                value = model.worthText,
                style = CodeTheme.typography.displayMedium.bolded(),
                color = CodeTheme.colors.textMain,
            )
        }

        Card {
            Text(
                text = stringResource(R.string.title_yourOwnership),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            model.ownership.forEach { (label, value, tone) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = grid.x3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(label),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                    )
                    Text(
                        text = value,
                        style = CodeTheme.typography.textMedium,
                        color = when (tone) {
                            Tone.Positive -> CodeTheme.colors.successText
                            Tone.Negative -> CodeTheme.colors.errorText
                            Tone.Neutral -> CodeTheme.colors.textMain
                        },
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(White05, CodeTheme.shapes.medium)
            .padding(CodeTheme.dimens.grid.x4),
    ) { content() }
}

// Hand-built from the contract's vectors (supply 1.25M, 12,400 held); the real tables are not
// available to previews.
private fun previewModel(todaySelected: Boolean): ExplainerUiModel {
    val points = (0..80).map {
        val t = it * 25_000.0
        ExplainerChartPoint(t, 0.01 + 8.78 * Math.pow(t / 7_300_000.0, 2.2))
    }
    val ticks = listOf(
        ExplainerTick(BigDecimal(5_000), 0.0, false, true),
        ExplainerTick(BigDecimal(22_700), 0.26, true, true),
        ExplainerTick(BigDecimal(100_000), 0.54, false, true),
        ExplainerTick(BigDecimal(1_000_000), 0.81, false, true),
        ExplainerTick(BigDecimal(10_000_000), 1.0, false, true),
    )
    val tickLabels = ticks.associateWith { if (it.isToday) "Today" else "$" + it.reserve.toPlainString() }
    return if (todaySelected) ExplainerUiModel(
        tokenName = "Coin", position = 0.26f, ticks = ticks, tickLabels = tickLabels, reserveText = "$22.7K", worthText = "$369.18",
        chart = ExplainerChart(points, 2_000_000.0, 0.01, points.last().price, 1_250_000.0, 0.02994, 1_250_000.0, 0.02994),
        scrubLabel = "$22.7K Today",
        ownership = previewOwnership(),
    ) else ExplainerUiModel(
        tokenName = "Coin", position = 0.54f, ticks = ticks, tickLabels = tickLabels, reserveText = "$100K", worthText = "$1,205.13",
        chart = ExplainerChart(points, 3_500_000.0, 0.01, points.last().price, 2_750_000.0, 0.0977, 1_250_000.0, 0.02994),
        scrubLabel = "$100K",
        ownership = previewOwnership(),
    )
}

private fun previewOwnership() = listOf(
    OwnershipRow(R.string.label_underlyingTokensYouOwn, "12,400"),
    OwnershipRow(R.string.label_currentPricePerToken, "$0.02994"),
    OwnershipRow(R.string.label_shareOfCirculatingSupply, "0.99%"),
    OwnershipRow(R.string.label_shareOfMaxSupply, "0.06%"),
    OwnershipRow(R.string.label_yourCurrentAppreciation, "+$12.40", Tone.Positive),
)

@Preview(name = "Today", heightDp = 900)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MarketCapExplainer_Today() {
    MarketCapExplainerBody(previewModel(todaySelected = true), onPositionChange = {})
}

@Preview(name = "100K", heightDp = 900)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MarketCapExplainer_100K() {
    MarketCapExplainerBody(previewModel(todaySelected = false), onPositionChange = {})
}
