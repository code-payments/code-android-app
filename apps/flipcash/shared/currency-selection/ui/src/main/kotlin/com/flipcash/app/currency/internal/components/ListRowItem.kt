package com.flipcash.app.currency.internal.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.currency.internal.CurrencyListItem
import com.flipcash.features.currency.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SwipeActionRow
import com.getcode.ui.core.rememberedClickable

@Composable
internal fun ListRowItem(
    item: CurrencyListItem.RegionCurrencyItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onRemoved: () -> Unit,
    onClick: () -> Unit
) {
    val rowContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.background)
                .let {
                    if (item.currency.rate > 0) {
                        it.rememberedClickable { onClick() }
                    } else it
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = CodeTheme.dimens.inset)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.CenterStart)
                        .alpha(if (item.currency.rate <= 0) 0.25f else 1.0f)
                ) {
                    item.currency.resId?.let { resId ->
                        Image(
                            modifier = Modifier
                                .padding(end = CodeTheme.dimens.grid.x3)
                                .requiredSize(CodeTheme.dimens.staticGrid.x6)
                                .clip(CodeTheme.shapes.large)
                                .align(Alignment.CenterVertically),
                            painter = painterResource(resId),
                            contentDescription = ""
                        )
                    }
                    Column(
                        modifier = Modifier
                            .wrapContentWidth()
                            .align(Alignment.CenterVertically),
                    ) {
                        Text(
                            text = item.currency.name,
                            style = CodeTheme.typography.textMedium,
                            color = CodeTheme.colors.textMain,
                        )
                    }
                }

                Image(
                    modifier = Modifier
                        .wrapContentWidth()
                        .align(Alignment.CenterEnd)
                        .alpha(if (item.currency.rate <= 0) 0.25f else 1.0f),
                    painter = painterResource(
                        if (isSelected)
                            R.drawable.ic_checked else R.drawable.ic_unchecked
                    ),
                    contentDescription = ""
                )
            }
        }
    }

    if (item.isRecent) {
        Column(modifier = modifier) {
            SwipeActionRow(
                modifier = Modifier.weight(1f),
                onDelete = onRemoved,
                stateKey = item.currency.code,
            ) {
                rowContent()
            }
            HorizontalDivider(
                color = CodeTheme.colors.dividerVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(start = CodeTheme.dimens.inset)
            )
        }
    } else {
        Column(modifier = modifier) {
            Box(modifier = Modifier.weight(1f)) {
                rowContent()
            }
            HorizontalDivider(
                color = CodeTheme.colors.dividerVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(start = CodeTheme.dimens.inset)
            )
        }
    }
}