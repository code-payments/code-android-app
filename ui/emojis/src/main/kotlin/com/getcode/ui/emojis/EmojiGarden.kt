package com.getcode.ui.emojis

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.EmojiFlags
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import com.getcode.libs.emojis.generated.Category
import com.getcode.libs.emojis.generated.Emoji
import com.getcode.libs.emojis.generated.Emojis
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.ui.core.verticalScrollStateGradient
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun EmojiGarden(onClick: (String) -> Unit) {
    val emojis = remember { Emojis.categorizedNoSkinTones }
    val allEmojis = remember(emojis) {
        emojis.mapValues { it.value.values.toList().flatten() }
    }
    val categories = emojis.keys.toList()
    val usageController = LocalEmojiUsageController.current
    var frequentEmojis by remember { mutableStateOf<List<Emoji>>(emptyList()) }
    val composeScope = rememberCoroutineScope()

    LaunchedEffect(usageController) {
        frequentEmojis = usageController.mostUsedEmojis(includeFiller = false)
            .mapNotNull { em -> allEmojis.values.flatten().firstOrNull { it.unicode == em } }
    }

    val categoryOptions = remember(frequentEmojis, categories) {
        if (frequentEmojis.isNotEmpty()) {
            listOf(Category.FREQUENT) + categories
        } else {
            categories
        }
    }

    var selectedCategory by remember(categoryOptions) { mutableStateOf(categoryOptions.first()) }

    val listState = rememberLazyGridState()

    val categoryHeaderIndexMapping = remember(categoryOptions) {
        var cumulativeIndex = 0
        val mapping = mutableMapOf<Category, Int>()

        // Iterate over each category and accumulate the count
        categoryOptions.forEach { category ->
            mapping[category] = cumulativeIndex
            cumulativeIndex += allEmojis[category]?.count() ?: 0
        }

        mapping
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScrollStateGradient(listState, color = CodeTheme.colors.background),
            columns = GridCells.Adaptive(CodeTheme.dimens.grid.x8),
            contentPadding = PaddingValues(
                top = CodeTheme.dimens.grid.x2,
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
                bottom = CodeTheme.dimens.grid.x10
            ),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            if (frequentEmojis.isNotEmpty()) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "header_${Category.FREQUENT.name}",
                    contentType = "header"
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodeTheme.colors.background)
                            .padding(vertical = CodeTheme.dimens.grid.x2)
                            .zIndex(1f), // Ensure it stays above other items,
                        text = Category.FREQUENT.displayName,
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary
                    )
                }
                items(frequentEmojis) { emoji ->
                    EmojiRender(
                        emoji = emoji.unicode,
                        showBackground = false,
                        onClick = { onClick(emoji.unicode) }
                    )
                }
            }

            emojis.entries.toList().fastForEach { (category, emojis) ->
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "header_${category.name}",
                    contentType = "header"
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodeTheme.colors.background)
                            .padding(vertical = CodeTheme.dimens.grid.x2)
                            .zIndex(1f), // Ensure it stays above other items,
                        text = category.displayName,
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary
                    )
                }
                items(emojis.values.flatten()) { (emoji, _) ->
                    EmojiRender(
                        emoji = emoji,
                        showBackground = false,
                        onClick = { onClick(emoji) }
                    )
                }
            }
        }

        EmojiCategorySelection(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = CodeTheme.dimens.inset)
                .navigationBarsPadding()
                .padding(bottom = CodeTheme.dimens.inset)
                .fillMaxWidth(),
            categories = categoryOptions,
            selectedCategory = selectedCategory,
            onSelection = { category ->
                selectedCategory = category

                // Find the first emoji index for this category
                val targetIndex = categoryHeaderIndexMapping[category]

                if (targetIndex != null && targetIndex >= 0) {
                    composeScope.launch {
                        listState.scrollToItem(targetIndex)
                    }
                }
            }
        )

        LaunchedEffect(frequentEmojis) {
            listState.scrollToItem(0)
        }

        LaunchedEffect(categoryOptions) {
            snapshotFlow { listState.layoutInfo }
                .mapNotNull { layoutInfo ->
                    val visibleItems = layoutInfo.visibleItemsInfo

                    if (visibleItems.isEmpty()) {
                        return@mapNotNull null
                    }

                    val lastHeader = visibleItems
                        .filter { it.key.toString().startsWith("header_") }
                        .minByOrNull { it.index } // Last visible header

                    val lastPassedHeader = if (lastHeader != null) {
                        val headerKey = lastHeader.key.toString().removePrefix("header_")
                        categoryOptions.find { it.name == headerKey }
                    } else {
                        // If no header is visible, find the last header before the first visible item
                        val firstVisibleIndex = visibleItems.first().index
                        (0 until firstVisibleIndex)
                            .mapNotNull { index ->
                                val item = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                                item?.takeIf { it.key.toString().startsWith("header_") }
                            }
                            .maxByOrNull { it.index }
                            ?.let { lastHeaderItem ->
                                val headerKey = lastHeaderItem.key.toString().removePrefix("header_")
                                categoryOptions.find { it.name == headerKey }
                            }
                    }

                    lastPassedHeader
                }
                .distinctUntilChanged()
                .onEach { newCategory ->
                    selectedCategory = newCategory
                }.launchIn(this)
        }
    }
}

@Composable
private fun EmojiCategorySelection(
    modifier: Modifier = Modifier,
    categories: List<Category>,
    selectedCategory: Category,
    onSelection: (Category) -> Unit,
) {
    val itemPositions = remember(categories) { mutableStateMapOf<Int, Float>() }
    val animatedOffset = remember(categories) { Animatable(0f) }

    Surface(
        modifier = modifier
            .wrapContentHeight()
            .background(CodeTheme.colors.surface.copy(0.87f), CodeTheme.shapes.small),
        color = Color.Transparent,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.grid.x1)
                    .offset {
                        IntOffset(
                            x = animatedOffset.value.roundToInt(),
                            y = 0
                        )
                    }
                    .size(CodeTheme.dimens.grid.x7)
                    .padding(vertical = 2.dp)
                    .background(
                        color = CodeTheme.colors.tertiary.copy(0.54f),
                        shape = CodeTheme.shapes.small
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = CodeTheme.dimens.grid.x2,
                        vertical = CodeTheme.dimens.grid.x1,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                categories.fastForEachIndexed { index, category ->
                    val painter = when (category) {
                        Category.SMILEYSPEOPLE -> rememberVectorPainter(Icons.Outlined.SentimentSatisfied)
                        Category.ANIMALSNATURE -> rememberVectorPainter(Icons.Outlined.Pets)
                        Category.FOODDRINK -> rememberVectorPainter(Icons.Outlined.LunchDining)
                        Category.TRAVELPLACES -> rememberVectorPainter(Icons.Outlined.House)
                        Category.ACTIVITIES -> rememberVectorPainter(Icons.Outlined.SportsBasketball)
                        Category.OBJECTS -> rememberVectorPainter(Icons.Outlined.EmojiObjects)
                        Category.SYMBOLS -> rememberVectorPainter(Icons.Outlined.EmojiSymbols)
                        Category.FLAGS -> rememberVectorPainter(Icons.Outlined.EmojiFlags)
                        Category.FREQUENT -> rememberVectorPainter(Icons.Outlined.AccessTime)
                    }

                    val contentColor by animateColorAsState(
                        if (category == selectedCategory) {
                            CodeTheme.colors.textMain
                        } else {
                            CodeTheme.colors.textSecondary
                        }
                    )

                    Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier
                            .noRippleClickable { onSelection(category) }
                            .onGloballyPositioned { coordinates ->
                                // Store the x position of each item
                                itemPositions[index] = coordinates.positionInParent().x
                            }
                    )
                }
            }
        }

        // Animate the background when selection changes
        LaunchedEffect(selectedCategory) {
            val selectedIndex = categories.indexOf(selectedCategory)
            if (selectedIndex >= 0 && itemPositions.containsKey(selectedIndex)) {
                animatedOffset.animateTo(
                    targetValue = itemPositions[selectedIndex] ?: 0f,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }
}

private fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit) = composed {
    this.clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
}
@Preview
@Composable
private fun Preview_EmojiGarden() {
    DesignSystem {
        EmojiGarden { }
    }
}