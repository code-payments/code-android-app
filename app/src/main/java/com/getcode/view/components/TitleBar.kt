package com.getcode.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.topBarHeight


@Preview
@Composable
fun TitleBar(
    modifier: Modifier = Modifier,
    title: String = "",
    backButton: Boolean = false,
    onBackIconClicked: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.background(Brand),
        elevation = 0.dp
    ) {
        Box(
            modifier = modifier
                .background(Brand)
                .fillMaxWidth()
                .height(topBarHeight)
        ) {
            if (backButton) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowLeft,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { onBackIconClicked() }
                        .padding(horizontal = 10.dp)
                        .size(34.dp)
                )
            }
            Text(
                text = title,
                color = Color.White,
                style = CodeTheme.typography.subtitle2,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}