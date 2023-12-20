package com.getcode.view.main.account

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.SessionManager
import com.getcode.solana.organizer.AccountType
import com.getcode.theme.*
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

    AnalyticsScreenWatcher(
        lifecycleOwner = LocalLifecycleOwner.current,
        event = AnalyticsManager.Screen.Deposit
    )

    Column(
        modifier = Modifier
            .background(Brand)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            text = stringResource(R.string.subtitle_howToDeposit),
            color = BrandLight,
            style = MaterialTheme.typography.body2.copy(
                textAlign = TextAlign.Center,
            ),
        )

        Row(
            modifier = Modifier
                .padding(vertical = 15.dp)
                .clip(RoundedCornerShape(5.dp))
                .border(1.dp, BrandLight, RoundedCornerShape(5.dp))
                .fillMaxWidth()
                .height(50.dp)
                .background(White05)
                .clickable {
                    localClipboardManager.setText(AnnotatedString(address))
                    isCopied = true
                }
                .padding(vertical = 10.dp, horizontal = 10.dp),
        ) {
            MiddleEllipsisText(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
                    .padding(top = 2.dp),
                text = address,
                color = White,
                style = MaterialTheme.typography.body1.copy(
                    textAlign = TextAlign.Center,
                ),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        CodeButton(
            modifier = Modifier
                .padding(bottom = 10.dp),
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