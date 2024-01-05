package com.getcode.view.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.topBarHeight
import com.getcode.util.rememberedClickable
import com.getcode.util.unboundedClickable

@Composable
fun SheetTitle(
    modifier: Modifier = Modifier,
    title: (@Composable () -> String?)? = null,
    displayLogo: Boolean = false,
    onLogoClicked: () -> Unit = { },
    backButton: Boolean = false,
    onBackIconClicked: () -> Unit = {},
    closeButton: Boolean = !backButton,
    onCloseIconClicked: () -> Unit = {},
) {
    Surface(
        modifier = modifier,
        color = Brand,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brand)
                .padding(vertical = CodeTheme.dimens.grid.x2)
                .fillMaxWidth()
                .height(topBarHeight),
        ) {
            if (closeButton) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = CodeTheme.dimens.inset)
                        .wrapContentWidth()
                        .size(CodeTheme.dimens.staticGrid.x6)
                        .unboundedClickable { onCloseIconClicked() }
                )
            }

            if (backButton) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = CodeTheme.dimens.inset)
                        .wrapContentWidth()
                        .size(CodeTheme.dimens.staticGrid.x6)
                        .unboundedClickable { onBackIconClicked() }
                )
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
                Text(
                    text = title?.invoke().orEmpty(),
                    color = Color.White,
                    style = CodeTheme.typography.h6.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Preview
@Composable
fun TitlePreview() {
    SheetTitle(
        title = { "Sheet Title" }
    )
}