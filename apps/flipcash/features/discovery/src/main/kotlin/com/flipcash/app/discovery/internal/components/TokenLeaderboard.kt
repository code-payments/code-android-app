package com.flipcash.app.discovery.internal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.FabPosition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.discovery.internal.LeaderboardEntry
import com.flipcash.app.discovery.internal.TokenDiscoveryViewModel
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.tokens.ui.CurrencyCreatorUpsellCard
import com.flipcash.features.discovery.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.sheetResignmentBehavior
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
internal fun TokenLeaderboard(
    category: DiscoverCategory?,
    tokens: Loadable<List<LeaderboardEntry>>,
    state: LazyListState,
    dispatch: (TokenDiscoveryViewModel.Event) -> Unit
) {
    val reduceBottomPadding = CodeTheme.dimens.grid.x4
    val features = LocalFeatureFlags.current
    val isNewUi by features.observe(FeatureFlag.NewUi).collectAsStateWithLifecycle()

    // The v2 upsell card sits over the list as the bottom bar; frost it against the leaderboard
    // scrolling beneath. The inline (non-v2) card is part of the list itself, so it gets no hazeState.
    val hazeState = rememberHazeState()

    val currencyCreatorCard = @Composable { modifier: Modifier, haze: HazeState? ->
        CurrencyCreatorUpsellCard(modifier = modifier, hazeState = haze) {
            dispatch(TokenDiscoveryViewModel.Event.CreateCurrency)
        }
    }
    CodeScaffold(
        bottomBar = {
            if (isNewUi) {
                currencyCreatorCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .navigationBarsPadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    hazeState,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .testTag("discovery_leaderboard")
                .verticalScrollStateGradient(
                    state,
                    color = CodeTheme.colors.background,
                    isLongGradient = true,
                    showAtEnd = isNewUi,
                )
                .addIf(tokens.isLoaded()) {
                    Modifier.sheetResignmentBehavior(state)
                },
            state = state,
            contentPadding = PaddingValues(
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
                top = CodeTheme.dimens.grid.x2,
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
                    // currency creator upsell card
                    if (!isNewUi) {
                        item { currencyCreatorCard(Modifier.fillParentMaxWidth(), null) }
                    }

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
                        // leaderboard header
                        item {
                            Row(
                                modifier = Modifier.padding(
                                    top = CodeTheme.dimens.inset,
                                    bottom = CodeTheme.dimens.grid.x1,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                            ) {
                                Text(
                                    text = stringResource(R.string.title_leaderboard),
                                    style = CodeTheme.typography.textLarge,
                                    color = CodeTheme.colors.textMain,
                                )

                                Icon(
                                    modifier = Modifier
                                        .size(CodeTheme.dimens.staticGrid.x4)
                                        .unboundedClickable {
                                            dispatch(TokenDiscoveryViewModel.Event.LearnAboutLeaderboard)
                                        },
                                    imageVector = Icons.Outlined.Info,
                                    tint = CodeTheme.colors.textSecondary,
                                    contentDescription = stringResource(R.string.content_description_leaderboard),
                                )
                            }
                        }

                        itemsIndexed(
                            items = tokens.data,
                            key = { _, entry -> entry.key },
                            contentType = { _, _ -> "token row" }
                        ) { index, entry ->
                            RankedTokenMetricsRow(
                                modifier = Modifier.padding(
                                    vertical = CodeTheme.dimens.grid.x3,
                                ),
                                rank = index + 1,
                                token = entry.token,
                                rankingSystem = if (isNewUi) {
                                    RankingSystem.MarketCap
                                } else {
                                    RankingSystem.Holders
                                },
                            ) {
                                dispatch(TokenDiscoveryViewModel.Event.OpenTokenInfo(entry.token.address))
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
}