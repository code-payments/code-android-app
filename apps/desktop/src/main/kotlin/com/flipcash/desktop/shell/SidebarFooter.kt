package com.flipcash.desktop.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White

/**
 * The bottom of the chat list (node 13000:91): avatar and name open You, the balance opens Wallet,
 * the gear opens Settings. The balance is the wallet's total, so it reads the same as Wallet's header.
 */
@Composable
fun SidebarFooter(
    displayName: String,
    balance: String,
    onYou: () -> Unit,
    onWallet: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable (Modifier) -> Unit = {},
) {
    Row(
        modifier = modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onYou)
                .testTag("footer_you"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            avatar(Modifier.size(28.dp))
            Text(
                text = displayName,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The balance pill (node 13000:230): the wallet's total, which opens Wallet.
        Row(
            modifier = Modifier
                .height(30.dp)
                .widthIn(min = 120.dp)
                .clip(CircleShape)
                .background(White.copy(alpha = 0.12f))
                .border(1.dp, White.copy(alpha = 0.35f), CircleShape)
                .clickable(role = Role.Button, onClick = onWallet)
                .padding(start = 10.dp, end = 12.dp)
                .testTag("footer_balance"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = CodeTheme.colors.textMain, modifier = Modifier.size(16.dp))
            Text(
                text = balance,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSettings)
                .testTag("footer_settings"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = CodeTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The rail's footer (rail node 13104:3750): Wallet, with the balance on hover, Settings, and your
 * avatar for You. The same three destinations as [SidebarFooter], stacked.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RailFooter(
    balance: String,
    onYou: () -> Unit,
    onWallet: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable (Modifier) -> Unit = {},
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TooltipArea(
            tooltip = {
                Text(
                    text = balance,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textMain,
                    modifier = Modifier
                        .background(CodeTheme.colors.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            },
        ) {
            RailButton(Icons.Outlined.AccountBalanceWallet, "Wallet, $balance", onWallet, "rail_wallet")
        }
        RailButton(Icons.Outlined.Settings, "Settings", onSettings, "rail_settings")
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "You", onClick = onYou)
                .testTag("rail_you"),
            contentAlignment = Alignment.Center,
        ) {
            avatar(Modifier.size(28.dp))
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, onClick: () -> Unit, tag: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = CodeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
    }
}
