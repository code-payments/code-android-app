package com.flipcash.desktop.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.ui.NavBarGlyph
import com.flipcash.app.core.ui.label
import com.getcode.theme.CodeTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Height of the area the window's title bar draws over. On macOS the content runs under a transparent
 * title bar, so whatever sits at the top of a pane starts below the traffic lights; elsewhere it is 0.
 */
val LocalTitleBarInset = compositionLocalOf { 0.dp }

/**
 * The 64-wide icon rail that replaces the tab bar from 600 wide (Decisions 12631:459, card 2): Chat
 * and Money at the top, the account's avatar (You) pinned at the bottom. The glyphs are the tab bar's
 * own; each tab's name moves into a tooltip.
 */
@Composable
fun DesktopRail(
    selectedTab: NavBarButton,
    onTabClick: (NavBarButton) -> Unit,
    avatar: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(DesktopDimens.RailWidth)
            .fillMaxHeight()
            .padding(top = LocalTitleBarInset.current + 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DesktopTabs.filter { it != NavBarButton.TipCard }.forEach { tab ->
            RailItem(tab, selected = tab == selectedTab, onClick = { onTabClick(tab) }) {
                NavBarGlyph(button = tab, selected = tab == selectedTab, badgeCount = 0)
            }
        }
        Spacer(Modifier.weight(1f))
        val you = NavBarButton.TipCard
        RailItem(you, selected = selectedTab == you, onClick = { onTabClick(you) }, highlight = false) {
            NavBarGlyph(button = you, selected = selectedTab == you, badgeCount = 0, avatar = avatar)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailItem(
    tab: NavBarButton,
    selected: Boolean,
    onClick: () -> Unit,
    // The avatar carries its own selection ring, so it gets no pill behind it.
    highlight: Boolean = true,
    glyph: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Text(
                text = stringResource(tab.label),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textMain,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CodeTheme.colors.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.CenterEnd,
            alignment = Alignment.CenterEnd,
            offset = DpOffset(8.dp, 0.dp),
        ),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected && highlight) CodeTheme.colors.surfaceVariant else Color.Transparent)
                .clickable(role = Role.Tab, onClick = onClick)
                .testTag("rail_${tab.name}"),
            contentAlignment = Alignment.Center,
        ) {
            glyph()
        }
    }
}
