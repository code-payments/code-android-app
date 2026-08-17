package com.getcode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.RestorePage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.navigation.core.LocalCodeNavigator
import dev.chrisbanes.haze.HazeState
import com.getcode.navigation.flow.FlowDismissStyle
import com.getcode.navigation.flow.LocalFlowDismissStyle
import com.getcode.navigation.scenes.LocalSheetNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.ui.components.AppBarDefaults.EndActionSlotHolder
import com.getcode.ui.utils.calculateHorizontalPadding
import kotlin.math.max

object AppBarDefaults {
    val ContentPadding: PaddingValues
        @Composable get() = PaddingValues(
            vertical = CodeTheme.dimens.grid.x1,
            horizontal = CodeTheme.dimens.grid.x2
        )

    internal val IconSize = 20.dp

    @Composable
    fun UpNavigation(modifier: Modifier = Modifier, hazeState: HazeState? = null, onClick: () -> Unit) {
        CircularIconButton(
            modifier = modifier,
            hazeState = hazeState,
            onClick = onClick,
            testTag = "action_back"
        ) { size ->
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
    }

    @Composable
    fun Close(modifier: Modifier = Modifier, hazeState: HazeState? = null, onClick: () -> Unit) {
        CircularIconButton(
            modifier = modifier,
            hazeState = hazeState,
            onClick = onClick,
            testTag = "action_close"
        ) { size ->
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
    }

    @Composable
    fun Share(modifier: Modifier = Modifier, hazeState: HazeState? = null, onClick: () -> Unit) {
        CircularIconButton(
            modifier = modifier,
            hazeState = hazeState,
            onClick = onClick,
            testTag = "action_share"
        ) { size ->
            Icon(
                painter = painterResource(R.drawable.ic_remote_send),
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
    }

    @Composable
    fun Leave(modifier: Modifier = Modifier, onClick: () -> Unit) {
        CircularIconButton(modifier = modifier, onClick = onClick) { size ->
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Logout,
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
    }

    @Composable
    fun Settings(modifier: Modifier = Modifier, onClick: () -> Unit) {
        CircularIconButton(modifier = modifier, onClick = onClick) { size ->
            Icon(
                painter = painterResource(R.drawable.ic_settings_outline),
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
    }

    @Composable
    fun Overflow(
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        CircularIconButton(modifier = modifier, onClick = onClick) { size ->
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
    }

    @Composable
    fun Reset(
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        CircularIconButton(modifier = modifier, onClick = onClick) { size ->
            Icon(
                imageVector = Icons.Rounded.RestorePage,
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
    }

    @Composable
    fun Title(
        modifier: Modifier = Modifier,
        text: String = "",
        style: TextStyle = CodeTheme.typography.screenTitle,
    ) {
        Text(
            modifier = modifier,
            text = text,
            style = style,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    @Composable
    internal fun EndActionSlotHolder(content: @Composable RowScope.() -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                CodeTheme.dimens.grid.x2
            )
        ) {
            content()
        }
    }
}

@Composable
fun AppBarWithTitle(
    modifier: Modifier = Modifier,
    title: String = "",
    titleTextStyle: TextStyle = CodeTheme.typography.screenTitle,
    titleContent: (@Composable () -> Unit)? = null,
    titleAlignment: Alignment.Horizontal = Alignment.Start,
    contentPadding: PaddingValues = AppBarDefaults.ContentPadding,
    onBackIconClicked: (() -> Unit)? = null,
    hazeState: HazeState? = null,
    endContent: @Composable RowScope.() -> Unit = { },
) {
    val navigator = LocalCodeNavigator.current
    val flowDismissStyle = LocalFlowDismissStyle.current

    // A non-null [onBackIconClicked] asks for a leading nav control; its icon adapts to context.
    // It's a Close (✕) when this screen can only leave its scope — the root of a sheet's own back
    // stack, or a flow that opted into [FlowDismissStyle.Close] — and a back arrow (←) when it can
    // pop within that scope. This internalizes the back-vs-close branch sheet screens used to
    // hand-roll. We only treat it as a sheet root for plain bottom-sheet content
    // ([LocalSheetNavigator] present); full-screen roots keep a back arrow, and a flow drives its
    // own swap through [flowDismissStyle] — a flow's inner stack is always depth-1 at its first
    // step, so reading its size here would wrongly show a Close even when the flow was pushed on
    // top of other content (e.g. Swap opened from Token Info).
    val isSheetRoot = LocalSheetNavigator.current != null &&
            !navigator.isFlowNavigator &&
            navigator.backStack.size <= 1
    val showBack = onBackIconClicked != null
    val showClose = showBack && (flowDismissStyle == FlowDismissStyle.Close || isSheetRoot)

    TopAppBarBase(
        modifier = modifier,
        contentPadding = contentPadding,
        leftIcon = {
            if (showBack && !showClose) {
                AppBarDefaults.UpNavigation(hazeState = hazeState) { onBackIconClicked() }
            }
        },
        titleRegion = {
            if (titleContent != null) titleContent() else AppBarDefaults.Title(text = title, style = titleTextStyle)
        },
        titleAlignment = titleAlignment,
        // The caller's end actions always render; a Close (when the leading control resolves to
        // one) is appended after them so it sits furthest toward the screen edge — the
        // conventional dismiss position. [TopAppBarBase] already lays these out in an
        // [EndActionSlotHolder], so a back arrow never has to share the leading slot with a close.
        rightContents = {
            endContent()
            if (showClose) {
                AppBarDefaults.Close(hazeState = hazeState) { onBackIconClicked() }
            }
        },
    )
}

@Composable
fun AppBarWithTitle(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    titleAlignment: Alignment.Horizontal = Alignment.Start,
    contentPadding: PaddingValues = AppBarDefaults.ContentPadding,
    leftIcon: @Composable () -> Unit = { },
    rightContents: @Composable RowScope.() -> Unit = { }
) {
    TopAppBarBase(
        modifier = modifier,
        leftIcon = leftIcon,
        rightContents = rightContents,
        contentPadding = contentPadding,
        titleRegion = title,
        titleAlignment = titleAlignment
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopAppBarBase(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = AppBarDefaults.ContentPadding,
    leftIcon: @Composable () -> Unit = { },
    titleRegion: @Composable () -> Unit = { },
    rightContents: @Composable RowScope.() -> Unit = { },
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally // New parameter
) {
    val inset = CodeTheme.dimens.inset
    val horizontal = contentPadding.calculateHorizontalPadding()

    val emptyLeftSlot = @Composable {
        Box(modifier = Modifier.padding(5.dp)) {
            AppBarDefaults.UpNavigation { }
        }
    }
    val leftSlot = @Composable {
        Box(modifier = Modifier.padding(5.dp)) {
            leftIcon()
        }
    }

    val rightSlot = @Composable {
        Box(modifier = Modifier.padding(5.dp)) {
            EndActionSlotHolder {
                rightContents()
            }
        }
    }

    val isInsideSheet = LocalSheetNavigator.current != null

    SubcomposeLayout(
        modifier = modifier
            .then(
                if (!isInsideSheet) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else Modifier
            )
            .height(56.dp),
    ) { constraints ->
        // A centered title needs a phantom leading slot (a back button's worth of width) so it
        // stays optically centered against the end actions even when there's no real leading
        // control. A start/end-aligned title has no such need — reserving it would only indent the
        // title past a control that isn't there — so skip it and let the title sit flush.
        val isCentered = titleAlignment == Alignment.CenterHorizontally

        // Measure left icon, if provided
        val emptyLeftIconPlaceable = if (isCentered) {
            subcompose("empty_leftIcon", emptyLeftSlot).firstOrNull()?.measure(
                constraints.copy(minWidth = 0, minHeight = 0)
            )
        } else {
            null
        }
        val leftIconPlaceable = subcompose("leftIcon", leftSlot).firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )
        // [leftSlot] wraps [leftIcon] in a 5dp-padded Box, so it has width even when the icon
        // renders nothing. Probe the raw icon to tell an actual leading control from an empty slot.
        val hasLeadingControl = subcompose("leftIcon_probe", leftIcon).firstOrNull()
            ?.measure(constraints.copy(minWidth = 0, minHeight = 0))
            ?.let { it.width > 0 } ?: false

        // Measure right contents, if provided
        val rightContentsPlaceable =
            subcompose("rightContents", rightSlot).firstOrNull()?.measure(
                constraints.copy(minWidth = 0, minHeight = 0)
            )

        // Calculate the remaining space for the title region. A slot with no real control
        // contributes no leading width, so a start-aligned title falls through to the flush
        // `inset` position instead of being indented past a control that isn't there.
        val realLeftWidth = if (hasLeadingControl) leftIconPlaceable?.width ?: 0 else 0
        val leftIconWidth = max(realLeftWidth, emptyLeftIconPlaceable?.width ?: 0)
        val rightContentsWidth = rightContentsPlaceable?.width ?: 0
        val remainingWidth =
            constraints.maxWidth - leftIconWidth - rightContentsWidth - (contentPadding.calculateLeftPadding(
                layoutDirection
            ).roundToPx() * 2) - (contentPadding.calculateRightPadding(layoutDirection)
                .roundToPx() * 2)

        // Measure title region with the remaining space, if provided
        val titleRegionPlaceable = subcompose("titleRegion", titleRegion).firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = remainingWidth)
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            // Place the leading control only when there is one. The centered phantom slot reserves
            // width (to keep the title optically centered) but is never drawn.
            if (hasLeadingControl) {
                leftIconPlaceable?.let { placeable ->
                    placeable.placeRelative(
                        x = contentPadding.calculateLeftPadding(layoutDirection).roundToPx(),
                        y = (constraints.maxHeight - placeable.height) / 2 +
                            contentPadding.calculateTopPadding().roundToPx()
                    )
                }
            }

            // Place right contents, if present
            rightContentsPlaceable?.placeRelative(
                x = constraints.maxWidth - rightContentsWidth - contentPadding.calculateRightPadding(
                    layoutDirection
                ).roundToPx(),
                y = (constraints.maxHeight - rightContentsPlaceable.height) / 2 + contentPadding.calculateTopPadding()
                    .roundToPx()
            )

            // Place title region with configurable alignment
            val titleX = when (titleAlignment) {
                Alignment.Start -> {
                    if (leftIconWidth == 0) inset.roundToPx()
                    else leftIconWidth + horizontal.roundToPx()
                }

                Alignment.End -> constraints.maxWidth - rightContentsWidth - contentPadding.calculateRightPadding(
                    layoutDirection
                ).roundToPx() - (titleRegionPlaceable?.width ?: 0)

                else -> (constraints.maxWidth - (titleRegionPlaceable?.width ?: 0)) / 2
            }

            // Place title region
            titleRegionPlaceable?.placeRelative(
                x = titleX,
                y = (constraints.maxHeight - (titleRegionPlaceable.height)) / 2 + contentPadding.calculateTopPadding()
                    .roundToPx()
            )
        }
    }
}

@Preview
@Composable
fun Preview_TitleBar(

) {
    DesignSystem {
        AppBarWithTitle(onBackIconClicked = {}, title = "Hey")
    }
}