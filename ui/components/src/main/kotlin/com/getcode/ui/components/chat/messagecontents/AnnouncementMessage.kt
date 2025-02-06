package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.AnnouncementAction
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Pill
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun AnnouncementMessage(
    modifier: Modifier = Modifier,
    text: String,
) {
    BoxWithConstraints(modifier = modifier) {
        Pill(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = maxWidth * 0.78f),
            text = text,
            textStyle = CodeTheme.typography.caption.copy(textAlign = TextAlign.Center),
            backgroundColor = CodeTheme.colors.surfaceVariant,
            contentColor = CodeTheme.colors.textSecondary
        )
    }
}

@Composable
internal fun ActionableAnnouncementMessage(
    modifier: Modifier = Modifier,
    text: String,
    action: AnnouncementAction,
) {
    val resolver = LocalAnnouncementActionResolver.current
    val resolvedAction = remember(resolver, action) { resolver(action) }

    val inset = CodeTheme.dimens.inset

    BoxWithConstraints(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = maxWidth - inset - inset) // max width sans inset on both sides
                .border(
                    color = CodeTheme.colors.tertiary,
                    width = 1.dp,
                    shape = CodeTheme.shapes.medium,
                )
                .padding(
                    horizontal = CodeTheme.dimens.grid.x2,
                    vertical = CodeTheme.dimens.grid.x2
                )
        ) contents@{
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {

                Text(
                    text = text,
                    style = CodeTheme.typography.textSmall.copy(textAlign = TextAlign.Center),
                    color = CodeTheme.colors.textSecondary,

                )

                if (resolvedAction != null) {
                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = resolvedAction.text,
                        buttonState = ButtonState.Filled,
                    ) { resolvedAction.onClick() }
                }
            }
        }
    }
}

data class ResolvedAction(
    val text: String,
    val onClick: () -> Unit
)

val LocalAnnouncementActionResolver: ProvidableCompositionLocal<(AnnouncementAction) -> ResolvedAction?> = staticCompositionLocalOf { { null } }