package com.flipcash.app.currencycreator.internal.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.rememberKeyboardController

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CurrencyCreatorTopBar(
    controller: CurrencyCreatorTopBarController,
    mainContent: (@Composable () -> Unit)? = null,
    endContent: (@Composable () -> Unit)? = null,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = controller.progress,
        label = "progress",
    )

    val defaultMainContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (controller.progress in 0.01f..0.999f) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(horizontal = CodeTheme.dimens.grid.x3)
                        .height(CodeTheme.dimens.grid.x1),
                    color = Color.White,
                    trackColor = CodeTheme.colors.divider,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }

    val defaultEndContent = @Composable {
        Spacer(Modifier.width(24.dp))
    }

    val keyboard = rememberKeyboardController()

    AppBarWithTitle(
        isInModal = true,
        title = { mainContent?.invoke() ?: defaultMainContent() },
        leftIcon = {
            AppBarDefaults.UpNavigation {
                keyboard.hideIfVisible {
                    controller.onBack()
                }
            }
        },
        rightContents = {
            endContent?.invoke() ?: defaultEndContent()
        }
    )
}
