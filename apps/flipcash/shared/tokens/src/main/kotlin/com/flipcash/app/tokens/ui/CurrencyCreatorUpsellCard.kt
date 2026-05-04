package com.flipcash.app.tokens.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
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
        Box(modifier = Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            var imageHeight by remember { mutableStateOf(0.dp) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = imageHeight)
                    .padding(
                        start = CodeTheme.dimens.grid.x3,
                        top = CodeTheme.dimens.grid.x3,
                        bottom = CodeTheme.dimens.grid.x3,
                    ),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            ) {
                val arrowId = "arrow"
                val arrowSize = with(LocalDensity.current) {
                    CodeTheme.dimens.staticGrid.x4.toSp()
                }
                val titleText = buildAnnotatedString {
                    append(stringResource(R.string.action_createYourOwnCurrency))
                    append(" ")
                    appendInlineContent(arrowId)
                }
                Text(
                    text = titleText,
                    style = CodeTheme.typography.screenTitle,
                    color = CodeTheme.colors.textMain,
                    inlineContent = mapOf(
                        arrowId to InlineTextContent(
                            Placeholder(
                                width = arrowSize,
                                height = arrowSize,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                contentDescription = null,
                                tint = CodeTheme.colors.textMain,
                            )
                        }
                    )
                )

                Text(
                    modifier = Modifier.fillMaxWidth(0.65f),
                    text = stringResource(R.string.subtitle_createYourOwnCurrency),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }

            Image(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .onSizeChanged { size ->
                        imageHeight = with(density) { size.height.toDp() }
                    },
                painter = painterResource(R.drawable.ic_bill_previews),
                contentDescription = null,
            )
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CurrencyCreatorUpsellCard() {
    CurrencyCreatorUpsellCard() { }
}