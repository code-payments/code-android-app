package com.flipcash.desktop.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

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
        modifier = modifier.height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onYou)
                .padding(6.dp)
                .testTag("footer_you"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            avatar(Modifier.size(28.dp))
            Text(
                text = displayName,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = balance,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onWallet)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("footer_balance"),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSettings)
                .testTag("footer_settings"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = CodeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
