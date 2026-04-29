package com.flipcash.app.menu.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TileButton
import com.flipcash.app.menu.MenuList
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.menu.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.launch

@Composable
internal fun MenuScreenContent(viewModel: MenuScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val appUpdater = LocalAppUpdater.current
    val composeScope = rememberCoroutineScope()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                modifier = Modifier.fillMaxWidth(),
                isInModal = true,
                title = stringResource(R.string.title_settings),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = { AppBarDefaults.Close { navigator.hide() } },
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .noRippleClickable {
                            composeScope.launch { appUpdater.checkForUpdate() }
                        }
                        .navigationBarsPadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    text = stringResource(
                        R.string.subtitle_appVersionInfoFooter,
                        state.appVersionInfo.versionName,
                        state.appVersionInfo.versionCode,
                        state.releaseTrack,
                    ),
                    color = CodeTheme.colors.textSecondary,
                    style = CodeTheme.typography.textSmall.copy(
                        textAlign = TextAlign.Center
                    ),
                )
            }
        }
    ) { padding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            items = state.items,
            header = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.grid.x3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                ) {
                    TileButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.action_depositFunds),
                        icon = painterResource(R.drawable.ic_menu_deposit)
                    ) {
                        navigator.push(AppRoute.Sheets.TokenSelection(purpose = TokenPurpose.Deposit))
                    }

                    TileButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.action_withdraw),
                        icon = painterResource(R.drawable.ic_menu_withdraw)
                    ) {
                        navigator.push(AppRoute.Sheets.TokenSelection(TokenPurpose.Withdraw))
                    }
                }
            },
            contentPadding = PaddingValues(top = CodeTheme.dimens.grid.x6),
            onItemClick = {
                viewModel.dispatchEvent(it.action)
            }
        )
    }
}