package com.getcode.view.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.manager.BottomBarManager
import com.getcode.theme.*

@Composable
fun BottomBarView(
    bottomBarMessage: BottomBarManager.BottomBarMessage?,
    onClose: (bottomBarActionType: BottomBarManager.BottomBarActionType?) -> Unit,
    onBackPressed: () -> Unit
) {
    bottomBarMessage ?: return

    BackHandler {
        onBackPressed()
    }

    Box(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .background(
                    when (bottomBarMessage.type) {
                        BottomBarManager.BottomBarMessageType.DEFAULT -> TopError
                        BottomBarManager.BottomBarMessageType.REMOTE_SEND -> BrandLight
                    }
                )
                .padding(15.dp)
        ) {
            Text(
                modifier = Modifier.padding(vertical = 15.dp),
                style = CodeTheme.typography.subtitle1,
                text = bottomBarMessage.title
            )
            Text(
                modifier = Modifier.padding(bottom = 10.dp),
                style = CodeTheme.typography.body2,
                text = bottomBarMessage.subtitle
            )
            CodeButton(
                onClick = {
                    bottomBarMessage.onPositive()
                    onClose(BottomBarManager.BottomBarActionType.Positive)
                },
                textColor =
                when (bottomBarMessage.type) {
                    BottomBarManager.BottomBarMessageType.DEFAULT -> TopError
                    BottomBarManager.BottomBarMessageType.REMOTE_SEND -> BrandLight
                },
                buttonState = ButtonState.Filled,
                text = bottomBarMessage.positiveText
            )
            CodeButton(
                onClick = {
                    bottomBarMessage.onNegative()
                    onClose(BottomBarManager.BottomBarActionType.Negative)
                },
                textColor = White,
                buttonState = ButtonState.Filled10,
                text = bottomBarMessage.negativeText
            )
            bottomBarMessage.tertiaryText?.let {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clickable {
                            bottomBarMessage.onTertiary()
                            onClose(BottomBarManager.BottomBarActionType.Tertiary)
                        }
                        .padding(vertical = 10.dp),
                    style = CodeTheme.typography.button.copy(fontSize = 14.sp),
                    text = it
                )
            }
        }
    }
}