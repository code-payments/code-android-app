package com.flipcash.app.tokens.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.theme.MultiDevicePreview
import com.flipcash.shared.tokens.R
import com.getcode.theme.CodeTheme

@Composable
fun CurrencyCreatorUpsellCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = CodeTheme.colors.surfaceVariant,
        contentColor = CodeTheme.colors.textMain,
        shape = CodeTheme.shapes.medium,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            val screenWidth = CodeTheme.dimens.screenWidth
            val imageWidthFraction = when {
                screenWidth < 360.dp -> 0.28f
                screenWidth < 420.dp -> 0.33f
                else -> 0.38f
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = CodeTheme.dimens.grid.x3,
                        top = CodeTheme.dimens.grid.x3,
                        bottom = CodeTheme.dimens.grid.x3,
                    )
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = stringResource(R.string.action_createYourOwnCurrency),
                        style = CodeTheme.typography.screenTitle,
                        color = CodeTheme.colors.textMain,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp),
                    )
                    Icon(
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                        imageVector = Icons.AutoMirrored.Default.ArrowForward,
                        contentDescription = null,
                        tint = CodeTheme.colors.textMain,
                    )
                }

                Text(
                    text = stringResource(R.string.subtitle_createYourOwnCurrency),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }

            Image(
                modifier = Modifier
                    .fillMaxWidth(imageWidthFraction),
                painter = painterResource(R.drawable.ic_bill_previews),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

@MultiDevicePreview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CurrencyCreatorUpsellCard() {
    CurrencyCreatorUpsellCard { }
}