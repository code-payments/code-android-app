package com.flipcash.app.messenger.internal.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flipcash.features.messenger.R
import com.flipcash.reporting.ReportDescription
import com.flipcash.reporting.ReportReason
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ChoiceRow
import com.getcode.ui.components.TextInput
import com.getcode.ui.theme.CodeButton

/**
 * Why this is being reported.
 *
 * One row per [ReportReason], drawn as [MuteChatSheet] draws its rows. Every row but the last is
 * the whole interaction: the tap is the answer, so a report is two taps from anywhere it can be
 * started. [ReportReason.Other] is the exception, and it is why this sheet has a second state at
 * all — a list that cannot say anything outside its own six words is a list people work around by
 * picking the nearest wrong one, which is worse than no taxonomy.
 *
 * The details field does not appear beside the rows, because a field standing open next to six
 * one-tap choices reads as required. It replaces them, after the choice that needs it.
 *
 * Because it replaces them, the two states are one sheet that changes shape rather than two
 * sheets: the bar and the subtitle are fixed, and only the region under them animates, so the
 * height change reads as the same surface resizing. The leading back arrow and the rightward
 * slide are the same fact told twice — "Something Else" is a step forward, and it is reversible.
 * Going back keeps what was typed, since [details] is remembered above the swap; a breadcrumb
 * that discarded the draft would be a trapdoor.
 *
 * What the sheet hands back is the reason and the raw details; assembling the two into the
 * contract's single `description` string is [ReportDescription]'s job, in shared code, so that
 * both platforms send the same bytes. Do not format anything here.
 *
 * @param onSubmit called once, with the chosen reason and the details the user typed, or `null`
 * where the reason needed none.
 */
@Composable
internal fun ReportSheet(
    onSubmit: (ReportReason, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var describing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val details = rememberTextFieldState()

    // How tall the field gets when it is expanded, taken from the window rather than from the
    // space left over after laying the sheet out. Measuring for it is what the obvious version of
    // this does, and it deadlocks: a wrap-content sheet derives its drag anchors from the height
    // its content reports, and the anchors are read again while that content is being placed, so
    // a field whose height came from the measurement changes the number it was computed from. The
    // loop never settles and the main thread spins until Android kills the app. A fraction of the
    // screen is a constant across the whole layout pass, so it cannot feed back. The sheet's own
    // detent caps the result, which is what makes this "as tall as the sheet allows" rather than
    // an exact height this file would otherwise have to keep in step with the bar and the button.
    val expandedFieldHeight = CodeTheme.dimens.screenHeight * 0.65f

    // Both of these are steps, so the system gesture has to agree with the controls on screen, and
    // unwind them in the order they were entered. Without this, back would dismiss the whole sheet
    // from a state the user can see a way out of.
    BackHandler(enabled = describing) {
        if (expanded) expanded = false else describing = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AppBarWithTitle(
            title = {
                AppBarDefaults.Title(
                    text = stringResource(R.string.title_report),
                    style = CodeTheme.typography.screenTitle,
                )
            },
            titleAlignment = Alignment.CenterHorizontally,
            // Built by hand rather than through the `onBackIconClicked` overload, which resolves
            // back-vs-close from the ambient navigator. Here the answer is the sheet's own step,
            // not its depth in whatever stack opened it: ✕ always closes the report, and ← only
            // exists in the second step.
            leftIcon = {
                if (describing) {
                    AppBarDefaults.UpNavigation(
                        modifier = Modifier.testTag("action_report_back"),
                        onClick = { describing = false; expanded = false },
                    )
                }
            },
            rightContents = { AppBarDefaults.Close(onClick = onDismiss) },
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            text = stringResource(R.string.subtitle_report),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        // The swap is instantaneous, and it has to be. This sheet sizes itself to its content:
        // it writes the height it measured into its own state, and that write recomputes the drag
        // detents. Content whose height is being animated therefore re-enters measurement on every
        // pass, and because the loop runs off the layout pass rather than the frame clock, it
        // starves the clock that would have finished the animation — the app spins at 100% and
        // Android kills it. Any cross-fade, slide or size transition over these two steps brings
        // that back, so the swap stays a plain `if`.
        //
        // The morph is still there; it is just the sheet's own. Swapping the content changes the
        // measured height once, to a value that does not move, and the sheet animates its offset
        // from the old detent to the new one. What the user sees is one surface changing size,
        // which is what the two steps being one sheet was supposed to convey.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x4, bottom = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            if (describing) {
                val typed = details.text.toString()
                TextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_report_details"),
                    state = details,
                    placeholder = stringResource(R.string.hint_reportDetails),
                    // Opens at the size of the answer it wants. One line invites one line,
                    // and "Something Else" is the row that exists because one line was not
                    // enough. It then grows with what is typed, up to a point past which a
                    // sheet this tall is worse than a scrollbar — that point is what the
                    // expand control is for.
                    minLines = 3,
                    maxLines = if (expanded) Int.MAX_VALUE else 8,
                    // Given to the box around the field rather than as `minHeight`, which
                    // sets a floor on the whole decorated row. The row centres that box in
                    // itself, so a floor there leaves the text and the placeholder floating in
                    // the middle of a screen-tall border; sizing the box makes the row wrap to
                    // it and the text start where the border does.
                    textModifier = if (expanded) Modifier.height(expandedFieldHeight)
                    else Modifier,
                    // Top-aligned so the placeholder sits where the first typed character
                    // will, rather than centred in a box three lines tall — and so the
                    // expand control rides the top edge instead of the vertical middle.
                    textFieldAlignment = Alignment.TopStart,
                    contentAlignment = Alignment.Top,
                    contentPadding = PaddingValues(CodeTheme.dimens.grid.x2),
                    trailingIcon = {
                        IconButton(
                            modifier = Modifier.testTag("action_report_expand"),
                            onClick = { expanded = !expanded },
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Outlined.CloseFullscreen
                                else Icons.Outlined.OpenInFull,
                                contentDescription = stringResource(
                                    if (expanded) R.string.action_collapseReportDetails
                                    else R.string.action_expandReportDetails
                                ),
                                tint = CodeTheme.colors.textSecondary,
                                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
                            )
                        }
                    },
                    // Clipped rather than rejected: the cap is the contract's, not a rule the user
                    // broke, and a field that stops accepting characters is clearer than an error
                    // about a limit nobody was told about.
                    inputTransformation = InputTransformation {
                        if (length > ReportDescription.MAX_DETAILS_LENGTH) revertAllChanges()
                    },
                )
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("action_submit_report"),
                    text = stringResource(R.string.action_submitReport),
                    // Picking "Something Else" and then saying nothing leaves the report with less
                    // information than any other row would have carried, so the button waits.
                    enabled = typed.isNotBlank(),
                    onClick = { onSubmit(ReportReason.Other, typed.trim()) },
                )
            } else {
                for (reason in ReportReason.entries) {
                    ChoiceRow(
                        modifier = Modifier.testTag("action_report_reason_${reason.name}"),
                        label = stringResource(reason.labelRes),
                        icon = rememberVectorPainter(Icons.Outlined.Flag),
                        onClick = {
                            if (reason == ReportReason.Other) describing = true
                            else onSubmit(reason, null)
                        },
                    )
                }
            }
        }

    }
}
