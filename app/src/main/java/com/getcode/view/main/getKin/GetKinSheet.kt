package com.getcode.view.main.getKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import com.getcode.R
import com.getcode.models.Bill
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.BuySellScreen
import com.getcode.navigation.screens.HomeResult
import com.getcode.navigation.screens.ReferFriendScreen
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.ui.components.CodeCircularProgressIndicator
import com.getcode.ui.utils.RepeatOnLifecycle
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberedClickable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

data class GetKinItem(
    val imageResId: Int,
    val inactiveImageResId: Int,
    val titleTextResId: Int,
    val subtitleTextResId: Int? = null,
    val isVisible: Boolean,
    val isActive: Boolean,
    val isLoading: Boolean,
    val isStrikeThrough: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun GetKinSheet(
    viewModel: GetKinSheetViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.stateFlow.collectAsState()

    val items = listOf(
        GetKinItem(
            imageResId = R.drawable.ic_gift,
            inactiveImageResId = R.drawable.ic_gift_inactive,
            titleTextResId = R.string.subtitle_getYourFirstKinFree,
            subtitleTextResId = R.string.title_limitedTimeOffer,
            isVisible = true,
            isActive = dataState.isEligibleGetFirstKinAirdrop,
            isLoading = dataState.isGetFirstKinAirdropLoading,
            isStrikeThrough = !dataState.isEligibleGetFirstKinAirdrop,
            onClick = {
                if (!dataState.isEligibleGetFirstKinAirdrop || dataState.isGetFirstKinAirdropLoading) {
                    return@GetKinItem
                }

                viewModel.dispatchEvent(GetKinSheetViewModel.Event.RequestedFirstKin)
            },
        ),
        GetKinItem(
            imageResId = R.drawable.ic_send2,
            inactiveImageResId = R.drawable.ic_send2_inactive,
            titleTextResId = R.string.title_referFriend,
            subtitleTextResId = R.string.title_limitedTimeOffer,
            isVisible = dataState.isEligibleGiveFirstKinAirdrop,
            isActive = dataState.isEligibleGiveFirstKinAirdrop,
            isLoading = false,
            isStrikeThrough = !dataState.isEligibleGiveFirstKinAirdrop,
            onClick = {
                if (!dataState.isEligibleGiveFirstKinAirdrop) {
                    return@GetKinItem
                }

                navigator.push(ReferFriendScreen)
            },
        ),
        GetKinItem(
            imageResId = R.drawable.ic_currency_dollar_active,
            inactiveImageResId = R.drawable.ic_currency_dollar_inactive,
            titleTextResId = R.string.title_buySellKin,
            isVisible = true,
            isActive = true,
            isLoading = false,
            isStrikeThrough = false,
            onClick = {
                navigator.push(BuySellScreen)
            },
        ),
    )

    RepeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.eventFlow
            .filterIsInstance<GetKinSheetViewModel.Event.OnKinReadyToGrab>()
            .map { it.amount }
            .onEach {
                navigator.hideWithResult(
                    HomeResult.Bill(
                        Bill.Cash(
                            kind = Bill.Kind.firstKin,
                            amount = it,
                            didReceive = true
                        )
                    )
                )
            }.launchIn(this)
    }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = CodeTheme.dimens.inset),
    ) {
        val (topSection, bottomSection) = createRefs()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .constrainAs(topSection) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_graphic_wallet),
                contentDescription = "",
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
            )
            Text(
                text = stringResource(R.string.title_getKin),
                style = CodeTheme.typography.h1,
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3),
            )
            Text(
                text = stringResource(R.string.subtitle_getKin),
                style = CodeTheme.typography.body1,
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .constrainAs(bottomSection) {
                    top.linkTo(topSection.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom)
                },
        ) {
            Column {
                for (item in items) {
                    if (!item.isVisible) {
                        continue
                    }

                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(White05),
                    )

                    Row(
                        modifier = Modifier
                            .addIf(
                                item.isStrikeThrough.not(),
                            ) {
                                Modifier.rememberedClickable { item.onClick() }
                            }
                            .padding(
                                vertical = CodeTheme.dimens.grid.x4,
                                horizontal = CodeTheme.dimens.grid.x2
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
                            painter = if (item.isActive) painterResource(id = item.imageResId) else painterResource(id = item.inactiveImageResId),
                            contentDescription = "",
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = CodeTheme.dimens.grid.x3)
                                .weight(1f),
                        ) {
                            Text(
                                text = stringResource(item.titleTextResId),
                                color = if (item.isActive) Color.White else colorResource(R.color.code_brand_light),
                                style = CodeTheme.typography.button.copy(
                                    textDecoration = if (item.isStrikeThrough) TextDecoration.LineThrough else CodeTheme.typography.button.textDecoration,
                                ),
                            )
                            item.subtitleTextResId?.let {
                                Text(
                                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                                    text = stringResource(it),
                                    style = CodeTheme.typography.body2,
                                    color = colorResource(R.color.code_brand_light),
                                )
                            }
                        }

                        if (item.isLoading) {
                            CodeCircularProgressIndicator(
                                strokeWidth = CodeTheme.dimens.thickBorder,
                                color = White,
                                modifier = Modifier
                                    .size(CodeTheme.dimens.grid.x3)
                                    .align(Alignment.CenterVertically),
                            )
                        } else if (item.isActive) {
                            Image(
                                modifier = Modifier.padding(start = CodeTheme.dimens.grid.x2),
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = "",
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(White05),
            )
        }
    }
}
