package com.getcode.view.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.view.main.home.components.HomeBottom

@Composable
internal fun DecorView(
    modifier: Modifier = Modifier,
    showBottomSheet: (HomeBottomSheet) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().then(modifier)) {
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

        HomeBottom(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            onPress = {
                showBottomSheet(it)
            },
        )
    }
}