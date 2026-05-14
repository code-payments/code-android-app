package com.flipcash.app.bill.customization.components

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.PlaygroundState
import com.flipcash.app.bill.customization.features.BackgroundControls
import com.flipcash.app.bill.customization.features.TextureControls
import com.flipcash.app.bill.customization.internal.InternalBillPlaygroundController
import com.flipcash.app.bill.customization.models.PlaygroundFeature
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.featureflags.NoOpFeatureFlagController
import com.flipcash.app.theme.FlipcashPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Pill
import com.getcode.ui.core.addIf
import com.getcode.ui.core.measured
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.swallowClicks

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BillPlayground(
    modifier: Modifier = Modifier,
    state: PlaygroundState,
    dispatchEvent: (Event) -> Unit,
) {
    Box(modifier = modifier) {
        val screenWidth = CodeTheme.dimens.screenWidth
        var firstFeatureWidth by remember { mutableStateOf(0.dp) }
        var lastFeatureWidth by remember { mutableStateOf(0.dp) }
        val halfWidth = screenWidth / 2
        val startPadding = halfWidth - firstFeatureWidth / 2
        val endPadding = halfWidth - lastFeatureWidth / 2
        val toolbar = LocalTextToolbar.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CodeTheme.dimens.grid.x3)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val selectedIndex by remember(state.selectedFeature) {
                derivedStateOf {
                    state.features.indexOf(state.selectedFeature).takeIf { it >= 0 } ?: 0
                }
            }

            ScrollableTabRow(
                modifier = Modifier.fillMaxWidth(),
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp,
                backgroundColor = Color.Transparent,
                indicator = {}, // no indicator
                divider = {} // no divider
            ) {
                // Add a leading spacer (invisible tab)
                Spacer(modifier = Modifier.width(startPadding))

                (state.features).fastForEachIndexed { index, feature ->
                    FeatureTab(
                        modifier = Modifier.addIf(index == 0 || index == state.features.lastIndex) {
                            Modifier.measured {
                                if (index == 0) {
                                    firstFeatureWidth = it.width
                                } else {
                                    lastFeatureWidth = it.width
                                }
                            }
                        },
                        feature = feature,
                        isSelected = feature == state.selectedFeature,
                        onClick = {
                            dispatchEvent(Event.SelectFeature(feature))
                        }
                    )
                }

                // Add a leading spacer (invisible tab)
                Spacer(modifier = Modifier.width(endPadding))
            }

            var playgroundHeight by remember { mutableStateOf(0.dp) }

            AnimatedContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .addIf(playgroundHeight == 0.dp) {
                        Modifier.wrapContentHeight()
                    }
                    .addIf(playgroundHeight > 0.dp) {
                        Modifier.height(playgroundHeight)
                    },
                targetState = state.selectedFeature,
            ) { feature ->
                when (feature) {
                    PlaygroundFeature.Background -> {
                        Box(modifier = Modifier.measured { playgroundHeight = it.height }) {
                            BackgroundControls(
                                state = state.backgroundState,
                                onShowingPaste = { dispatchEvent(Event.PresentPasteOption(true)) },
                                onPasteConfiguration = { dispatchEvent(Event.ApplyFromClipboard) },
                                dispatchEvent = dispatchEvent
                            )
                        }
                    }

                    PlaygroundFeature.Textures -> TextureControls(
                        state.textureState,
                        dispatchEvent
                    )
                }
            }
        }

        if (state.awaitingPaste) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .swallowClicks {
                        dispatchEvent(Event.PresentPasteOption(false))
                        toolbar.hide()
                    }
            )
        }
    }
}

@Composable
private fun FeatureTab(
    feature: PlaygroundFeature,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        if (isSelected) Color.White else Color.Transparent
    )

    val contentColor by animateColorAsState(
        if (isSelected) Color.Black else Color.White
    )

    val alpha by animateFloatAsState(
        if (isSelected) 1f else 0.6f
    )

    Pill(
        modifier = modifier
            .alpha(alpha)
            .presenceBorder(
                width = CodeTheme.dimens.border,
                color = Color.White,
                shape = CodeTheme.shapes.medium,
            )
            .rememberedClickable(onClick = onClick),
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        text = stringResource(feature.labelRes),
        textStyle = CodeTheme.typography.textMedium,
        shape = CodeTheme.shapes.medium,
    )
}

@Composable
internal fun Modifier.presenceBorder(
    width: Dp = 2.dp,
    color: Color = Color.White.copy(0.30f),
    shape: Shape = CodeTheme.shapes.small
): Modifier = this.border(
    width = width,
    color = color,
    shape = shape
)

private object PreviewFeatureFlagController : FeatureFlagController by NoOpFeatureFlagController {
    override fun observe(flag: FeatureFlag<*>): StateFlow<Boolean> = MutableStateFlow(true)
}

@Composable
@Preview
private fun PreviewCustomizationControls() {
    FlipcashPreview {
        val clipboardManager = LocalContext.current.getSystemService(ClipboardManager::class.java)
        val controller = remember {
            InternalBillPlaygroundController(
                clipboard = clipboardManager,
                featureFlags = PreviewFeatureFlagController,
            )
        }
        val state by controller.state.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(CodeTheme.dimens.grid.x10)
                    .aspectRatio(0.555f)
                    .weight(1f)
                    .background(
                        brush = state.backgroundBrush,
                        shape = CodeTheme.shapes.large,
                    )
                    .presenceBorder(),
            )
            BillPlayground(
                state = state,
            ) { controller.dispatchEvent(it) }
        }
    }
}