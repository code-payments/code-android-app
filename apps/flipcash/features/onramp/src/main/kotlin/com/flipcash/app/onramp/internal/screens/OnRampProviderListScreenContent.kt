package com.flipcash.app.onramp.internal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.app.onramp.internal.data.OnRampProviderItem
import com.flipcash.features.onramp.R
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3, Alignment.CenterVertically),
        contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
    ) {
        items(state.providers) { provider ->
            OnRampProviderCell(
                provider = provider,
                modifier = Modifier.fillMaxWidth(),
                onClick = { dispatchEvent(OnRampViewModel.Event.OnProviderSelected(provider)) },
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
                shape = CodeTheme.shapes.extraSmall
            )
            .rememberedClickable(onClick = onClick)
            .padding(CodeTheme.dimens.grid.x3,),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            painter = provider.icon,
            contentDescription = null,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Text(
                text = provider.title,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = provider.description,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.secondary,
        )
    }
}