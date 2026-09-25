package com.flipcash.app.messenger.internal.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.features.messenger.R
import com.getcode.libs.emojis.reactions.EmojiCatalogEntry
import com.getcode.libs.emojis.reactions.EmojiPickerModel
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.AllowSheetExpansionWhenScrollable
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The full emoji picker (decision 5: opens as `ChatStep.ReactionPicker`, a [com.getcode.ui.navigation.HalfSheet]).
 * A search capsule (not auto-focused — iOS's picker doesn't auto-focus its search field either),
 * then every catalog category in a fixed 7-column grid behind a floating glass category bar, all
 * through one scrollable list so [AllowSheetExpansionWhenScrollable] sees a single scroll container.
 * Mirrors iOS `EmojiPickerSheet` (node 9768:1402, category bar node 9768:1624). There is no title —
 * iOS's picker doesn't have one either.
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
        SearchField(
            state = searchState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp),
        )

        val query by produceState(initialValue = "") {
            snapshotFlow { searchState.text.toString() }
                .distinctUntilChanged()
                .debounce(150)
                .collect { value = it }
        }

        var hasLoadedOnce by remember { mutableStateOf(false) }
        val sections by produceState(initialValue = emptyList(), query, loadSections) {
            value = loadSections(query)
            hasLoadedOnce = true
        }

        when {
            !hasLoadedOnce -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CodeCircularProgressIndicator()
                }
            }

            sections.isEmpty() && query.isNotBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.title_noSearchResults, query),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            else -> {
                EmojiGrid(
                    sections = sections,
                    query = query,
                    gridState = gridState,
                    onSelected = onSelected,
                )
            }
        }
    }
}

private const val COLUMNS = 7

/** One flat entry in the grid's backing list, keyed so a category-bar tap can find a section's row. */
private sealed interface GridRow {
    val sectionId: String

    data class Header(override val sectionId: String, val title: String) : GridRow
    data class Emojis(override val sectionId: String, val entries: List<EmojiCatalogEntry>) : GridRow
}

@Composable
private fun EmojiGrid(
    sections: List<EmojiPickerModel.Section>,
    query: String,
    gridState: LazyListState,
    onSelected: (String) -> Unit,
) {
    // A LazyColumn of hand-chunked rows rather than LazyVerticalGrid: AllowSheetExpansionWhenScrollable
    // only reads a LazyListState, and one flat list keeps every section's header + rows in the same
    // scroll container it watches. It also fills the sheet as it expands to 92.5% (no heightIn cap).
    val rows = remember(sections) {
        buildList {
            for (section in sections) {
                if (section.id != EmojiPickerModel.SEARCH_RESULTS_ID) {
                    add(GridRow.Header(section.id, section.title))
                }
                for (chunk in section.entries.chunked(COLUMNS)) {
                    add(GridRow.Emojis(section.id, chunk))
                }
            }
        }
    }

    // The flat-list index a category-bar tap scrolls to — the header when the section has one
    // (every catalog category does; search results never show the bar at all).
    val sectionStartIndex = remember(rows) {
        val map = LinkedHashMap<String, Int>()
        rows.forEachIndexed { index, row -> map.putIfAbsent(row.sectionId, index) }
        map
    }

    val showBar = query.isBlank() && sections.size > 1
    val hazeState = rememberHazeState()
    val scope = rememberCoroutineScope()

    // A category chosen from the bar whose section the grid is still scrolling to — the indicator
    // holds on it so it doesn't pass through every category the grid scrolls over on the way
    // (mirrors iOS's `pendingCategory`).
    var pendingCategory by remember { mutableStateOf<String?>(null) }
    val visibleCategory by remember {
        derivedStateOf {
            val visibleIndex = gridState.firstVisibleItemIndex
            sectionStartIndex.entries
                .filter { it.value <= visibleIndex }
                .maxByOrNull { it.value }
                ?.key
        }
    }
    LaunchedEffect(visibleCategory) {
        if (visibleCategory == pendingCategory) pendingCategory = null
    }
    val selectedCategory = pendingCategory ?: visibleCategory ?: sections.firstOrNull()?.id

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        LazyColumn(
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = if (showBar) 45.dp + 24.dp else 16.dp,
            ),
        ) {
            items(rows, key = { row ->
                when (row) {
                    is GridRow.Header -> "header-${row.sectionId}"
                    is GridRow.Emojis -> "row-${row.sectionId}-${row.entries.first().emoji}"
                }
            }) { row ->
                when (row) {
                    is GridRow.Header -> {
                        Text(
                            text = row.title.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CodeTheme.colors.textMain.copy(alpha = 0.5f),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 20.dp, bottom = 12.dp),
                        )
                    }

                    is GridRow.Emojis -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            for (entry in row.entries) {
                                EmojiCell(entry = entry, onSelected = onSelected)
                            }
                            repeat(COLUMNS - row.entries.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (showBar) {
            CategoryBar(
                sections = sections,
                selected = selectedCategory,
                hazeState = hazeState,
                onSelect = { id ->
                    pendingCategory = id
                    val index = sectionStartIndex[id] ?: return@CategoryBar
                    scope.launch { gridState.animateScrollToItem(index) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun RowScope.EmojiCell(entry: EmojiCatalogEntry, onSelected: (String) -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 41.dp)
            .unboundedClickable { onSelected(entry.emoji) }
            .semantics { contentDescription = entry.name },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = entry.emoji, fontSize = 34.sp)
    }
}

/**
 * The floating jump bar to each category, a glass capsule over the grid (node 9768:1624), frosted
 * with a Haze blur of the grid scrolling beneath it — Android's analogue of iOS's `.glassEffect`
 * (see [com.flipcash.app.core.ui.NavigationBar] for the same pattern). Unlike iOS, there is no
 * drag-across-the-bar gesture — only a tap per slot (a deliberate scope cut; see the chunk report).
 */
@Composable
private fun CategoryBar(
    sections: List<EmojiPickerModel.Section>,
    selected: String?,
    hazeState: HazeState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = CodeTheme.colors.background
    val liquidGlass = HazeBlurStyle {
        blurRadius(32.dp)
        backgroundColor(backdrop)
        colorEffects(listOf(HazeColorEffect.tint(backdrop.copy(alpha = 0.72f))))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .height(45.dp)
            .clip(CircleShape)
            .hazeBlur(HazeInput.Sources(hazeState), liquidGlass),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
        ) {
            for (section in sections) {
                val isSelected = section.id == selected
                val indicatorColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        CodeTheme.colors.textMain.copy(alpha = 0.14f)
                    } else {
                        Color.Transparent
                    },
                    label = "categoryBarIndicator",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(indicatorColor)
                        .unboundedClickable { onSelect(section.id) }
                        .semantics { contentDescription = section.title },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = section.entries.firstOrNull()?.emoji.orEmpty(), fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchField(state: TextFieldState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.26f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            BasicTextField(
                state = state,
                textStyle = CodeTheme.typography.textMedium.copy(color = CodeTheme.colors.textMain),
                cursorBrush = SolidColor(CodeTheme.colors.textMain),
                lineLimits = TextFieldLineLimits.SingleLine,
                decorator = { inner ->
                    if (state.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hint_searchEmoji),
                            style = CodeTheme.typography.textMedium,
                            color = CodeTheme.colors.textSecondary,
                        )
                    }
                    inner()
                },
            )
        }
        if (state.text.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = stringResource(R.string.action_clearEmojiSearch),
                tint = CodeTheme.colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .unboundedClickable { state.clearText() },
            )
        }
    }
}
