package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
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
    val details = rememberTextFieldState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_report),
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
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
