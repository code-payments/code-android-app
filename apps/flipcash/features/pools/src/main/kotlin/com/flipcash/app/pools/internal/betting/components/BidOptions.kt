package com.flipcash.app.pools.internal.betting.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.label
import com.flipcash.app.core.pools.winningOutcome
import com.flipcash.app.core.ui.FlagWithFiat
import com.flipcash.features.pools.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable

@Composable
internal fun BidOptions(
    canBid: Boolean,
    buyIn: Fiat,
    outcomes: List<PoolBetOutcome>,
    totals: Map<PoolBetOutcome, Int>,
    selectedOutcome: PoolBetOutcome?,
    isLoaded: Boolean,
    resolution: PoolResolution,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = CodeTheme.dimens.inset),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    onOutcomeSelected: (PoolBetOutcome) -> Unit,
) {
    val possibleOutcomes by rememberUpdatedState(outcomes)

    val textSmall = CodeTheme.typography.textSmall
    val measurer = rememberTextMeasurer()
    val youSaid = stringResource(R.string.subtitle_youSaid)
    val result = remember {
        measurer.measure(
            text = youSaid,
            style = textSmall,
        )
    }

    Box {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(contentPadding)
                .then(modifier),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            possibleOutcomes.fastForEach { item ->
                val isSelected = remember(
                    item,
                    selectedOutcome,
                    isLoaded,
                ) { item.key == selectedOutcome?.key }

                // State to track if the composable has been initialized
                var isInitialized by remember { mutableStateOf(false) }

                // State to control animation trigger
                var triggerAnimation by remember { mutableStateOf(false) }

                var showContent by remember { mutableStateOf(false) }

                // Detect selection change after data is loaded
                LaunchedEffect(isSelected) {
                    if (isInitialized) {
                        // Selection changed from false to true after data is loaded
                        triggerAnimation = true
                    }

                    showContent = isSelected
                }

                // Set isInitialized to true after first composition
                LaunchedEffect(isLoaded) {
                    if (isLoaded) {
                        isInitialized = true
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ClickableCell(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        enabled = canBid,
                        isSelected = isSelected,
                        item = item,
                        resolution = resolution,
                        totals = totals
                    ) { onOutcomeSelected(item) }

                    AnimatedContent(
                        targetState = showContent,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        contentKey = { (item.key + it).hashCode() },
                        transitionSpec = {
                            if (triggerAnimation && targetState) {
                                slideInVertically(
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    )
                                ) + fadeIn() togetherWith fadeOut() + slideOutVertically()
                            } else {
                                // No animation for initial load, data change, or deselection
                                ContentTransform(
                                    targetContentEnter = EnterTransition.None,
                                    initialContentExit = ExitTransition.None
                                )
                            }
                        },
                    ) { show ->
                        if (show) {
                            val shape = CodeTheme.shapes.medium.copy(
                                topStart = ZeroCornerSize,
                                topEnd = ZeroCornerSize,
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        compositingStrategy =
                                            CompositingStrategy.Offscreen
                                    }
                            ) {
                                Text(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .border(
                                            width = CodeTheme.dimens.border,
                                            color = CodeTheme.colors.border,
                                            shape = shape
                                        )
                                        .background(
                                            color = CodeTheme.colors.surfaceVariant,
                                            shape = shape,
                                        )
                                        .padding(
                                            horizontal = CodeTheme.dimens.grid.x2,
                                            vertical = CodeTheme.dimens.grid.x1,
                                        ),
                                    text = youSaid,
                                    style = CodeTheme.typography.textSmall,
                                    color = CodeTheme.colors.textSecondary,
                                )
                            }
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(result.size.height.dp + CodeTheme.dimens.grid.x2)
                            )
                        }
                    }
                }
            }
        }

        if (canBid && selectedOutcome == null) {
            val buyInAmount = buyIn.formatted(
                formatting = Fiat.Formatting.Truncated
            )

            val label = stringResource(R.string.subtitle_poolNotYetBet, buyInAmount)
            val buyInIndex = label.indexOf(buyInAmount)
            Text(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(top = CodeTheme.dimens.grid.x6),
                text = buildAnnotatedString {
                    append(label)
                    addStyle(
                        style = SpanStyle(
                            color = CodeTheme.colors.textMain,
                        ), start = buyInIndex, end = buyInIndex + buyInAmount.length
                    )
                },
                textAlign = TextAlign.Center,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ClickableCell(
    enabled: Boolean,
    isSelected: Boolean,
    resolution: PoolResolution,
    item: PoolBetOutcome,
    totals: Map<PoolBetOutcome, Int>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        when (resolution) {
            is PoolResolution.BooleanResolution -> {
                val isWon = resolution.winningOutcome == item
                if (isWon) CodeTheme.colors.success.copy(0.69f) else Color(0xFF193024)
            }
            PoolResolution.NotSet -> if (isSelected) Color.White else Color(0xFF071F10)
            PoolResolution.Refund -> Color(0xFF071F10)
        }
    )

    val borderColor by animateColorAsState(
        when (resolution) {
            is PoolResolution.BooleanResolution -> {
                val isWon = resolution.winningOutcome == item
                if (isWon) Color.Transparent else CodeTheme.colors.border
            }
            PoolResolution.NotSet -> if (isSelected) Color.White else CodeTheme.colors.border
            PoolResolution.Refund -> CodeTheme.colors.border
        }
    )

    val choiceOptionColor by animateColorAsState(
        when (resolution) {
            is PoolResolution.BooleanResolution -> Color.White
            PoolResolution.NotSet -> if (isSelected) Color.Black else CodeTheme.colors.textSecondary
            PoolResolution.Refund -> CodeTheme.colors.textSecondary
        }
    )

    val choiceBetTotalColor by animateColorAsState(
        when (resolution) {
            is PoolResolution.BooleanResolution -> {
                val isWon = resolution.winningOutcome == item
                if (isWon) Color.White else CodeTheme.colors.textSecondary
            }
            PoolResolution.NotSet -> if (isSelected) Color.Black else Color.White.copy(0.3f)
            PoolResolution.Refund -> CodeTheme.colors.textSecondary
        }
    )
    Box(
        modifier = modifier
            .rememberedClickable(
                enabled = enabled,
                onClick = onClick
            )
            .border(
                width = CodeTheme.dimens.border,
                color = borderColor,
                shape = CodeTheme.shapes.medium
            )
            .background(backgroundColor, CodeTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.matchParentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.label,
                style = CodeTheme.typography.displayMedium,
                color = choiceOptionColor,
            )

            Text(
                text = pluralStringResource(
                    R.plurals.subtitle_personCount,
                    totals[item] ?: 0,
                    totals[item] ?: 0
                ),
                style = CodeTheme.typography.textSmall,
                color = choiceBetTotalColor,
            )
        }
    }
}