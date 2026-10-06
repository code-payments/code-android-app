package com.flipcash.app.menu.internal

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import dev.chrisbanes.haze.HazeState

/**
 * The single trailing control the You tab and its profile card draw over their content: Settings on
 * the tab, Download on the card. It sits in an untitled [AppBarWithTitle] so it lands where every
 * other tab's trailing bar button does, and it frosts whatever is under it when given a [hazeState].
 */
@Composable
internal fun ProfileBarButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    testTag: String? = null,
) {
    AppBarWithTitle(
        modifier = modifier,
        endContent = {
            CircularIconButton(
                hazeState = hazeState,
                onClick = onClick,
                testTag = testTag,
            ) { size ->
                Icon(
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(size),
                )
            }
        },
    )
}
