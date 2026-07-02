package com.getcode.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.ui.core.unboundedClickable

@Composable
fun SearchInput(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    placeholder: String = stringResource(R.string.action_search),
) {
    TextInput(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        leadingIcon = {
            Icon(
                modifier = Modifier.padding(start = CodeTheme.dimens.grid.x3),
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
        },
        placeholder = placeholder,
        placeholderStyle = CodeTheme.typography.textMedium,
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                Icon(
                    modifier = Modifier
                        .testTag("send_search_clear")
                        .unboundedClickable {
                            state.clearText()
                        }.padding(end = CodeTheme.dimens.grid.x3),
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = White50,
                )
            }
        },
        maxLines = 1,
        style = CodeTheme.typography.textMedium,
        shape = CircleShape,
    )
}