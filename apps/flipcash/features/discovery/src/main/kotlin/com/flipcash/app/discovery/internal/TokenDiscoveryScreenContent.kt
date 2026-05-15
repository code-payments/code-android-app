package com.flipcash.app.discovery.internal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.discovery.internal.components.TokenLeaderboard
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.discovery.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.measured
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.CodeSegmentedControl
import com.getcode.util.resources.LocalResources

@Composable
internal fun TokenDiscoveryScreen(viewModel: TokenDiscoveryViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenDiscoveryScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun TokenDiscoveryScreenContent(
    state: TokenDiscoveryViewModel.State,
    dispatch: (TokenDiscoveryViewModel.Event) -> Unit
) {
    val listState = rememberLazyListState()
    CodeScaffold { padding ->
        AnimatedContent(
            targetState = state.tokens,
            transitionSpec = { fadeIn(tween()) togetherWith fadeOut(tween()) },
            contentKey = { it::class }, // only crossfade on type change, not data updates
        ) { tokens ->
            TokenLeaderboard(
                category = state.category,
                state = listState,
                tokens = tokens,
                padding = padding,
                showGradientAtEnd = !state.createEnabled,
                dispatch = dispatch
            )
        }
    }
}


@Composable
@Preview(name = "Loading", group = "Token Discovery")
private fun Loading_Preview() {
    FlipcashPreview(showBackground = true) {
        TokenDiscoveryScreenContent(
            state = TokenDiscoveryViewModel.State(
                category = DiscoverCategory.Popular,
                tokens = Loadable.Loading()
            )
        ) { }
    }
}

@Composable
@Preview(name = "Loaded (Popular)", group = "Token Discovery")
private fun LoadedPopular_Preview() {
    FlipcashPreview(showBackground = true) {
        TokenDiscoveryScreenContent(
            state = TokenDiscoveryViewModel.State(
                category = DiscoverCategory.Popular,
                tokens = Loadable.Loaded(sampleTokens())
            )
        ) { }
    }
}

@Composable
@Preview(name = "Empty (Popular)", group = "Token Discovery")
private fun EmptyPopular_Preview() {
    FlipcashPreview(showBackground = true) {
        TokenDiscoveryScreenContent(
            state = TokenDiscoveryViewModel.State(
                category = DiscoverCategory.Popular,
                tokens = Loadable.Loaded(emptyList())
            )
        ) { }
    }
}

@Composable
@Preview(name = "Empty (New)", group = "Token Discovery")
private fun EmptyNew_Preview() {
    FlipcashPreview(showBackground = true) {
        TokenDiscoveryScreenContent(
            state = TokenDiscoveryViewModel.State(
                category = DiscoverCategory.New,
                tokens = Loadable.Loaded(emptyList())
            )
        ) { }
    }
}

@Composable
@Preview(name = "Error", group = "Token Discovery")
private fun Error_Preview() {
    FlipcashPreview(showBackground = true) {
        TokenDiscoveryScreenContent(
            state = TokenDiscoveryViewModel.State(
                category = DiscoverCategory.Popular,
                tokens = Loadable.Error(message = "Check your connection and try again")
            )
        ) { }
    }
}

private val dummyVmMetadata = VmMetadata(
    vm = PublicKey("11111111111111111111111111111111"),
    authority = PublicKey("11111111111111111111111111111111"),
    lockDurationInDays = 21,
)

private fun sampleToken(
    address: String,
    name: String,
    symbol: String,
    currentHolders: Long,
    weeklyDelta: Long,
): Token = MintMetadata(
    address = Mint(address),
    decimals = 6,
    name = name,
    symbol = symbol,
    createdAt = null,
    description = "",
    imageUrl = "",
    vmMetadata = dummyVmMetadata,
    launchpadMetadata = null,
    billCustomizations = null,
    socialLinks = emptyList(),
    holderMetrics = HolderMetrics(
        currentHolders = currentHolders,
        holderDeltas = listOf(
            HolderMetrics.HolderDelta(WindowedRange.LastWeek, weeklyDelta),
        ),
    ),
)

private fun sampleTokens(): List<Token> = listOf(
    sampleToken(
        "kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6",
        "Teddies",
        "TEDDIES",
        1_230_000,
        11_000
    ),
    sampleToken("54ggcQ23uen5b9QXMAns99MQNTKn7iyzq4wvCW6e8r25", "Jeffy", "JEFFY", 895_000, 44_000),
    sampleToken(
        "64dkhPKhdjc2xg3NLyDjC14wiXHLnGXHHUxJnqZVugJt",
        "\$BadBoys",
        "BADBOYS",
        588_000,
        4_000
    ),
    sampleToken(
        "A3e8dzb1y4gqGP2cnCS3UU8dm5YNrFpZBpjjdoZdtfnB",
        "TimberPuppies",
        "TIMBERPUPPIES",
        147_000,
        1_200
    ),
    sampleToken("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", "Ducat", "DUCAT", 88_174, 945),
    sampleToken(
        "311m6Sb1814PfAxkEcqq6MNdBiVZLr8VWuAWDSC72euW",
        "Cattle Cash",
        "CATTLE",
        44_759,
        108
    ),
    sampleToken("2psDP3LAvbNzfvBYNMs9ieMpsD8PVzyQsKNfZrjEKoDN", "bmoney", "BMONEY", 28_554, 46),
)