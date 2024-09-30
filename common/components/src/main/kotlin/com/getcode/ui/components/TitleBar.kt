package com.getcode.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.unboundedClickable


@Composable
fun TitleBar(
    modifier: Modifier = Modifier,
    title: String = "",
    backButton: Boolean = false,
    onBackIconClicked: () -> Unit = {},
    endContent: @Composable BoxScope.() -> Unit = { },
) {
    TopAppBarBase(
        modifier = modifier,
        leftIcon = {
            if (backButton) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .wrapContentWidth()
                        .size(24.dp)
                        .unboundedClickable { onBackIconClicked() }
                )
            }
        },
        titleRegion = {
            Text(
                text = title,
                color = Color.White,
                style = CodeTheme.typography.screenTitle,
            )
        },
        rightContents = endContent
    )
}

@Composable
private fun TopAppBarBase(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = CodeTheme.dimens.inset),
    leftIcon: @Composable BoxScope.() -> Unit = { },
    titleRegion: @Composable () -> Unit = { },
    rightContents: @Composable BoxScope.() -> Unit = { }
) {
    Box(
        modifier = modifier
            .height(56.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            titleRegion()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(40.dp, Dp.Unspecified)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                leftIcon()
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(48.dp, Dp.Unspecified)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
                contentAlignment = Alignment.Center
            ) {
                rightContents()
            }
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