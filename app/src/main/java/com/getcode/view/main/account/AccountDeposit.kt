package com.getcode.view.main.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.theme.Brand
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.extraSmall
import com.getcode.util.rememberedClickable
import com.getcode.vendor.Base58
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.MiddleEllipsisText

@Composable
fun AccountDeposit() {
    val address = SessionManager.getOrganizer()?.primaryVault
        ?.let { Base58.encode(it.byteArray) }
        ?: return

    val localClipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .background(Brand)
            .fillMaxSize()
            .padding(horizontal = CodeTheme.dimens.inset)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            text = stringResource(R.string.subtitle_howToDeposit),
            color = BrandLight,
            style = CodeTheme.typography.body2.copy(
                textAlign = TextAlign.Center,
            ),
        )

        Row(
            modifier = Modifier
                .padding(vertical = CodeTheme.dimens.grid.x3)
                .clip(CodeTheme.shapes.extraSmall)
                .border(CodeTheme.dimens.border, BrandLight, CodeTheme.shapes.extraSmall)
                .fillMaxWidth()
                .height(CodeTheme.dimens.grid.x10)
                .background(White05)
                .rememberedClickable {
                    localClipboardManager.setText(AnnotatedString(address))
                    isCopied = true
                }
                .padding(CodeTheme.dimens.grid.x2),
        ) {
            MiddleEllipsisText(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
                    .padding(top = CodeTheme.dimens.grid.x1),
                text = address,
                color = White,
                style = CodeTheme.typography.body1.copy(
                    textAlign = TextAlign.Center,
                ),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.grid.x2),
            onClick = {
                localClipboardManager.setText(AnnotatedString(address))
                isCopied = true
            },
            text = stringResource(if (!isCopied) R.string.action_copyAddress else R.string.action_copied),
            enabled = !isCopied,
            isTextSuccess = isCopied,
            buttonState = ButtonState.Filled,
        )
    }
}