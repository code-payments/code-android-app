package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.topBarHeight
import com.getcode.ui.utils.unboundedClickable


@Composable
fun TitleBar(
    modifier: Modifier = Modifier,
    title: String = "",
    backButton: Boolean = false,
    onBackIconClicked: () -> Unit = {}
) {
    Surface(
        color = Brand,
        elevation = 0.dp
    ) {
        Box(
            modifier = modifier
                .statusBarsPadding()
                .background(Brand)
                .fillMaxWidth()
                .height(topBarHeight),
        ) {
            if (backButton) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = CodeTheme.dimens.inset)
                        .wrapContentWidth()
                        .size(24.dp)
                        .unboundedClickable { onBackIconClicked() }
                )
            }
            Text(
                text = title,
                color = Color.White,
                style = CodeTheme.typography.screenTitle,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Preview
@Composable
fun Preview_TitleBar(

) {
    CodeTheme {
        TitleBar(backButton = true, title = "Hey")
    }
}