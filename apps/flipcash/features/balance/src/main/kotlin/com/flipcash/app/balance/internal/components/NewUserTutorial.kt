package com.flipcash.app.balance.internal.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.flipcash.features.balance.R
import com.getcode.theme.CodeTheme

sealed interface TutorialItem {
    val title: String
        @Composable get
    val description: String
        @Composable get
    val icon: Painter
        @Composable get
    val isCompleted: Boolean

    class AddMoney(override val isCompleted: Boolean) : TutorialItem {
        override val title: String
            @Composable get() = stringResource(R.string.title_addMoney)
        override val description: String
            @Composable get() = stringResource(R.string.subtitle_addMoney)
        override val icon: Painter
            @Composable get() = rememberVectorPainter(Icons.Outlined.AddCircleOutline)

    }

    class ScanTipCard(override val isCompleted: Boolean) : TutorialItem {
        override val title: String
            @Composable get() = stringResource(R.string.title_scanTipCard)
        override val description: String
            @Composable get() = stringResource(R.string.subtitle_scanTipCard)
        override val icon: Painter
            @Composable get() = painterResource(R.drawable.ic_nav_scan)
    }
}

@Composable
fun NewUserTutorial(
    title: String,
    items: List<TutorialItem>,
    modifier: Modifier = Modifier,
    onItemClicked: (TutorialItem) -> Unit,
) {
    val completedCount = remember(items) { items.count { it.isCompleted } }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textMain,
            )

            Text(
                text = "$completedCount / ${items.count()}",
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        Column(
            modifier = Modifier
                .clip(CodeTheme.shapes.medium)
                .background(color = Color.White.copy(0.05f)),
        ) {
            items.fastForEach { item ->
                OnboardingItemRow(
                    item = item,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    onItemClicked(item)
                }
            }
        }
    }
}

@Composable
private fun OnboardingItemRow(
    item: TutorialItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(modifier = modifier
        .clickable(enabled = !item.isCompleted, onClick = onClick)
        .padding(CodeTheme.dimens.inset),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Image(
            modifier = Modifier.align(Alignment.Top).size(24.dp),
            painter = if (item.isCompleted) {
                painterResource(R.drawable.ic_checked_green)
            } else {
                item.icon
            },
            colorFilter = if (!item.isCompleted) ColorFilter.tint(CodeTheme.colors.textMain) else null,
            contentDescription = null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (item.isCompleted) 0.38f else 1f)
        ) {
            Text(
                text = item.title,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = item.description,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
        Icon(
            modifier = Modifier.align(Alignment.CenterVertically),
            painter = painterResource(R.drawable.ic_chevron_right),
            tint = CodeTheme.colors.textSecondary,
            contentDescription = null
        )
    }
}