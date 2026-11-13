package com.flipcash.shared.chat.ui

// kmp-audit: renders CodeButton, which lives in :ui:components androidMain (RuntimeShader frosted glass)

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.subtitle_groupReadyEmpty
import com.flipcash.core.resources.title_groupReadyEmpty
import com.flipcash.core.resources.title_invitePeople
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import org.jetbrains.compose.resources.stringResource

/**
 * "Your group is ready": Group Money's form of [GroupReadyCardBubble] (frames 1-06 and 1-08, node
 * 13729:658). The same moment, drawn as a centred empty state rather than a message from Flipcash:
 * a title, one line on what to do, and the button that does it.
 */
@Composable
fun GroupReadyEmptyState(
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.grid.x8)
            .padding(top = CodeTheme.dimens.grid.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Text(
            text = stringResource(Res.string.title_groupReadyEmpty),
            style = CodeTheme.typography.screenTitle.copy(fontWeight = FontWeight.Bold),
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.subtitle_groupReadyEmpty),
            style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.Normal),
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CodeTheme.dimens.grid.x2))
        CodeButton(
            text = stringResource(Res.string.title_invitePeople),
            buttonState = ButtonState.Filled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.grid.x4),
            onClick = onInvite,
        )
    }
}

@Preview
@Composable
private fun GroupReadyEmptyStatePreview() {
    FlipcashPreview(showBackground = true) {
        GroupReadyEmptyState(onInvite = {})
    }
}
