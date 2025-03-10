package com.getcode.navigation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.core.ContextMenuAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface ContextMenuStyle {
    @get:Composable
    val color: Color

    data object Default: ContextMenuStyle {
        override val color: Color
            @Composable get() = Color(0xFF171921)
    }

    data object Themed: ContextMenuStyle {
        override val color: Color
            @Composable get() = CodeTheme.colors.surfaceVariant
    }
}

class ContextSheet(
    private val actions: List<ContextMenuAction>,
    private val style: ContextMenuStyle = ContextMenuStyle.Themed,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val composeScope = rememberCoroutineScope()

        LazyColumn(
            modifier = Modifier
                .background(style.color)
                .padding(top = CodeTheme.dimens.grid.x2)
                .navigationBarsPadding()
        ) {
            itemsIndexed(actions) { index, action ->
                when (action) {
                   is ContextMenuAction.Single -> {
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
                               modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
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
                   }

                    is ContextMenuAction.Custom -> {
                        action.Content()
                    }
                }
                if (index < actions.lastIndex) {
                    Divider(color = White05)
                }
            }
        }
    }
}