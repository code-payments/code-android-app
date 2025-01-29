package com.getcode.navigation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.util.fastForEachIndexed
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.components.contextmenu.ContextMenuAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ContextSheet(private val actions: List<ContextMenuAction>) : Screen {

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .background(Color(0xFF171921))
                .padding(top = CodeTheme.dimens.inset)
                .navigationBarsPadding()
        ) {
            val navigator = LocalCodeNavigator.current
            val composeScope = rememberCoroutineScope()
            actions.fastForEachIndexed { index, action ->
                Row(
                    modifier = Modifier
                        .clickable {
                            composeScope.launch {
                                navigator.hide()
                                if (action.delayUponSelection) {
                                    delay(300)
                                }
                                action.onSelect()
                            }
                        }
                        .fillMaxWidth()
                        .padding(
                            horizontal = CodeTheme.dimens.inset,
                            vertical = CodeTheme.dimens.grid.x3
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {
                    Image(
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
                        painter = action.painter,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            if (action.isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain
                        )
                    )
                    Text(
                        text = action.title,
                        style = CodeTheme.typography.textMedium.copy(
                            color = if (action.isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (index < actions.lastIndex) {
                    Divider(color = White05)
                }
            }
        }
    }
}