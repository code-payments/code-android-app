package com.getcode.view.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.components.ImageWithBackground
import com.getcode.util.ChromeTabsUtils

@Composable
fun LoginHome(
    createAccount: () -> Unit,
    login: () -> Unit,
) {
    val context = LocalContext.current

    Box {
        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            val (bgImage, logo, buttonCreate, buttonLogin, toc) = createRefs()

            ImageWithBackground(
                modifier = Modifier
                    .constrainAs(bgImage) {
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        top.linkTo(parent.top)
                    }
                    .fillMaxSize(),
                painter = ColorPainter(Brand),
                backgroundDrawableResId = R.drawable.ic_code_splash_bg,
                contentDescription = null
            )

            Image(
                painter = painterResource(
                    R.drawable.ic_code_logo_near_white
                ),
                contentDescription = "",
                modifier = Modifier
                    .constrainAs(logo) {
                        top.linkTo(parent.top)
                        bottom.linkTo(buttonCreate.top)
                        centerHorizontallyTo(parent)
                    }
                    .fillMaxWidth(0.65f)
                    .fillMaxHeight(0.65f)
            )

            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(buttonCreate) {
                        top.linkTo(logo.bottom) //possibly remove!!
                        bottom.linkTo(buttonLogin.top)
                    }
                    .padding(horizontal = CodeTheme.dimens.inset),
                onClick = createAccount,
                text = stringResource(R.string.action_createAccount),
                buttonState = ButtonState.Filled,
            )
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(buttonLogin) {
                        top.linkTo(buttonCreate.bottom)
                    }
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
                pushStringAnnotation(tag = "tos", annotation = stringResource(R.string.app_privacy_policy))
                withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(stringResource(R.string.title_termsOfService))
                }
                pop()
                append(" ")
                append(stringResource(R.string.core_and))
                append(" ")
                pushStringAnnotation(
                    tag = "policy",
                    annotation = stringResource(R.string.app_privacy_policy)
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
                    .constrainAs(toc) {
                        bottom.linkTo(parent.bottom)
                    }
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
    }

    val focusManager = LocalFocusManager.current
    val analytics = LocalAnalytics.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        analytics.onAppStarted()

    }
}