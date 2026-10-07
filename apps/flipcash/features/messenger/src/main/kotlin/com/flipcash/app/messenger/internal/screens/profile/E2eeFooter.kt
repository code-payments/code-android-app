package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme

/**
 * The encryption note pinned to the bottom of a profile: a lock and one line saying whether the
 * chat is end-to-end encrypted, with Learn More under it (nodes 10557:1304, 10557:1643).
 *
 * Passed as the scaffold's bottom bar rather than put in the list, so it stays put while the list
 * scrolls.
 */
@Composable
internal fun E2eeFooter(
    isEncrypted: Boolean,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
    // Off where something below the footer already clears the system bar, as the pinned button on
    // a person's profile does.
    clearNavigationBar: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (clearNavigationBar) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(12.dp),
                imageVector = if (isEncrypted) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(
                    if (isEncrypted) R.string.label_e2eeFooter_dm else R.string.label_e2eeFooter_group
                ),
                style = CodeTheme.typography.textSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            modifier = Modifier.clickable(onClick = onLearnMore),
            text = stringResource(R.string.action_learnMore),
            style = CodeTheme.typography.textSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = CodeTheme.colors.textMain,
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_E2eeFooter_Dm() {
    E2eeFooter(isEncrypted = true, onLearnMore = {})
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_E2eeFooter_Group() {
    E2eeFooter(isEncrypted = false, onLearnMore = {})
}
