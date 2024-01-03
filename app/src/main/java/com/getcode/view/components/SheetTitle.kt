package com.getcode.view.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.topBarHeight
import com.getcode.view.main.account.AccountSheetViewModel

@Composable
fun SheetTitle(
    modifier: Modifier = Modifier,
    title: String? = null,
    displayLogo: Boolean = false,
    onLogoClicked: () -> Unit = { },
    backButton: Boolean = false,
    onBackIconClicked: () -> Unit = {},
    closeButton: Boolean = !backButton,
    onCloseIconClicked: () -> Unit = {},
) {
    Surface(
        elevation = 0.dp,
        modifier = modifier.background(Brand)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brand)
                .padding(top = 10.dp, bottom = 10.dp)
                .fillMaxWidth()
                .height(topBarHeight),
        ) {
            if (closeButton) {
                IconButton(
                    onClick = onCloseIconClicked,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier
                            .wrapContentWidth()
                            .size(35.dp)
                    )
                }
            }

            if (backButton) {
                IconButton(
                    onClick = onBackIconClicked,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier
                            .wrapContentWidth()
                            .size(35.dp)
                    )
                }
            }

            if (displayLogo) {
                Image(
                    painterResource(
                        R.drawable.ic_code_logo_near_white
                    ),
                    contentDescription = "",
                    modifier = Modifier
                        .height(40.dp)
                        .align(Alignment.Center)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onLogoClicked() }
                )
            } else {
                Text(
                    text = title.orEmpty(),
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
        title = "Sheet Title"
    )
}