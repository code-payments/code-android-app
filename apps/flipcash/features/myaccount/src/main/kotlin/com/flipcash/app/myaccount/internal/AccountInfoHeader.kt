package com.flipcash.app.myaccount.internal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight.Companion.W600
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.menu.BetaIndicator
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable

@Composable
internal fun AccountInfoHeader(
    state: MyAccountScreenViewModel.State,
    modifier: Modifier = Modifier,
    dispatch: (MyAccountScreenViewModel.Event) -> Unit
) {
    Box(
        modifier = modifier
            .border(
                color = CodeTheme.colors.textSecondary,
                shape = CodeTheme.shapes.small,
                width = CodeTheme.dimens.border
            )
            .padding(CodeTheme.dimens.grid.x2)
            .width(IntrinsicSize.Max)
            .height(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
        ) {
            CopyableTextEntry(
                label = "Public Key",
                value = state.publicKey.orEmpty()
            ) { dispatch(MyAccountScreenViewModel.Event.CopyPublicKey) }

            CopyableTextEntry(
                label = "Account ID",
                value = state.accountId.orEmpty()
            ) { dispatch(MyAccountScreenViewModel.Event.CopyAccountId) }

            BetaIndicator(
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun CopyableTextEntry(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit
) {
    Column(
        modifier = modifier.rememberedClickable(onClick = onCopy)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = CodeTheme.typography.caption.copy(fontWeight = W600),
                color = CodeTheme.colors.textMain
            )

            Icon(
                Icons.Default.CopyAll,
                contentDescription = "Copy",
                tint = CodeTheme.colors.cashBillDecorColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(start = CodeTheme.dimens.grid.x1)
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = value,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
            overflow = TextOverflow.MiddleEllipsis,
            maxLines = 1
        )
    }
}