package com.flipcash.app.currency.internal.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.features.currency.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.ui.components.TextInput

@Composable
internal fun SearchBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    TextInput(
        modifier = modifier
            .padding(horizontal = CodeTheme.dimens.grid.x3),
        state = state,
        leadingIcon = {
            Icon(
                modifier = Modifier.padding(start = CodeTheme.dimens.grid.x1),
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = White50,
            )
        },
        placeholder = stringResource(id = R.string.subtitle_searchCurrencies),
        placeholderStyle = CodeTheme.typography.textMedium,
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(
                    onClick = {
                        state.clearText()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = White50,
                    )
                }
            }
        },
        maxLines = 1,
        style = CodeTheme.typography.textMedium,
        shape = CodeTheme.shapes.small
    )
}