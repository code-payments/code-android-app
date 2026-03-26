package com.flipcash.app.discovery.internal.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.discovery.internal.TokenDiscoveryViewModel
import com.flipcash.features.discovery.R
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.utils.sheetResignmentBehavior

@Composable
internal fun TokenLeaderboard(
    category: DiscoverCategory?,
    tokens: Loadable<List<Token>>,
    showGradientAtEnd: Boolean,
    padding: PaddingValues,
    state: LazyListState,
    dispatch: (TokenDiscoveryViewModel.Event) -> Unit
) {
    val reduceBottomPadding = if (showGradientAtEnd) 0.dp else CodeTheme.dimens.grid.x4
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollStateGradient(
                state,
                color = CodeTheme.colors.background,
                isLongGradient = true,
                showAtEnd = showGradientAtEnd,
            )
            .addIf(tokens.isLoaded()) {
                Modifier.sheetResignmentBehavior(state)
            },
        state = state,
        contentPadding = PaddingValues(
            start = CodeTheme.dimens.inset,
            end = CodeTheme.dimens.inset,
            top = CodeTheme.dimens.grid.x2 + padding.calculateTopPadding(),
            bottom = (CodeTheme.dimens.grid.x2 + padding.calculateBottomPadding() - reduceBottomPadding).coerceAtLeast(0.dp)
        )
    ) {
        when (tokens) {
            is Loadable.Error -> {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.title_discoverFailedToLoad),
                                style = CodeTheme.typography.textLarge,
                                color = CodeTheme.colors.textMain,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = tokens.message.orEmpty(),
                                style = CodeTheme.typography.textSmall,
                                color = CodeTheme.colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )

                            CodeButton(
                                onClick = {
                                    dispatch(TokenDiscoveryViewModel.Event.Refresh)
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = CodeTheme.dimens.grid.x2),
                                contentPadding = PaddingValues(),
                                text = stringResource(R.string.action_retry),
                                shape = CircleShape,
                                buttonState = ButtonState.Filled
                            )
                        }
                    }
                }
            }

            is Loadable.Loaded -> {
                val results = tokens.data
                if (results.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when (category) {
                                        DiscoverCategory.Popular -> stringResource(R.string.title_discoverEmptyPopular)
                                        DiscoverCategory.New -> stringResource(R.string.title_discoverEmptyNew)
                                        else -> ""
                                    },
                                    style = CodeTheme.typography.textLarge,
                                    color = CodeTheme.colors.textMain,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    modifier = Modifier.fillMaxWidth(0.6f),
                                    text = when (category) {
                                        DiscoverCategory.Popular -> stringResource(R.string.subtitle_discoverEmptyPopular)
                                        DiscoverCategory.New -> stringResource(R.string.subtitle_discoverEmptyNew)
                                        else -> ""
                                    },
                                    textAlign = TextAlign.Center,
                                    style = CodeTheme.typography.textSmall,
                                    color = CodeTheme.colors.textSecondary,
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = tokens.data,
                        key = { _, t -> t.address.base58() },
                        contentType = { _, t -> "token row" }
                    ) { index, token ->
                        RankedTokenMetricsRow(
                            modifier = Modifier.padding(
                                vertical = CodeTheme.dimens.grid.x3,
                            ),
                            rank = index + 1,
                            token = token,
                        ) {
                            dispatch(TokenDiscoveryViewModel.Event.OpenTokenInfo(token.address))
                        }

                        if (index < tokens.data.lastIndex) {
                            HorizontalDivider(
                                color = CodeTheme.colors.dividerVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                            )

                        }
                    }
                }
            }

            is Loadable.Loading -> {
                items(12) { index ->
                    SkeletonRankedTokenMetricsRow(
                        rank = index + 1,
                        modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3)
                    )
                    if (index < 7) {
                        HorizontalDivider(
                            color = CodeTheme.colors.dividerVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}