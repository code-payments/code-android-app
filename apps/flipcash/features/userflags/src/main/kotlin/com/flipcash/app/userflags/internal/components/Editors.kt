package com.flipcash.app.userflags.internal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.userflags.FieldEditor
import com.flipcash.app.userflags.internal.EditableEntry
import com.flipcash.app.userflags.internal.UserFlagsViewModel
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.TextInput
import androidx.compose.foundation.clickable
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCheckbox
import com.getcode.ui.theme.CodeRadioButton

@Composable
internal fun <T> TextInputContent(
    editor: FieldEditor.TextInput<T>,
    entry: EditableEntry<T>,
    dispatch: (UserFlagsViewModel.Event) -> Unit,
    onDismiss: () -> Unit,
) {
    val textFieldState = remember(entry) {
        TextFieldState(initialText = entry.formattedEditValue())
    }

    TextInput(
        modifier = Modifier
            .fillMaxWidth(),
        state = textFieldState,
        placeholder = stringResource(entry.field.hint),
        contentPadding = PaddingValues(
            horizontal = CodeTheme.dimens.inset,
            vertical = CodeTheme.dimens.grid.x3,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = editor.keyboard),
        maxLines = 1,
        inputTransformation = entry.field.inputTransformation,
        outputTransformation = entry.field.outputTransformation,
    )

    val parsed = remember(textFieldState.text) {
        editor.parse(textFieldState.text.toString())
    }

    Spacer(Modifier.height(CodeTheme.dimens.grid.x3))
    CodeButton(
        onClick = {
            parsed?.let { dispatch(UserFlagsViewModel.Event.UpdateFlag(entry.field, it)) }
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
        text = "Apply",
        enabled = parsed != null,
    )
}

@Suppress("UNCHECKED_CAST")
@Composable
internal fun <T> MultiSelectContent(
    editor: FieldEditor.MultiSelect<T>,
    entry: EditableEntry<List<T>>,
    dispatch: (UserFlagsViewModel.Event) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(entry) {
        mutableStateListOf<T>().apply {
            addAll(entry.resolved.effectiveValue)
        }
    }

    editor.options.forEach { (label, value) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (value in selected) selected.remove(value) else selected.add(value)
                }
                .padding(vertical = CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            CodeCheckbox(
                checked = value in selected,
                onCheckedChange = { checked ->
                    if (checked) selected.add(value) else selected.remove(value)
                },
            )
            Text(
                text = label,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain
            )
        }
    }

    Spacer(Modifier.height(CodeTheme.dimens.grid.x3))
    CodeButton(
        onClick = {
            dispatch(UserFlagsViewModel.Event.UpdateFlag(entry.field, selected.toList()))
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
        text = "Apply"
    )
}

@Composable
internal fun <T> SingleSelectContent(
    editor: FieldEditor.SingleSelect<T>,
    entry: EditableEntry<T>,
    dispatch: (UserFlagsViewModel.Event) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(entry) { mutableStateOf(entry.resolved.effectiveValue) }

    editor.options.forEach { (label, value) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selected = value }
                .padding(vertical = CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            CodeRadioButton(
                selected = selected == value,
                onClick = { selected = value },
            )
            Text(
                text = label,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain
            )
        }
    }

    Spacer(Modifier.height(CodeTheme.dimens.grid.x3))
    CodeButton(
        onClick = {
            dispatch(UserFlagsViewModel.Event.UpdateFlag(entry.field, selected))
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
        text = "Apply"
    )
}