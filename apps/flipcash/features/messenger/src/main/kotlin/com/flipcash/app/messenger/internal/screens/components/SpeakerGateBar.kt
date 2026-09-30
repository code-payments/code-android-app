package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

/**
 * Stands where the composer does for a member the chat's speaker rules keep from speaking (node
 * 10588:1969 for the `never` copy). Mirrors iOS `ConversationGatePresentation.readOnly`.
 *
 * The surface is the composer field's own: the same shape, outline and blur over the same
 * [hazeState], so the transcript scrolling under it reads as it does under the field. Disabled,
 * with no input, attach or send button and no call to action: none of the three requirements has an
 * action the viewer could take from here.
 *
 * Copy follows iOS `ConversationGatePanel`: `never` names the sender, a balance states the amount
 * as a sending requirement, staff names who may send.
 */
@Composable
internal fun SpeakerGateBar(
    /** The requirement standing in the viewer's way. */
    requirement: ChatRuleRequirement,
    hazeState: HazeState,
    /** The token a balance rule names, or null to state the amount alone (the reserve, or unresolved). */
    currencyName: String?,
    modifier: Modifier = Modifier,
    /** Off only for a Robolectric render, whose native runtime cannot compile the blur shader. */
    blurEnabled: Boolean = true,
) {
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)
    val text = when (requirement) {
        ChatRuleRequirement.Never -> stringResource(R.string.label_chatGate_speakerNever)
        ChatRuleRequirement.Staff -> stringResource(R.string.subtitle_chatGate_speakerStaffOnly)
        is ChatRuleRequirement.MinimumBalance ->
            if (currencyName != null) {
                stringResource(
                    R.string.subtitle_chatGate_speakerMinimumBalance,
                    requirement.amount.formatted(),
                    currencyName,
                )
            } else {
                stringResource(
                    R.string.subtitle_chatGate_speakerMinimumBalance_anyToken,
                    requirement.amount.formatted(),
                )
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.grid.x6.coerceAtLeast(CodeTheme.dimens.inset))
            .padding(vertical = CodeTheme.dimens.grid.x3)
            .height(50.dp)
            .border(CodeTheme.dimens.border, CodeTheme.colors.divider, CodeTheme.shapes.medium)
            .then(if (blurEnabled) Modifier.hazeBlur(HazeInput.Sources(hazeState), material) else Modifier)
            .padding(horizontal = CodeTheme.dimens.grid.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Preview
@Composable
private fun SpeakerGateBarPreview() {
    FlipcashPreview(showBackground = true) {
        SpeakerGateBar(
            requirement = ChatRuleRequirement.Never,
            hazeState = rememberHazeState(),
            currencyName = null,
        )
    }
}
