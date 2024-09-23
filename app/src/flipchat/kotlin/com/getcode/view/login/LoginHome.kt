package com.getcode.view.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.getcode.LocalAnalytics
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.util.ChromeTabsUtils

@Composable
fun LoginHome(
    createAccount: () -> Unit,
    login: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.secondary)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.65f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
        ) {
            Image(
                painter = painterResource(R.drawable.flipchat_logo),
                contentDescription = "",
                modifier = Modifier
            )
            Text(
                text = "Flipchat",
                style = CodeTheme.typography.displayMedium,
                color = White
            )
        }

        Spacer(Modifier.weight(1f))

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            onClick = createAccount,
            text = stringResource(R.string.action_createAccount),
            buttonState = ButtonState.Filled,
        )
        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            onClick = login,
            text = stringResource(R.string.action_logIn),
            buttonState = ButtonState.Subtle,
        )


        val bottomString = buildAnnotatedString {
            append(stringResource(R.string.login_description_byTapping))
            append(" ")
            append(stringResource(R.string.login_description_agreeToOur))
            append(" ")
            pushStringAnnotation(tag = "tos", annotation = "https://app.getcode.com/tos")
            withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(stringResource(R.string.title_termsOfService))
            }
            pop()
            append(" ")
            append(stringResource(R.string.core_and))
            append(" ")
            pushStringAnnotation(
                tag = "policy",
                annotation = "https://app.getcode.com/privacy-policy"
            )
            withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(stringResource(R.string.title_privacyPolicy))
            }
            pop()
        }

        ClickableText(
            text = bottomString,
            style = CodeTheme.typography.caption.copy(
                textAlign = TextAlign.Center,
                color = CodeTheme.colors.textSecondary
            ),
            modifier = Modifier
                .padding(CodeTheme.dimens.grid.x4),
            onClick = { offset ->
                bottomString.getStringAnnotations(tag = "tos", start = offset, end = offset)
                    .firstOrNull()?.let {
                        ChromeTabsUtils.launchUrl(context, it.item)
                    }
                bottomString.getStringAnnotations(tag = "policy", start = offset, end = offset)
                    .firstOrNull()?.let {
                        ChromeTabsUtils.launchUrl(context, it.item)
                    }
            }
        )
    }

    val focusManager = LocalFocusManager.current
    val analytics = LocalAnalytics.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        analytics.onAppStarted()
    }
}