package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.getcode.libs.emojis.reactions.EmojiPickerModel
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.TextInput
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.utils.AllowSheetExpansionWhenScrollable
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The full emoji picker (decision 5: opens as `ChatStep.ReactionPicker`, a [com.getcode.ui.navigation.HalfSheet]).
 * A search field (not auto-focused — iOS's picker doesn't auto-focus its search field either),
 * a Frequently Used row, then every catalog category, all through one scrollable grid so
 * [AllowSheetExpansionWhenScrollable] sees a single scroll container.
 */
@Composable
internal fun ReactionPickerSheet(
    loadSections: suspend (query: String) -> List<EmojiPickerModel.Section>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val gridState = rememberLazyListState()
    AllowSheetExpansionWhenScrollable(gridState)

    val searchState = remember { TextFieldState() }

    Column(modifier = Modifier.fillMaxWidth()) {
        AppBarWithTitle(
            title = stringResource(R.string.title_reactorsSheet),
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )

        SearchField(
            state = searchState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x2),
        )

        val query by produceState(initialValue = "") {
            snapshotFlow { searchState.text.toString() }
                .distinctUntilChanged()
                .debounce(150)
                .collect { value = it }
        }

        val sections by produceState(initialValue = emptyList(), query, loadSections) {
            value = loadSections(query)
        }

        EmojiGrid(
            sections = sections,
            gridState = gridState,
            onSelected = onSelected,
        )
    }
}

private const val COLUMNS = 7

@Composable
private fun EmojiGrid(
    sections: List<EmojiPickerModel.Section>,
    gridState: LazyListState,
    onSelected: (String) -> Unit,
) {
    // A LazyColumn of hand-chunked rows rather than LazyVerticalGrid: AllowSheetExpansionWhenScrollable
    // only reads a LazyListState, and one flat list keeps every section's header + rows in the same
    // scroll container it watches.
    LazyColumn(
        state = gridState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        for (section in sections) {
            item(key = "header-${section.id}") {
                Text(
                    text = section.title,
                    fontSize = 13.sp,
                    color = CodeTheme.colors.textSecondary,
                    modifier = Modifier.padding(
                        top = CodeTheme.dimens.grid.x2,
                        bottom = CodeTheme.dimens.grid.x1,
                    ),
                )
            }
            val rows = section.entries.chunked(COLUMNS)
            items(rows, key = { row -> "${section.id}-${row.first().emoji}" }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                ) {
                    for (entry in row) {
                        Text(
                            text = entry.emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .size(44.dp)
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .unboundedClickable { onSelected(entry.emoji) },
                        )
                    }
                    if (row.size < COLUMNS) {
                        Box(modifier = Modifier.width((44 * (COLUMNS - row.size)).dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(state: TextFieldState, modifier: Modifier = Modifier) {
    TextInput(
        modifier = modifier,
        shape = CodeTheme.shapes.medium,
        state = state,
        placeholder = stringResource(R.string.hint_searchEmoji),
        minHeight = CodeTheme.dimens.grid.x8,
        contentPadding = PaddingValues(
            start = 8.dp + CodeTheme.dimens.staticGrid.x2,
            top = 8.dp,
            end = 8.dp + CodeTheme.dimens.staticGrid.x2,
            bottom = 8.dp,
        ),
        maxLines = 1,
        colors = inputColors(
            backgroundColor = CodeTheme.colors.surface,
            textColor = CodeTheme.colors.textMain,
            cursorColor = CodeTheme.colors.textMain,
            placeholderColor = CodeTheme.colors.textSecondary,
        ),
        trailingIcon = state.text.takeIf { it.isNotBlank() }?.let {
            {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "",
                    tint = CodeTheme.colors.textSecondary,
                    modifier = Modifier
                        .padding(end = CodeTheme.dimens.grid.x2)
                        .wrapContentWidth()
                        .size(20.dp)
                        .unboundedClickable { state.clearText() },
                )
            }
        },
    )
}
