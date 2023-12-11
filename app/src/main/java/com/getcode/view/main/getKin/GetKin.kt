package com.getcode.view.main.getKin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.R
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.util.conditionally
import com.getcode.view.SheetSections
import com.getcode.view.main.home.HomeViewModel

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
fun GetKin(upPress: () -> Unit = {}, navController: NavController, homeViewModel: HomeViewModel) {
    val viewModel = hiltViewModel<GetKinSheetViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()

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

                viewModel.requestFirstKinAirdrop(upPress, homeViewModel)
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

                navController.navigate(SheetSections.REFER_FRIEND.route)
            },
        ),
        GetKinItem(
            imageResId = R.drawable.ic_currency_dollar_active,
            inactiveImageResId = R.drawable.ic_currency_dollar_inactive,
            titleTextResId = R.string.subtitle_buyAndSendKin,
            isVisible = true,
            isActive = true,
            isLoading = false,
            isStrikeThrough = false,
            onClick = {
                navController.navigate(SheetSections.BUY_AND_SELL_KIN.route)
            },
        ),
    )

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Text(
                text = stringResource(R.string.title_getKin),
                style = MaterialTheme.typography.h1,
                modifier = Modifier.padding(vertical = 15.dp),
            )
            Text(
                text = stringResource(R.string.subtitle_getKin),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(vertical = 10.dp),
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
                            .conditionally(
                                condition = item.isStrikeThrough.not(),
                            ) {
                                clickable {
                                    item.onClick()
                                }
                            }
                            .padding(vertical = 20.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            modifier = Modifier.size(25.dp),
                            painter = if (item.isActive) painterResource(id = item.imageResId) else painterResource(id = item.inactiveImageResId),
                            contentDescription = "",
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 17.dp)
                                .weight(1f),
                        ) {
                            Text(
                                text = stringResource(item.titleTextResId),
                                color = if (item.isActive) Color.White else colorResource(R.color.code_brand_light),
                                style = MaterialTheme.typography.button.copy(
                                    textDecoration = if (item.isStrikeThrough) TextDecoration.LineThrough else MaterialTheme.typography.button.textDecoration,
                                ),
                            )
                            item.subtitleTextResId?.let {
                                Text(
                                    modifier = Modifier.padding(top = 3.dp),
                                    text = stringResource(it),
                                    style = MaterialTheme.typography.caption,
                                    fontSize = 13.sp,
                                    color = colorResource(R.color.code_brand_light),
                                )
                            }
                        }

                        if (item.isLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.CenterVertically),
                            )
                        } else if (item.isActive) {
                            Image(
                                modifier = Modifier.padding(start = 10.dp),
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

            // TODO: need a better way to reload model
            AnimatedVisibility(visible = true) {
                viewModel.reset()
            }
        }
    }
}
