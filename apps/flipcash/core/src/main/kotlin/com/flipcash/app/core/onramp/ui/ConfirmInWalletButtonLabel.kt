package com.flipcash.app.core.onramp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.core.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.theme.CodeTheme

@Composable
fun buildExternalWalletButtonLabel(
    prefix: String,
    provider: OnRampProvider.UsesDeeplinks,
    iconColor: Color
): AnnotatedButtonLabel {
    val (title, icon) = when (provider) {
        OnRampProvider.Backpack -> stringResource(R.string.label_backpack) to painterResource(R.drawable.ic_backpack_wallet)
        OnRampProvider.Phantom -> stringResource(R.string.label_phantom) to painterResource(R.drawable.ic_phantom_wallet)
        OnRampProvider.Solflare -> stringResource(R.string.label_solflare) to painterResource(R.drawable.ic_solflare_wallet)
    }

    return buildAnnotatedString {
        append(prefix)
        appendInlineContent("[icon]", alternateText = " ")
        append(title)
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
                        painter = icon,
                        colorFilter = ColorFilter.tint(iconColor),
                        contentDescription = null
                    )
                }
            }
        )
    )
}