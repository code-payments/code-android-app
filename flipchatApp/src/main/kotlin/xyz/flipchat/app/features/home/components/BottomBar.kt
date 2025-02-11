package xyz.flipchat.app.features.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.SystemNavigationMode
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberSystemNavigationMode
import com.getcode.ui.utils.withTopBorder

@Composable
internal fun BottomBar(
    tabNavigator: TabNavigator,
    tabs: List<ChildNavTab>
) {
    val systemNavigationMode by rememberSystemNavigationMode()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .withTopBorder()
            .addIf(systemNavigationMode != SystemNavigationMode.Gesture) {
                Modifier
                    .background(CodeTheme.colors.background)
                    .navigationBarsPadding()
            }
    ) {
        tabs.fastForEach { tab ->
            BottomBarTab(tabNavigator, tab, systemNavigationMode)
        }
    }
}

@Composable
private fun RowScope.BottomBarTab(
    tabNavigator: TabNavigator,
    tab: ChildNavTab,
    systemNavigationMode: SystemNavigationMode,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (tabNavigator.current.options.index == tab.options.index) {
            CodeTheme.colors.brandSubtle
        } else {
            CodeTheme.colors.surface
        },
        label = "selected tab color"
    )
    Box(
        modifier = Modifier
            .background(backgroundColor)
            .weight(1f)
            .clickable { tabNavigator.current = tab }
            .addIf(systemNavigationMode == SystemNavigationMode.Gesture) {
                Modifier.navigationBarsPadding()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(
                    top = CodeTheme.dimens.grid.x2,
                    bottom = if (systemNavigationMode != SystemNavigationMode.Gesture) {
                        CodeTheme.dimens.grid.x2
                    } else {
                        0.dp
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                CodeTheme.dimens.grid.x1,
                Alignment.CenterVertically
            )
        ) {
            Image(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                painter = tab.options.icon!!,
                colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
                contentDescription = null,
            )

            Text(
                text = tab.options.title,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}