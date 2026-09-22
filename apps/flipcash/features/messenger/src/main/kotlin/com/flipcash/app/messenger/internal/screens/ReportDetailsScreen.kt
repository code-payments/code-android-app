package com.flipcash.app.messenger.internal.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.features.messenger.R
import com.flipcash.reporting.ReportDescription
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import com.getcode.view.LoadingSuccessState

/**
 * The free-text step, reached only by picking [ReportReason.Other].
 *
 * This is a step of its own rather than a second act inside the reason sheet, and the reason is
 * load-bearing: a sheet that sizes itself to its content derives its drag anchors from the height
 * that content reports, then reads those anchors again while placing it. Growing the content to
 * hold a text field therefore changes the number the sheet was measured from, and the two chase
 * each other off the frame clock until Android kills the app for not responding. A step rests at a
 * detent decided before the content is measured, so nothing it holds can feed back into its size —
 * which is also why the field can simply take the room left over and the keyboard inset can be
 * honoured honestly, instead of both being approximated from a fraction of the screen.
 *
 * The heading is two blocks, not one sentence: the ask ("tell us what's wrong") is what the
 * screen wants, and the privacy line is what it offers in return. Run together at one weight they
 * compete, and the part that reassures is the part that gets skipped.
 *
 * The counter runs down rather than up, and the row carries the remaining count as its
 * content description: [AnimatedNumberText] animates per digit, so a screen reader walking the
 * children would otherwise read a number that is mid-flight and briefly wrong.
 */
@Composable
internal fun ReportDetailsContent(
    state: TextFieldState,
    onSubmit: (String) -> Unit,
    // See [ReasonSelectionContent]: the send is shown here rather than behind a closed flow.
    progress: LoadingSuccessState = LoadingSuccessState(),
    onNavigateUp: () -> Unit,
) {
    val keyboard = rememberKeyboardController()
    val typed = state.text.toString()
    val remaining = ReportDescription.MAX_DETAILS_LENGTH - typed.length

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_report),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = onNavigateUp,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(top = CodeTheme.dimens.grid.x1),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                val isOverLimit = remaining < 0
                val textColor by animateColorAsState(
                    if (isOverLimit) CodeTheme.colors.errorText else CodeTheme.colors.textSecondary
                )

                Row(
                    modifier = Modifier
                        .testTag("report_details_counter")
                        .semantics(mergeDescendants = true) {
                            contentDescription = remaining.toString()
                        },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedNumberText(
                        value = remaining.toString(),
                        style = CodeTheme.typography.textSmall,
                        color = textColor,
                    )

                    Text(
                        text = stringResource(com.flipcash.core.R.string.label_characters),
                        style = CodeTheme.typography.textSmall,
                        color = textColor,
                    )

                    AnimatedVisibility(
                        visible = typed.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = stringResource(com.flipcash.core.R.string.label_remaining),
                            style = CodeTheme.typography.textSmall,
                            color = textColor,
                        )
                    }
                }

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("action_report_submit")
                        .navigationBarsPadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3)
                        .imePadding(),
                    text = stringResource(R.string.action_submitReport),
                    enabled = typed.isNotBlank() && !isOverLimit,
                    isLoading = progress.loading,
                    isSuccess = progress.success,
                    onClick = { keyboard.hideIfVisible { onSubmit(typed.trim()) } },
                )
            }
        },
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
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
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                Text(
                    modifier = Modifier.fillMaxWidth(0.80f),
                    text = stringResource(R.string.title_reportDetails),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                )

                Text(
                    modifier = Modifier.fillMaxWidth(0.80f),
                    text = stringResource(R.string.subtitle_reportDetails),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }

            DisplayTextInput(
                state = state,
                placeholder = stringResource(R.string.hint_reportDetails),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_report_details")
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                style = CodeTheme.typography.textMedium,
                placeholderStyle = CodeTheme.typography.textMedium.copy(
                    color = CodeTheme.colors.textTertiary,
                ),
                maxLines = Int.MAX_VALUE,
            )
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
