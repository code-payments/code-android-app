package com.flipcash.app.currency.internal.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FixedThreshold
import androidx.compose.material.Icon
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.currency.internal.CurrencyListItem
import com.flipcash.features.currency.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ListRowItem(
    item: CurrencyListItem.RegionCurrencyItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onRemoved: () -> Unit,
    onClick: () -> Unit
) {

    var removed by remember(item) {
        mutableStateOf(false)
    }

    val dismissState = remember(item) {
        DismissState(
            initialValue = DismissValue.Default,
            confirmStateChange = {
                if (it == DismissValue.DismissedToStart) {
                    removed = true
                    true
                } else false
            }
        )
    }

    SwipeToDismiss(
        modifier = modifier,
        state = dismissState,
        dismissThresholds = { FixedThreshold(150.dp) },
        directions = if (item.isRecent) setOf(DismissDirection.EndToStart) else emptySet(),
        background = {
            if (item.isRecent) {
                DismissBackground(dismissState)
            }
        }
    ) {
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
                            style = CodeTheme.typography.textMedium
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

            Divider(
                color = CodeTheme.colors.dividerVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.BottomCenter)
                    .padding(start = CodeTheme.dimens.inset)
            )
        }
    }

    LaunchedEffect(removed) {
        if (removed) {
            delay(200)
            onRemoved()
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DismissBackground(dismissState: DismissState) {
    val color = when (dismissState.dismissDirection) {
        DismissDirection.EndToStart -> CodeTheme.colors.error
        else -> CodeTheme.colors.background
    }
    val direction = dismissState.dismissDirection

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(end = CodeTheme.dimens.inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (direction == DismissDirection.EndToStart) {
            Icon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = "delete"
            )
        }
    }
}