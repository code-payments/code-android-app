package com.getcode.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.topBarHeight
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.unboundedClickable

@Composable
fun BoxScope.SheetTitleText(modifier: Modifier = Modifier, text: String) {
    Text(
        text = text,
        color = Color.White,
        style = CodeTheme.typography.screenTitle,
        modifier = modifier.align(Alignment.Center)
    )
}

object SheetTitleDefaults {
    @Composable
    fun BackButton() {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "",
            tint = Color.White,
        )
    }

    @Composable
    fun CloseButton() {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "",
            tint = Color.White,
        )
    }

    @Composable
    fun RefreshButton() {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "",
            tint = Color.White,
        )
    }
}

@Composable
fun SheetTitle(
    modifier: Modifier = Modifier,
    color: Color = CodeTheme.colors.background,
    title: @Composable BoxScope.() -> Unit = { },
    displayLogo: Boolean = false,
    onLogoClicked: () -> Unit = { },
    backButton: @Composable () -> Unit = { SheetTitleDefaults.BackButton() },
    backButtonEnabled: Boolean = false,
    onBackIconClicked: () -> Unit = {},
    closeButton: @Composable () -> Unit = { SheetTitleDefaults.CloseButton() },
    closeButtonEnabled: Boolean = !backButtonEnabled,
    onCloseIconClicked: () -> Unit = {},
) {
    Surface(
        modifier = modifier,
        color = color,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color)
                .padding(vertical = CodeTheme.dimens.grid.x2)
                .fillMaxWidth()
                .height(topBarHeight),
        ) {
            if (closeButtonEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = CodeTheme.dimens.inset)
                        .wrapContentWidth()
                        .size(CodeTheme.dimens.staticGrid.x6)
                        .unboundedClickable { onCloseIconClicked() }
                ) {
                    closeButton()
                }
            }

            if (backButtonEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = CodeTheme.dimens.inset)
                        .wrapContentWidth()
                        .size(CodeTheme.dimens.staticGrid.x6)
                        .unboundedClickable { onBackIconClicked() }
                ) {
                    backButton()
                }
            }

            if (displayLogo) {
                Image(
                    painterResource(
                        R.drawable.ic_code_logo_near_white
                    ),
                    contentDescription = "",
                    modifier = Modifier
                        .requiredHeight(CodeTheme.dimens.staticGrid.x8)
                        .align(Alignment.Center)
                        .rememberedClickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onLogoClicked() }
                )
            } else {
                title()
            }
        }
    }
}

@Preview
@Composable
fun TitlePreview() {
    SheetTitle(
        title = {
            SheetTitleText(text = "Sheet Title")
        }
    )
}