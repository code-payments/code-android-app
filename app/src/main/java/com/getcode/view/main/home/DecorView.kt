package com.getcode.view.main.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.Black50
import com.getcode.theme.CodeTheme
import com.getcode.view.main.home.components.HomeBottom
import timber.log.Timber

@Composable
internal fun DecorView(
    dataState: HomeUiModel,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    showBottomSheet: (HomeBottomSheet) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
    ) {
        Image(
            modifier = Modifier
                .statusBarsPadding()
                .padding(vertical = 15.dp)
                .padding(horizontal = 15.dp)
                .align(Alignment.TopStart),
            painter = painterResource(
                R.drawable.ic_code_logo_white
            ),
            contentDescription = "",
        )

        Image(
            modifier = Modifier
                .statusBarsPadding()
                .padding(vertical = 10.dp)
                .padding(horizontal = 15.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .clickable {
                    showBottomSheet(HomeBottomSheet.ACCOUNT)
                },
            painter = painterResource(
                R.drawable.ic_home_options
            ),
            contentDescription = "",
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            //Balance Changed Toast
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 25.dp, bottom = 16.dp),
                visible = dataState.billState.showToast,
                enter = slideInVertically(animationSpec = tween(600), initialOffsetY = { it }) +
                        fadeIn(animationSpec = tween(500, 100)),
                exit = if (!isPaused)
                    slideOutVertically(animationSpec = tween(600), targetOffsetY = { it }) +
                            fadeOut(animationSpec = tween(500, 100))
                else fadeOut(animationSpec = tween(0)),
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(RoundedCornerShape(25.dp))
                        .background(Black50)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    val toast = dataState.billState.toast
                    Timber.d("toast=$toast")
                    Text(
                        text = toast?.formattedAmount.orEmpty(),
                        style = CodeTheme.typography.body2.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            HomeBottom(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp),
                onPress = {
                    showBottomSheet(it)
                },
            )
        }
    }
}