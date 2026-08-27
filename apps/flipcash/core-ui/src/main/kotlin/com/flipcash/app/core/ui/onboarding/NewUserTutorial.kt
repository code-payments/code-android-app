package com.flipcash.app.core.ui.onboarding

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall

/**
 * A row in a "what's left to do" checklist.
 *
 * Split by the screen that owns it: [Wallet] and [Profile] are drawn by different tabs and share
 * nothing but the row layout, so each call site's `when` stays exhaustive over its own family and
 * cannot be handed an item it has no branch for.
 */
sealed interface TutorialItem {
    val title: String
        @Composable get
    val description: String
        @Composable get
    val icon: Painter
        @Composable get
    val isCompleted: Boolean

    /** The wallet tab's new-user milestones. */
    sealed interface Wallet : TutorialItem

    /** The "You" tab's profile-completion steps (node 9544:18140). */
    sealed interface Profile : TutorialItem

    class AddMoney(override val isCompleted: Boolean) : Wallet {
        override val title: String
            @Composable get() = stringResource(R.string.title_addMoney)
        override val description: String
            @Composable get() = stringResource(R.string.subtitle_addMoney)
        override val icon: Painter
            @Composable get() = rememberVectorPainter(Icons.Outlined.AddCircleOutline)
    }

    class ScanTipCard(override val isCompleted: Boolean) : Wallet {
        override val title: String
            @Composable get() = stringResource(R.string.title_scanTipCard)
        override val description: String
            @Composable get() = stringResource(R.string.subtitle_scanTipCard)
        override val icon: Painter
            @Composable get() = painterResource(R.drawable.ic_nav_scan)
    }

    class ProfilePicture(override val isCompleted: Boolean) : Profile {
        override val title: String
            @Composable get() = stringResource(R.string.title_addProfilePicture)
        override val description: String
            @Composable get() = stringResource(R.string.subtitle_addProfilePicture)
        override val icon: Painter
            @Composable get() = painterResource(R.drawable.ic_people_circle)
    }

    /**
     * Drawn but inert. Nothing backs a user-set minimum tip yet: the amount comes from
     * server-supplied regional presets, no field for it exists on the profile or the tip-card
     * customization message, and iOS has no implementation either. The row is in the design, so
     * it is drawn — and it never completes, which is the state node 9641:17019 shows.
     */
    class MinimumTip(override val isCompleted: Boolean = false) : Profile {
        override val title: String
            @Composable get() = stringResource(R.string.title_setMinimumTip)
        override val description: String
            @Composable get() = stringResource(R.string.subtitle_setMinimumTip)
        override val icon: Painter
            @Composable get() = painterResource(R.drawable.ic_coins)
    }
}

@Composable
fun <T : TutorialItem> NewUserTutorial(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    onItemClicked: (T) -> Unit,
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
                .clip(CodeTheme.shapes.extraSmall)
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

@Preview(name = "Finish Your Profile — nothing done")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun PreviewFinishProfileEmpty() {
    NewUserTutorial(
        title = stringResource(R.string.title_finishYourProfile),
        items = listOf(
            TutorialItem.ProfilePicture(isCompleted = false),
            TutorialItem.MinimumTip(),
        ),
        onItemClicked = {},
    )
}

@Preview(name = "Finish Your Profile — photo set")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun PreviewFinishProfilePhotoSet() {
    NewUserTutorial(
        title = stringResource(R.string.title_finishYourProfile),
        items = listOf(
            TutorialItem.ProfilePicture(isCompleted = true),
            TutorialItem.MinimumTip(),
        ),
        onItemClicked = {},
    )
}
