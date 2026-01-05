package com.flipcash.app.menu.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.menu.MenuList
import com.flipcash.app.onramp.AddCashRow
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.menu.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.plus
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
                title = {
                    Image(
                        painter = painterResource(R.drawable.ic_flipcash_logo_w_name),
                        contentDescription = "",
                        modifier = Modifier
                            .rememberedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.dispatchEvent(MenuScreenViewModel.Event.OnLogoTapped)
                            }.requiredHeight(CodeTheme.dimens.staticGrid.x7)
                            .wrapContentWidth()
                            .padding(horizontal = CodeTheme.dimens.grid.x3),
                    )
                },
                titleAlignment = Alignment.CenterHorizontally,
                rightContents = { AppBarDefaults.Close { navigator.hide() } },
                contentPadding = AppBarDefaults.ContentPadding.plus(PaddingValues(top = CodeTheme.dimens.grid.x2))
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
                        state.appVersionInfo.versionCode
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
            header = if (state.showQuickActions) {
                {
                    AddCashRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = CodeTheme.dimens.inset,
                                vertical = CodeTheme.dimens.grid.x4,
                            ),
                        onAddCash = { viewModel.dispatchEvent(MenuScreenViewModel.Event.OnAddCashClicked) },
                        onWithdraw = { viewModel.dispatchEvent(MenuScreenViewModel.Event.OnWithdrawClicked) },
                    )
                }
            } else null,
            items = state.items,
            contentPadding = PaddingValues(top = CodeTheme.dimens.grid.x6),
            onItemClick = {
                viewModel.dispatchEvent(it.action)
            }
        )
    }
}