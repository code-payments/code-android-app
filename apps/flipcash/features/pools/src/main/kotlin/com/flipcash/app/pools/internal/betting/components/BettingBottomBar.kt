package com.flipcash.app.pools.internal.betting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.util.fastForEach
import com.flipcash.app.pools.internal.betting.PoolBettingViewModel
import com.flipcash.features.pools.R
import com.getcode.manager.BottomBarManager
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun BettingBottomBar(
    state: PoolBettingViewModel.State,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(
                bottom = CodeTheme.dimens.grid.x2
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        if (state.isLoaded && !state.isHost && !state.isResolved) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = CodeTheme.dimens.grid.x3,
                        horizontal = CodeTheme.dimens.inset
                    ),
                text = stringResource(R.string.subtitle_poolParticipantOutcomeDisclaimer),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        state.bottomBarActions.fastForEach {
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = it.text,
                buttonState = it.style.toButtonStyle(),
                onClick = it.onClick,
            )
        }
    }
}

private fun BottomBarManager.BottomBarButtonStyle.toButtonStyle() = when (this) {
    BottomBarManager.BottomBarButtonStyle.Filled -> ButtonState.Filled
    BottomBarManager.BottomBarButtonStyle.Filled50 -> ButtonState.Filled50
    BottomBarManager.BottomBarButtonStyle.Outlined -> ButtonState.Bordered
    BottomBarManager.BottomBarButtonStyle.Text -> ButtonState.Subtle
}