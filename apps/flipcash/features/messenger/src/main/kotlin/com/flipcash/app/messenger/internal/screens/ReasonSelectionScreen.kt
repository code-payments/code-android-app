package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.flipcash.features.messenger.R
import com.flipcash.reporting.ReportReason
import com.getcode.navigation.flow.FlowDismissStyle
import com.getcode.navigation.flow.LocalFlowDismissStyle
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.extraSmall
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeRadioButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.view.LoadingSuccessState

/**
 * Why this is being reported: one row per [ReportReason], picked before it is sent.
 *
 * A radio group rather than six buttons that each file on touch. Filing a report is not undoable
 * from here, and the labels are close enough to one another that a mis-tap is a plausible way to
 * reach the wrong one; a selection that a second, deliberate press confirms costs one tap and
 * removes that whole class of mistake. It also gives each row room for a line of scope underneath
 * its label — see [descriptionRes] — which six tap-to-send rows have nowhere to put.
 *
 * The button says what it will do, because that differs by row: [ReportReason.Other] opens a
 * second step and everything else submits outright. What the flow then does with the answer is
 * still the flow's business — this screen reports a choice and nothing more, which is what keeps
 * the rows uniform.
 *
 * The nav control is a ✕ in the leading slot, asked for rather than inferred. [AppBarWithTitle]
 * can resolve ✕ against ← on its own, but only from the depth of the stack the screen sits in, and
 * report is pushed over the chat it was opened from — so at this step, which is the flow's first
 * and leaves it outright, that reading comes out a ←. Leading rather than trailing because the
 * route is a fullscreen modal; the trailing ✕ belongs to the bottom sheets.
 */
@Composable
internal fun ReasonSelectionContent(
    onChoose: (ReportReason) -> Unit,
    onNavigateUp: () -> Unit,
    // The flow stays up until the report is acknowledged, so the button carries the send itself:
    // the wait, then the checkmark that closing used to swallow.
    progress: LoadingSuccessState = LoadingSuccessState(),
) {
    // Saved, so stepping forward to the details screen and back again doesn't lose the pick that
    // got you there.
    var selected by rememberSaveable { mutableStateOf<ReportReason?>(null) }

    // This screen is always the first step of the report flow, so its nav control leaves the flow
    // rather than stepping back within it. AppBarWithTitle cannot infer that: it decides from the
    // outer stack depth, and report is pushed over the chat rather than opened as the sheet's
    // first entry. Declaring it here puts the close on the trailing edge, where the app's other
    // dismiss controls sit.
    CompositionLocalProvider(LocalFlowDismissStyle provides FlowDismissStyle.Close) {
        CodeScaffold(
            topBar = {
                AppBarWithTitle(
                    title = stringResource(R.string.title_report),
                    titleAlignment = Alignment.CenterHorizontally,
                    onBackIconClicked = onNavigateUp,
                )
            },
            bottomBar = {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("action_report_reason_continue")
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .navigationBarsPadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    text = if (selected == ReportReason.Other) {
                        stringResource(com.flipcash.core.R.string.action_next)
                    } else {
                        stringResource(R.string.action_submitReport)
                    },
                    // `progress.isIdle` as well as the pick: `CodeButton` reads its content
                    // colour and its clickability from `enabled`, not from `isLoading`/
                    // `isSuccess`. Left enabled, the Filled button stays white, which hides a
                    // checkmark drawn in white and keeps taking presses through the send.
                    enabled = selected != null && progress.isIdle,
                    isLoading = progress.loading,
                    isSuccess = progress.success,
                    onClick = { selected?.let(onChoose) },
                )
            },
        ) { padding ->
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .verticalScrollStateGradient(
                        scrollState = scrollState,
                        color = CodeTheme.colors.background,
                        isLongGradient = true,
                    )
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(top = CodeTheme.dimens.grid.x4),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.subtitle_report),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CodeTheme.dimens.grid.x4)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                ) {
                    for (reason in ReportReason.entries) {
                        ReportReasonRow(
                            modifier = Modifier.testTag("action_report_reason_${reason.name}"),
                            label = stringResource(reason.labelRes),
                            description = stringResource(reason.descriptionRes),
                            selected = selected == reason,
                            onSelect = { selected = reason },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One choice: a radio, its label, and the line of scope under it.
 *
 * Built here rather than on [com.getcode.ui.components.ChoiceRow], which is a card with an icon
 * and a single line and no notion of being selected. The card shape and [White05] fill are taken
 * from it so the two read as the same family.
 *
 * The whole row is the target — [selectable] on the row, `null` on the radio — so the radio is a
 * state indicator rather than a second, smaller thing to hit.
 */
@Composable
private fun ReportReasonRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.extraSmall)
            .background(White05)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        CodeRadioButton(
            selected = selected,
            // The row owns the click; a second target inside it would only be a smaller one.
            onClick = null,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Text(
                text = label,
                // 17sp Demi, matching ChoiceRow and the settings rows rather than `textMedium`.
                style = CodeTheme.typography.textMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                color = CodeTheme.colors.textMain,
            )

            Text(
                text = description,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}
