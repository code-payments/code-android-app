package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.core.onramp.ui.AnnotatedButtonLabel
import com.flipcash.core.R
import com.getcode.theme.CodeTheme

@Composable
fun buildNotifyButtonLabel(): AnnotatedButtonLabel {
    return buildAnnotatedString {
        appendInlineContent("[icon]", alternateText = " ")
        append("We'll Notify You")
    } to mapOf(
        "[icon]" to InlineTextContent(
            placeholder = Placeholder(
                width = 25.sp,
                height = 14.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            ),
            children = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier.padding(
                            start = CodeTheme.dimens.staticGrid.x1 + 2.dp,
                            end = CodeTheme.dimens.staticGrid.x1
                        ),
                        painter = painterResource(R.drawable.ic_check),
                        colorFilter = ColorFilter.tint(CodeTheme.colors.success),
                        contentDescription = null
                    )
                }
            }
        )
    )
}