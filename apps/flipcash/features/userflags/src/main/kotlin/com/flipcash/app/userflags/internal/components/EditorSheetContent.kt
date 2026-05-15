package com.flipcash.app.userflags.internal.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.userflags.FieldEditor
import com.flipcash.app.userflags.internal.EditableEntry
import com.flipcash.app.userflags.internal.UserFlagsViewModel
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.ui.utils.rememberKeyboardController


@Composable
internal fun <T> FieldEditorSheet(
    entry: EditableEntry<T>,
    onDismiss: () -> Unit,
    dispatch: (UserFlagsViewModel.Event) -> Unit,
) {
    val ime = rememberKeyboardController()
    val dismiss = {
        ime.hideIfVisible { onDismiss() }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = CodeTheme.shapes.extraLarge,
        color = CodeTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(CodeTheme.dimens.grid.x4),
        ) {
            Text(
                text = stringResource(entry.field.label),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            Spacer(Modifier.height(CodeTheme.dimens.grid.x3))

            when (val editor = entry.field.editor) {
                is FieldEditor.SingleSelect -> SingleSelectContent(editor, entry, dispatch, dismiss)
                is FieldEditor.MultiSelect<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    MultiSelectContent(
                        editor = editor as FieldEditor.MultiSelect<Nothing>,
                        entry = entry as EditableEntry<List<Nothing>>,
                        dispatch = dispatch,
                        onDismiss = dismiss,
                    )
                }
                is FieldEditor.TextInput -> TextInputContent(editor, entry, dispatch, dismiss)
            }
        }
    }

    // Close on back press
    BackHandler { dismiss() }
}