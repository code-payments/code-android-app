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
    fun UpNavigation(modifier: Modifier = Modifier, onClick: () -> Unit) {
        CircularIconButton(
            modifier = modifier,
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
    fun Close(modifier: Modifier = Modifier, onClick: () -> Unit) {
        CircularIconButton(
            modifier = modifier,
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
    fun Share(modifier: Modifier = Modifier, onClick: () -> Unit) {
        CircularIconButton(
            modifier = modifier,
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
    titleContent: (@Composable () -> Unit)? = null,
    titleAlignment: Alignment.Horizontal = Alignment.Start,
    contentPadding: PaddingValues = AppBarDefaults.ContentPadding,
    onBackIconClicked: (() -> Unit)? = null,
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
                AppBarDefaults.UpNavigation { onBackIconClicked() }
            }
        },
        titleRegion = {
            if (titleContent != null) titleContent() else AppBarDefaults.Title(text = title)
        },
        titleAlignment = titleAlignment,
        // The caller's end actions always render; a Close (when the leading control resolves to
        // one) is appended after them so it sits furthest toward the screen edge — the
        // conventional dismiss position. [TopAppBarBase] already lays these out in an
        // [EndActionSlotHolder], so a back arrow never has to share the leading slot with a close.
        rightContents = {
            endContent()
            if (showClose) {
                AppBarDefaults.Close { onBackIconClicked() }
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
        // Measure left icon, if provided
        val emptyLeftIconPlaceable =
            subcompose("empty_leftIcon", emptyLeftSlot).firstOrNull()?.measure(
                constraints.copy(minWidth = 0, minHeight = 0)
            )
        val leftIconPlaceable = subcompose("leftIcon", leftSlot).firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )

        // Measure right contents, if provided
        val rightContentsPlaceable =
            subcompose("rightContents", rightSlot).firstOrNull()?.measure(
                constraints.copy(minWidth = 0, minHeight = 0)
            )

        // Calculate the remaining space for the title region
        val leftIconWidth = max(leftIconPlaceable?.width ?: 0, emptyLeftIconPlaceable?.width ?: 0)
        val isEmpty = (leftIconPlaceable?.width ?: 0) == 5.dp.roundToPx()
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
            // Place left icon, if present
            val left = if (isEmpty) emptyLeftIconPlaceable else leftIconPlaceable
            left?.placeRelative(
                x = contentPadding.calculateLeftPadding(layoutDirection).roundToPx(),
                y = (constraints.maxHeight - (left.height)) / 2 + contentPadding.calculateTopPadding()
                    .roundToPx()
            )

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