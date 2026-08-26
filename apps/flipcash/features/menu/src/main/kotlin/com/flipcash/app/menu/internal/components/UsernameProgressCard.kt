package com.flipcash.app.menu.internal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.SystemGreen
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.theme.White50
import com.getcode.theme.extraSmall

/**
 * How close the account is to being allowed a `@handle`, and — once it is — the way in.
 *
 * Locked and [Unlocked] are the design's two variants (nodes 9536:4336 and 9537:1845) rather than a
 * bag of nullable fields, so a full bar can never render next to an amount still to go.
 */
internal sealed interface UsernameProgress {
    /**
     * @param fraction how much of the minimum the balance covers, `0f..1f`.
     * @param remaining the shortfall, already formatted for display (e.g. `$12.50 USD`).
     */
    data class Locked(val fraction: Float, val remaining: String) : UsernameProgress

    data object Unlocked : UsernameProgress
}

/**
 * Nodes 9536:4336 / 9537:1845 — the "You" tab's nudge toward claiming a `@handle`, sitting under the
 * Share / Download tiles in the same 88dp skin.
 *
 * Tappable in both states: below the minimum the tap is what surfaces the "Minimum Balance Required"
 * sheet, which is the only place the rule is spelled out. The caller decides that, and also decides
 * whether the card renders at all — it is gone once a handle exists.
 *
 * [minimumBalance] is the formatted threshold (e.g. `$100 USD`), interpolated into the locked
 * subtitle; it comes from the same `usernameMinBalance` flag the entry screen's rejection dialog
 * reads, so the two never quote different numbers.
 */
@Composable
internal fun UsernameProgressCard(
    progress: UsernameProgress,
    minimumBalance: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CodeTheme.dimens.grid.x18)
            .clip(CodeTheme.shapes.extraSmall)
            .background(White05)
            .clickable { onClick() }
            .padding(horizontal = CodeTheme.dimens.grid.x3),
    ) {
        Column(modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.title_usernameUpsell),
                    style = CodeTheme.typography.textSmall,
                    color = White,
                )
                when (progress) {
                    is UsernameProgress.Locked -> Text(
                        text = stringResource(R.string.label_usernameAmountToGo, progress.remaining),
                        style = CodeTheme.typography.textSmall,
                        color = White50,
                    )

                    // The affordance only appears once tapping it leads somewhere other than a
                    // rejection.
                    UsernameProgress.Unlocked -> Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = White50,
                    )
                }
            }
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = when (progress) {
                    is UsernameProgress.Locked -> stringResource(
                        R.string.subtitle_usernameUpsellLocked,
                        minimumBalance,
                    )

                    UsernameProgress.Unlocked ->
                        stringResource(R.string.subtitle_usernameUpsellUnlocked)
                },
                style = CodeTheme.typography.caption,
                color = White50,
            )
        }

        UsernameProgressBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CodeTheme.dimens.grid.x3),
            fraction = when (progress) {
                is UsernameProgress.Locked -> progress.fraction
                UsernameProgress.Unlocked -> 1f
            },
            // Green only on the full bar: it reads as "done", which a partial bar isn't.
            color = when (progress) {
                is UsernameProgress.Locked -> White
                UsernameProgress.Unlocked -> SystemGreen
            },
        )
    }
}

@Composable
private fun UsernameProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CodeTheme.dimens.grid.x1)
            .clip(CircleShape)
            .background(White10),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Preview(name = "Below the minimum")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UsernameProgressCard_Locked() {
    Box(modifier = Modifier.fillMaxSize().padding(CodeTheme.dimens.inset)) {
        UsernameProgressCard(
            progress = UsernameProgress.Locked(fraction = 0.84f, remaining = "$12.50 USD"),
            minimumBalance = "$100 USD",
            onClick = {},
        )
    }
}

@Preview(name = "Minimum met")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UsernameProgressCard_Unlocked() {
    Box(modifier = Modifier.fillMaxSize().padding(CodeTheme.dimens.inset)) {
        UsernameProgressCard(
            progress = UsernameProgress.Unlocked,
            minimumBalance = "$100 USD",
            onClick = {},
        )
    }
}
