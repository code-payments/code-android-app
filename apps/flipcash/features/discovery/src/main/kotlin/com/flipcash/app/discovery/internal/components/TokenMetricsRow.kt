package com.flipcash.app.discovery.internal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.TokenIcon
import com.flipcash.app.core.ui.rememberShimmerAlpha
import com.flipcash.app.core.ui.shimmer
import com.flipcash.app.core.util.abbreviated
import com.flipcash.features.discovery.R
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.charts.LineTrend
import com.getcode.ui.core.addIf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding


@Composable
internal fun RankedTokenMetricsRow(
    rank: Int,
    token: Token,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("leaderboard_token_row")
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        TokenMetricsRow(token = token, onClick = null)
    }
}

@Composable
internal fun TokenMetricsRow(
    token: Token,
    modifier: Modifier = Modifier,
    window: WindowedRange = WindowedRange.LastWeek,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .addIf(onClick != null) {
                Modifier.clickable(onClick = { onClick?.invoke() })
            },
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenIcon(
            modifier = Modifier.size(CodeTheme.dimens.grid.x9),
            token = token
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = token.name,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignByBaseline(),
                    color = CodeTheme.colors.textMain,
                    style = CodeTheme.typography.screenTitle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = pluralStringResource(
                        R.plurals.subtitle_personCount,
                        token.holderMetrics.currentHolders.toInt(),
                        token.holderMetrics.currentHolders.abbreviated()
                    ),
                    color = CodeTheme.colors.textMain,
                    style = CodeTheme.typography.textMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = CodeTheme.dimens.grid.x1),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = token.marketCap()?.formatted().orEmpty(),
                    color = CodeTheme.colors.textSecondary,
                    style = CodeTheme.typography.caption,
                )

                val metricsDelta =
                    remember(token.holderMetrics) { token.holderMetrics.deltaForWindow(window) }
                val change = if (metricsDelta >= 0) LineTrend.Up else LineTrend.Down
                val deltaForWindow = buildString {
                    when (change) {
                        LineTrend.Down -> Unit // negative carried over from abbreviated
                        LineTrend.Up -> append("+")
                    }
                    append(metricsDelta.abbreviated())
                    append(" ")
                    append(
                        when (window) {
                            WindowedRange.AllTime -> stringResource(R.string.label_marketCapAllTime)
                            WindowedRange.LastDay -> stringResource(R.string.label_marketCapDay)
                            WindowedRange.LastWeek -> stringResource(R.string.label_marketCapWeek)
                            WindowedRange.LastMonth -> stringResource(R.string.label_marketCapMonth)
                            WindowedRange.LastYear -> stringResource(R.string.label_marketCapYear)
                        }
                    )
                }
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = deltaForWindow,
                    color = when (change) {
                        LineTrend.Down -> CodeTheme.colors.textSecondary
                        LineTrend.Up -> change.color
                    },
                    style = CodeTheme.typography.caption,
                )
            }
        }
    }
}

@Composable
fun RankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
    color: Color = CodeTheme.colors.textMain
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = CodeTheme.typography.screenTitle
    val padding = CodeTheme.dimens.grid.x2
    // Measure the widest possible rank string once; all badges share this size.
    val badgeSize = remember(textMeasurer, density) {
        val measured = textMeasurer.measure("999", style)
        with(density) {
            val side = maxOf(measured.size.width, measured.size.height).toDp() + padding
            side
        }
    }

    Box(
        modifier = modifier
            .size(badgeSize),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SkeletonRankedTokenMetricsRow(rank: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val shimmerAlpha = rememberShimmerAlpha()
        RankBadge(rank, color = Color.White.copy(alpha = shimmerAlpha))
        Spacer(Modifier.width(2.dp))
        // Icon placeholder
        Box(Modifier
            .size(CodeTheme.dimens.grid.x9)
            .shimmer(CircleShape))
        Spacer(Modifier.width(CodeTheme.dimens.grid.x2))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Box(Modifier
                    .size(width = 80.dp, height = 14.dp)
                    .shimmer()) // name
                Box(Modifier
                    .size(width = 50.dp, height = 12.dp)
                    .shimmer()) // market cap
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(Modifier
                    .size(width = 60.dp, height = 14.dp)
                    .shimmer()) // holders
                Box(Modifier
                    .size(width = 40.dp, height = 12.dp)
                    .shimmer()) // delta
            }
        }
    }
}
