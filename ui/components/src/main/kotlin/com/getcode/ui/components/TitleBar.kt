package com.getcode.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.ui.utils.calculateHorizontalPadding
import com.getcode.ui.utils.unboundedClickable

object AppBarDefaults {
    @Composable
    fun UpNavigation(modifier: Modifier = Modifier, onClick: () -> Unit) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "",
            tint = Color.White,
            modifier = modifier
                .wrapContentWidth()
                .size(24.dp)
                .unboundedClickable { onClick() }
        )
    }

    @Composable
    fun Close(modifier: Modifier = Modifier, onClick: () -> Unit) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "",
            tint = Color.White,
            modifier = modifier
                .wrapContentWidth()
                .size(24.dp)
                .unboundedClickable { onClick() }
        )
    }

    @Composable
    fun Share(modifier: Modifier = Modifier, onClick: () -> Unit) {
        Icon(
            painter = painterResource(R.drawable.ic_remote_send),
            contentDescription = "",
            tint = Color.White,
            modifier = modifier
                .wrapContentWidth()
                .size(24.dp)
                .unboundedClickable { onClick() }
        )
    }

    @Composable
    fun Overflow(
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "",
            tint = Color.White,
            modifier = modifier
                .wrapContentWidth()
                .size(24.dp)
                .unboundedClickable { onClick() }
        )
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
        )
    }
}

@Composable
fun AppBarWithTitle(
    modifier: Modifier = Modifier,
    title: String = "",
    titleAlignment: Alignment.Horizontal = Alignment.Start,
    backButton: Boolean = false,
    onBackIconClicked: () -> Unit = {},
    endContent: @Composable () -> Unit = { },
) {
    TopAppBarBase(
        modifier = modifier.statusBarsPadding(),
        leftIcon = {
            if (backButton) {
                AppBarDefaults.UpNavigation { onBackIconClicked() }
            }
        },
        titleRegion = {
            AppBarDefaults.Title(text = title)
        },
        titleAlignment = titleAlignment,
        rightContents = endContent
    )
}

@Composable
fun AppBarWithTitle(
    modifier: Modifier = Modifier,
    title: String = "",
    titleAlignment: Alignment.Horizontal = Alignment.Start,
    startContent: @Composable () -> Unit = { },
    endContent: @Composable () -> Unit = { },
) {
    TopAppBarBase(
        modifier = modifier.statusBarsPadding(),
        leftIcon =  startContent,
        titleRegion = {
            AppBarDefaults.Title(text = title)
        },
        titleAlignment = titleAlignment,
        rightContents = endContent
    )
}

@Composable
fun AppBarWithTitle(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    titleAlignment: Alignment.Horizontal = Alignment.Start,
    contentPadding: PaddingValues = PaddingValues(horizontal = CodeTheme.dimens.grid.x2),
    leftIcon: @Composable () -> Unit = { },
    rightContents: @Composable () -> Unit = { }
) {
    TopAppBarBase(
        modifier = modifier.statusBarsPadding(),
        leftIcon = leftIcon,
        rightContents = rightContents,
        contentPadding = contentPadding,
        titleRegion = title,
        titleAlignment = titleAlignment
    )
}

@Composable
private fun TopAppBarBase(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = CodeTheme.dimens.grid.x2),
    leftIcon: @Composable () -> Unit = { },
    titleRegion: @Composable () -> Unit = { },
    rightContents: @Composable () -> Unit = { },
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally // New parameter
) {
    val inset = CodeTheme.dimens.inset
    val horizontal = contentPadding.calculateHorizontalPadding()

    SubcomposeLayout(modifier = modifier.height(56.dp)) { constraints ->
        // Measure left icon, if provided
        val leftIconPlaceable = subcompose("leftIcon", leftIcon).firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )

        // Measure right contents, if provided
        val rightContentsPlaceable = subcompose("rightContents", rightContents).firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )

        // Calculate the remaining space for the title region
        val leftIconWidth = leftIconPlaceable?.width ?: 0
        val rightContentsWidth = rightContentsPlaceable?.width ?: 0
        val remainingWidth = constraints.maxWidth - leftIconWidth - rightContentsWidth - contentPadding.calculateLeftPadding(layoutDirection).roundToPx() - (contentPadding.calculateRightPadding(layoutDirection).roundToPx() * 2)

        // Measure title region with the remaining space, if provided
        val titleRegionPlaceable = subcompose("titleRegion", titleRegion).firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = remainingWidth)
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            // Place left icon, if present
            leftIconPlaceable?.placeRelative(
                x = contentPadding.calculateLeftPadding(layoutDirection).roundToPx(),
                y = (constraints.maxHeight - (leftIconPlaceable.height)) / 2
            )

            // Place right contents, if present
            rightContentsPlaceable?.placeRelative(
                x = constraints.maxWidth - rightContentsWidth - contentPadding.calculateRightPadding(layoutDirection).roundToPx(),
                y = (constraints.maxHeight - rightContentsPlaceable.height) / 2
            )

            // Place title region with configurable alignment
            val titleX = when (titleAlignment) {
                Alignment.Start -> {
                    if (leftIconWidth == 0) inset.roundToPx()
                    else leftIconWidth + horizontal.roundToPx()
                }
                Alignment.End -> constraints.maxWidth - rightContentsWidth - contentPadding.calculateRightPadding(layoutDirection).roundToPx() - (titleRegionPlaceable?.width ?: 0)
                else -> (constraints.maxWidth - (titleRegionPlaceable?.width ?: 0)) / 2
            }

            // Place title region
            titleRegionPlaceable?.placeRelative(
                x = titleX,
                y = (constraints.maxHeight - (titleRegionPlaceable.height)) / 2
            )
        }
    }
}

@Preview
@Composable
fun Preview_TitleBar(

) {
    DesignSystem {
        AppBarWithTitle(backButton = true, title = "Hey")
    }
}