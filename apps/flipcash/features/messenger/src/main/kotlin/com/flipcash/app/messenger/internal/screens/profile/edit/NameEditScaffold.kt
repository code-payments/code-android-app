package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController

/**
 * The single-field name editor the group's title uses, laid out as NameEntryScreen lays out the
 * same edit: the bar carries the back control and no title, and the heading sits inside the
 * scaffold above the field, so it scrolls and insets with the content rather than floating in a
 * bar over it. A payment request's name reuses it so both edits look and behave alike.
 *
 * [remaining] shows a counter once fewer than [ChatTitle.COUNTER_THRESHOLD] characters are left.
 */
@Composable
internal fun NameEditScaffold(
    heading: String,
    placeholder: String,
    field: TextFieldState,
    buttonText: String,
    canSubmit: Boolean,
    remaining: Int,
    capitalization: KeyboardCapitalization,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
) {
    val keyboard = rememberKeyboardController()
    val submit = { if (canSubmit) keyboard.hideIfVisible(onSubmit) }

    Column {
        AppBarWithTitle(
            onBackIconClicked = { keyboard.hideIfVisible(onBack) },
        )
        CodeScaffold(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            topBar = {
                Text(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(
                            top = CodeTheme.dimens.grid.x2,
                            bottom = CodeTheme.dimens.inset,
                        ),
                    text = heading,
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    // Only once the end is near, so it reads as a warning and not as furniture.
                    if (remaining < ChatTitle.COUNTER_THRESHOLD) {
                        Text(
                            modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x3),
                            text = stringResource(R.string.label_groupNameRemaining, remaining),
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary,
                        )
                    }
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = CodeTheme.dimens.grid.x3),
                        text = buttonText,
                        enabled = canSubmit,
                        isLoading = isLoading,
                        isSuccess = isSuccess,
                        onClick = submit,
                    )
                }
            },
        ) { padding ->
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.padding(padding)) {
                DisplayTextInput(
                    state = field,
                    placeholder = placeholder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        capitalization = capitalization,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { submit() },
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}
