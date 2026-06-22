package com.flipcash.app.permissions.internal.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.flipcash.shared.chat.ui.AnimatedConversationPreview
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
fun ContactRationaleContent() {
    val context = LocalContext.current
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CodeButton(
                    onClick = { context.launchAppSettings() },
                    text = stringResource(R.string.action_allowContactAccessInSettings),
                    buttonState = ButtonState.Filled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(top = CodeTheme.dimens.grid.x12),
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedConversationPreview(animate = false, gradientColorStops = listOf(0f, 0.9f, 0.99f))

                Text(
                    text = stringResource(R.string.permissions_title_contactsRationale),
                    style = CodeTheme.typography.displaySmall,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x2)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.permissions_description_contactsRationale),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                Column(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x12)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                ) {
                    BulletRow(
                        painterResource(R.drawable.ic_checklist),
                        stringResource(R.string.rationale_bullet_contactChoice)
                    )
                    BulletRow(
                        painterResource(R.drawable.ic_lock),
                        stringResource(R.string.rationale_bullet_secure)
                    )
                    BulletRow(
                        painterResource(R.drawable.ic_people_gear),
                        stringResource(R.string.rationale_bullet_changeAccess)
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletRow(painter: Painter, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painter, contentDescription = null, tint = CodeTheme.colors.textMain)
        Text(
            text = text,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewContactRationale() {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub(context = LocalContext.current),
    ) {
        ContactRationaleContent()
    }
}