package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.flipcash.app.theme.FlipcashPreview

/**
 * Node 12846:259 (4d). The invite link handed the viewer a key that failed its checks, so the
 * group cannot be read. Leaving is the only repair the contract offers.
 */
@Composable
internal fun WrongKeyBanner(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CodeTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(CodeTheme.dimens.border, CodeTheme.colors.divider, shape)
            .padding(CodeTheme.dimens.inset),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Text(
            text = stringResource(R.string.title_cantReadGroup),
            style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CodeTheme.colors.textMain,
        )
        Text(
            text = stringResource(R.string.description_cantReadGroup),
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
        )
        CodeButton(
            modifier = Modifier.fillMaxWidth().padding(top = CodeTheme.dimens.grid.x2),
            text = stringResource(R.string.action_leaveGroup),
            buttonState = ButtonState.Bordered,
            onClick = onLeave,
        )
    }
}

@Preview
@Composable
private fun WrongKeyBannerPreview() {
    FlipcashPreview(showBackground = true) {
        WrongKeyBanner(onLeave = {}, modifier = Modifier.padding(CodeTheme.dimens.inset))
    }
}
