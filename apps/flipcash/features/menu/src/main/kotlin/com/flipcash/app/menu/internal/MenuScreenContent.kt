package com.flipcash.app.menu.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.features.menu.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.debugBounds
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.plus

@Composable
internal fun MenuScreenContent(viewModel: MenuScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
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
                            .requiredHeight(CodeTheme.dimens.staticGrid.x7)
                            .wrapContentWidth()
                            .rememberedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.dispatchEvent(MenuScreenViewModel.Event.OnLogoTapped)
                            },
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
                        .navigationBarsPadding(),
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
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollStateGradient(
                    scrollState = listState,
                    isLongGradient = true,
                ),
            state = listState,
            contentPadding = PaddingValues(
                top = CodeTheme.dimens.grid.x6
            )
        ) {
            items(state.items, key = { it.id }, contentType = { it }) { item ->
                ListItem(item = item) {
                    viewModel.dispatchEvent(item.action)
                }
            }
        }
    }
}

@Composable
private fun ListItem(item: MenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .rememberedClickable { onClick() }
            .padding(CodeTheme.dimens.grid.x5)
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = CenterVertically
    ) {
        Image(
            modifier = Modifier
                .padding(end = CodeTheme.dimens.inset)
                .height(CodeTheme.dimens.staticGrid.x5)
                .width(CodeTheme.dimens.staticGrid.x5),
            painter = item.icon,
            colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
            contentDescription = ""
        )

        Text(
            modifier = Modifier.align(CenterVertically),
            text = item.name,
            style = CodeTheme.typography.textLarge.copy(
                fontWeight = FontWeight.Bold
            ),
        )

        Spacer(Modifier.weight(1f))

        if (item.isStaffOnly) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
            ) {
                Box(
                    modifier = Modifier
                        .size(CodeTheme.dimens.grid.x1)
                        .background(
                            color = CodeTheme.colors.betaIndicator,
                            shape = CircleShape
                        )
                )

                Text(
                    text = stringResource(R.string.subtitle_beta),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }
    }

    Divider(
        modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
        color = CodeTheme.colors.divider,
        thickness = 0.5.dp
    )
}