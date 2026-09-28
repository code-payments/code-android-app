package com.flipcash.app.messenger.internal.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.flipcash.app.messenger.internal.screens.components.ChatTopEdge
import com.flipcash.features.messenger.R
import com.getcode.libs.emojis.reactions.EmojiCatalogEntry
import com.getcode.libs.emojis.reactions.EmojiPickerModel
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.AllowSheetExpansionWhenScrollable
import com.getcode.ui.utils.LocalSheetOverhang
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The full emoji picker (decision 5: opens as `ChatStep.ReactionPicker`, a [com.getcode.ui.navigation.HalfSheet]).
 * A glass search capsule floating over the grid (not auto-focused — iOS's picker doesn't auto-focus
 * its search field either), then every catalog category in a fixed 7-column grid behind a floating
 * glass category bar, all through one scrollable list so [AllowSheetExpansionWhenScrollable] sees a
 * single scroll container. Mirrors iOS `EmojiPickerSheet` (node 9768:1402, category bar node
 * 9768:1624). There is no title — iOS's picker doesn't have one either.
 *
 * The grid shows base emoji only. Long-pressing one with skin tones ([tones]) offers them.
 */
@Composable
internal fun ReactionPickerSheet(
    searchState: TextFieldState,
    sections: List<EmojiPickerModel.Section>,
    tones: Map<String, List<String>>,
    loaded: Boolean,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val gridState = rememberLazyListState()
    AllowSheetExpansionWhenScrollable(gridState)

    val query = searchState.text.toString()
    val hazeState = rememberHazeState()
    val glass = rememberLiquidGlass()

    Box(modifier = Modifier.fillMaxWidth()) {
        when {
            !loaded -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = SEARCH_FIELD_INSET),
                    contentAlignment = Alignment.Center,
                ) {
                    CodeCircularProgressIndicator()
                }
            }

            sections.isEmpty() && query.isNotBlank() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = SEARCH_FIELD_INSET),
                    contentAlignment = Alignment.Center,
                ) {
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
                    tones = tones,
                    query = query,
                    gridState = gridState,
                    hazeState = hazeState,
                    glass = glass,
                    onSelected = onSelected,
                )
            }
        }

        // Over the grid rather than above it, so the emoji scroll up under the glass.
        SearchField(
            state = searchState,
            hazeState = hazeState,
            glass = glass,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = SEARCH_FIELD_TOP),
        )
    }
}

/**
 * The glass both floating capsules are frosted with: a Haze blur of the grid beneath — Android's
 * analogue of iOS's `.glassEffect` (see [com.flipcash.app.core.ui.NavigationBar] for the same
 * pattern).
 */
@Composable
private fun rememberLiquidGlass(): HazeBlurStyle {
    val backdrop = CodeTheme.colors.background
    return remember(backdrop) {
        HazeBlurStyle {
            blurRadius(32.dp)
            backgroundColor(backdrop)
            colorEffects(listOf(HazeColorEffect.tint(backdrop.copy(alpha = 0.72f))))
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
    tones: Map<String, List<String>>,
    query: String,
    gridState: LazyListState,
    hazeState: HazeState,
    glass: HazeBlurStyle,
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
    val scope = rememberCoroutineScope()

    // Sized in dp rather than sp: the grid is a fixed 7 columns, so a larger font scale would only
    // crowd the cells, and one shared style keeps each cell from resolving its own.
    val density = LocalDensity.current
    val emojiStyle = remember(density) { TextStyle(fontSize = with(density) { EMOJI_SIZE.toSp() }) }

    // The emoji whose tone choice is open, if any.
    var toneTarget by remember { mutableStateOf<String?>(null) }
    val showTones = remember { { emoji: String -> toneTarget = emoji } }
    val hideTones = remember { { toneTarget = null } }

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

    // An expandable sheet is laid out at its expanded height and slid down, so its bottom sits off
    // screen until it's fully up. Lifting the bar by that overhang keeps it hugging the screen at
    // either detent and mid-drag; it's read at placement so it moves in the same frame as the sheet.
    // Collapsing past the half detent would lift it up into the search field, so the lift stops
    // once the bar reaches the top of the grid, 12dp under the field, and from there the bar ducks
    // off screen with the sheet.
    val sheetOverhang = LocalSheetOverhang.current
    var gridHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        LazyColumn(
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { gridHeightPx = it.height }
                .fadeUnderSearchField(CodeTheme.colors.surface)
                .hazeSource(hazeState),
            contentPadding = PaddingValues(
                top = SEARCH_FIELD_INSET,
                bottom = if (showBar) CATEGORY_BAR_HEIGHT + 24.dp else 16.dp,
            ),
        ) {
            items(
                rows,
                key = { row ->
                    when (row) {
                        is GridRow.Header -> "header-${row.sectionId}"
                        is GridRow.Emojis -> "row-${row.sectionId}-${row.entries.first().emoji}"
                    }
                },
                contentType = { row -> if (row is GridRow.Header) HEADER_CONTENT else ROW_CONTENT },
            ) { row ->
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
                                EmojiCell(
                                    entry = entry,
                                    tones = tones[entry.emoji],
                                    showingTones = toneTarget == entry.emoji,
                                    style = emojiStyle,
                                    onSelected = onSelected,
                                    onShowTones = showTones,
                                    onHideTones = hideTones,
                                )
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
                glass = glass,
                onSelect = { id ->
                    pendingCategory = id
                    val index = sectionStartIndex[id] ?: return@CategoryBar
                    scope.launch { gridState.animateScrollToItem(index) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset {
                        val maxLift = gridHeightPx -
                            (CATEGORY_BAR_HEIGHT + CATEGORY_BAR_BOTTOM_GAP).roundToPx()
                        val lift = sheetOverhang().roundToInt().coerceIn(0, maxLift.coerceAtLeast(0))
                        IntOffset(0, -lift)
                    }
                    .padding(bottom = CATEGORY_BAR_BOTTOM_GAP),
            )
        }
    }
}

/**
 * One grid cell. `combinedClickable` with no interaction source is a plain modifier node that
 * creates its source only once pressed; the composed `unboundedClickable` it replaces remembered a
 * source and a ripple in each of the ~50 cells on screen, on every row the fling brought in.
 */
@Composable
private fun RowScope.EmojiCell(
    entry: EmojiCatalogEntry,
    tones: List<String>?,
    showingTones: Boolean,
    style: TextStyle,
    onSelected: (String) -> Unit,
    onShowTones: (String) -> Unit,
    onHideTones: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 41.dp)
            .combinedClickable(
                interactionSource = null,
                indication = CellRipple,
                onLongClickLabel = if (tones != null) stringResource(R.string.action_chooseSkinTone) else null,
                onLongClick = if (tones != null) {
                    { onShowTones(entry.emoji) }
                } else {
                    null
                },
                onClick = { onSelected(entry.emoji) },
            )
            .semantics { contentDescription = entry.name },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = entry.emoji, style = style)
        if (showingTones && tones != null) {
            TonePicker(
                tones = tones,
                style = style,
                onSelected = { tone ->
                    onHideTones()
                    onSelected(tone)
                },
                onDismiss = onHideTones,
            )
        }
    }
}

private val CellRipple = ripple(bounded = false, radius = 24.dp)

/**
 * The skin tones a long-press offers, in a popup above the pressed cell (below it when there's no
 * room above): the base, then each tone. Five tones fit one row with the base; the 25 of a
 * two-person emoji go in rows of five under it.
 */
@Composable
private fun TonePicker(
    tones: List<String>,
    style: TextStyle,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val provider = remember(density) {
        TonePickerPosition(
            gap = with(density) { 8.dp.roundToPx() },
            margin = with(density) { 8.dp.roundToPx() },
        )
    }
    val rows = remember(tones) {
        if (tones.size <= TONE_ROW + 1) listOf(tones) else listOf(tones.take(1)) + tones.drop(1).chunked(TONE_ROW)
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(ToneSurface)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (row in rows) {
                Row {
                    for (tone in row) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { onSelected(tone) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(text = tone, style = style)
                        }
                    }
                }
            }
        }
    }
}

/** Centred over the anchor, [gap] above it (below when it won't fit), never within [margin] of the window's edges. */
private class TonePickerPosition(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = if (above >= margin) above else anchorBounds.bottom + gap
        return IntOffset(x, y)
    }
}

/** The quick reaction strip's surface, so the two long-press popups match. */
private val ToneSurface = Color(0xFF303030)
private const val TONE_ROW = 5

/**
 * The floating jump bar to each category, a glass capsule over the grid (node 9768:1624), frosted
 * with [rememberLiquidGlass] like the search field. The selected category sits on
 * its own capsule indicator, which springs between slots. As on iOS, the indicator can be pressed and
 * dragged across the bar; [onSelect] fires only once it's let go over a category, so a plain tap is
 * the same gesture with no travel.
 */
@Composable
private fun CategoryBar(
    sections: List<EmojiPickerModel.Section>,
    selected: String?,
    hazeState: HazeState,
    glass: HazeBlurStyle,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val selectedIndex = sections.indexOfFirst { it.id == selected }.coerceAtLeast(0)

    // Where the finger is along the bar while it drags the indicator, null otherwise.
    var dragX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .height(CATEGORY_BAR_HEIGHT)
            .clip(CircleShape)
            .hazeBlur(HazeInput.Sources(hazeState), glass),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp)
                .pointerInput(sections) {
                    val count = sections.size
                    fun indexAt(x: Float): Int =
                        (x / (size.width.toFloat() / count)).toInt().coerceIn(0, count - 1)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragX = down.position.x
                        var hovered = indexAt(down.position.x)
                        var lastX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                lastX = change.position.x
                                change.consume()
                                break
                            }
                            change.consume()
                            lastX = change.position.x
                            dragX = lastX
                            val index = indexAt(lastX)
                            if (index != hovered) {
                                hovered = index
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                        dragX = null
                        onSelect(sections[indexAt(lastX)].id)
                    }
                },
        ) {
            val barWidth = constraints.maxWidth.toFloat()
            val slot = barWidth / sections.size.coerceAtLeast(1)
            val indicatorWidth = with(density) { minOf(slot, (INDICATOR_HEIGHT + 6.dp).toPx()) }
            val restingX = slot * (selectedIndex + 0.5f)
            val targetX = dragX?.coerceIn(slot / 2, barWidth - slot / 2) ?: restingX
            // Follow the finger 1:1 while dragging; only the press, release and selection animate.
            val indicatorX by animateFloatAsState(
                targetValue = targetX,
                animationSpec = if (dragX != null) snap() else settleSpring(),
                label = "categoryIndicatorX",
            )
            val indicatorScale by animateFloatAsState(
                targetValue = if (dragX == null) 1f else 1.15f,
                animationSpec = settleSpring(),
                label = "categoryIndicatorScale",
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((indicatorX - indicatorWidth / 2).roundToInt(), 0) }
                    .size(width = with(density) { indicatorWidth.toDp() }, height = INDICATOR_HEIGHT)
                    .graphicsLayer {
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                    }
                    .clip(CircleShape)
                    .background(CodeTheme.colors.textMain.copy(alpha = 0.14f)),
            )

            Row(modifier = Modifier.fillMaxSize()) {
                sections.forEachIndexed { index, section ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .semantics {
                                contentDescription = section.title
                                this.selected = index == selectedIndex
                                onClick {
                                    onSelect(section.id)
                                    true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = section.entries.firstOrNull()?.emoji.orEmpty(), fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

// iOS's `.spring(duration: 0.35, bounce: 0.2)`.
private fun <T> settleSpring() = spring<T>(dampingRatio = 0.8f, stiffness = 320f)

private val CATEGORY_BAR_HEIGHT = 45.dp
private val CATEGORY_BAR_BOTTOM_GAP = 4.dp
private val INDICATOR_HEIGHT = 38.dp

private val SEARCH_FIELD_TOP = 16.dp
private val SEARCH_FIELD_HEIGHT = 44.dp

/** Where the grid's first row starts: under the floating search field, with a 12dp gap. */
private val SEARCH_FIELD_INSET = SEARCH_FIELD_TOP + SEARCH_FIELD_HEIGHT + 12.dp

/** How far the grid takes to fade in. It ends where the first row rests, so nothing is dimmed at rest. */
private val FADE_LENGTH = 32.dp

private val EMOJI_SIZE = 34.dp

private const val HEADER_CONTENT = "header"
private const val ROW_CONTENT = "row"

/**
 * Fades the grid out towards the top, so an emoji scrolling up dissolves as it passes under the
 * search field instead of being cut by the capsule's bottom edge — Android's stand-in for iOS's soft
 * scroll edge. Clear at [SEARCH_FIELD_INSET], where the first row rests, [surface] at full strength
 * [FADE_LENGTH] above it and everything above that, eased with [ChatTopEdge]'s ramp.
 *
 * A scrim in the sheet's own colour rather than a `DstIn` alpha mask: the sheet is an opaque
 * [surface], so the two look the same, but the mask needs an offscreen layer the size of the whole
 * grid, re-rendered on every scrolled frame, where the scrim draws one strip.
 *
 * Applied outside [hazeSource], so the field's blur still samples the emoji at full strength.
 */
private fun Modifier.fadeUnderSearchField(surface: Color): Modifier = drawWithCache {
    val end = SEARCH_FIELD_INSET.toPx()
    val start = end - FADE_LENGTH.toPx()
    val stops = Array(ChatTopEdge.RampSamples.size) { index ->
        val t = ChatTopEdge.RampSamples[index]
        ((start + t * (end - start)) / end) to surface.copy(alpha = 1f - ChatTopEdge.eased(t))
    }
    val scrim = Brush.verticalGradient(colorStops = stops, startY = 0f, endY = end)
    onDrawWithContent {
        drawContent()
        drawRect(brush = scrim, size = Size(size.width, end))
    }
}

/**
 * The search capsule, frosted with the same glass as the category bar. The dark fill over the blur
 * is the field's look at rest, when nothing is under it yet.
 */
@Composable
private fun SearchField(
    state: TextFieldState,
    hazeState: HazeState,
    glass: HazeBlurStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(SEARCH_FIELD_HEIGHT)
            .clip(CircleShape)
            .hazeBlur(HazeInput.Sources(hazeState), glass)
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
