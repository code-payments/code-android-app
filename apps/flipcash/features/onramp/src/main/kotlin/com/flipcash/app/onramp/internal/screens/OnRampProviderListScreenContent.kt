package com.flipcash.app.onramp.internal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.onramp.LocalOnRampAmountController
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.app.onramp.internal.data.OnRampProviderDestination
import com.flipcash.app.onramp.internal.data.OnRampProviderItem
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.onramp.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.core.rememberedClickable

@Composable
internal fun OnRampProviderListScreen(
    viewModel: OnRampViewModel,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    OnRampProviderListScreenContent(
        state = state,
        dispatchEvent = viewModel::dispatchEvent,
    )
}

@Composable
private fun OnRampProviderListScreenContent(
    state: OnRampViewModel.State,
    dispatchEvent: (OnRampViewModel.Event) -> Unit,
) {
    val onramp = LocalOnRampAmountController.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
    ) {
        item {
            Column(
                modifier = Modifier.fillParentMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x5),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(CodeTheme.dimens.grid.x4))
                Image(
                    painter = painterResource(id = R.drawable.ic_usdc_on_solana),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x3),
                    text = stringResource(R.string.subtitle_addCashToWallet),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
        items(state.providers) { provider ->
            OnRampProviderCell(
                provider = provider,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onramp.requestAmountSelection(provider.provider as OnRampProvider) },
            )
        }
    }
}

@Composable
private fun OnRampProviderCell(
    provider: OnRampProviderItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .background(
                color = CodeTheme.colors.surfaceVariant,
                shape = CodeTheme.shapes.extraSmall,
            )
            .rememberedClickable(onClick = onClick)
            .padding(
                horizontal = CodeTheme.dimens.grid.x4,
                vertical = CodeTheme.dimens.grid.x5,
            ),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            painter = provider.icon,
            contentDescription = null,
        )

        Text(
            modifier = Modifier.weight(1f),
            text = provider.title,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )

        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.secondary,
        )
    }
}

@Composable
@Preview
private fun ProviderCellPreview() {
    FlipcashPreview(showBackground = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CodeTheme.dimens.inset),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
        ) {
            OnRampProvider.types.fastForEach {
                OnRampProviderCell(
                    provider = OnRampProviderItem(
                        provider = it,
                        destination = OnRampProviderDestination.Screen(AppRoute.OnRamp.AmountEntry(Mint.usdf)),
                    ),
                ) { }
            }
        }
    }
}