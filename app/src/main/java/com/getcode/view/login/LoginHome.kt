package com.getcode.view.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.theme.BrandLight
import com.getcode.util.ChromeTabsUtils
import com.getcode.view.ARG_IS_NEW_ACCOUNT
import com.getcode.view.LoginSections
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.R


@Preview
@Composable
fun LoginHome(navController: NavController? = null, upPress: () -> Unit = {}) {
    val viewModel = hiltViewModel<LoginViewModel>()

    val context = LocalContext.current

    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
        val (bgImage, logo, buttonCreate, buttonLogin, toc) = createRefs()

        Image(
            painterResource(R.drawable.ic_code_splash_bg),
            "",
            modifier = Modifier
                .constrainAs(bgImage) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                }
                .fillMaxSize(),
            contentScale = ContentScale.Crop,
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
            Modifier
                .constrainAs(buttonCreate) {
                    top.linkTo(logo.bottom) //possibly remove!!
                    bottom.linkTo(buttonLogin.top)
                }
                .padding(horizontal = 20.dp),
            onClick = {
                navController?.navigate(
                    LoginSections.PHONE_VERIFY.route
                        .replace("{${ARG_IS_NEW_ACCOUNT}}", true.toString())
                )
            },
            text = stringResource(R.string.action_createAccount),
            buttonState = ButtonState.Filled,
        )
        CodeButton(
            Modifier
                .constrainAs(buttonLogin) {
                    top.linkTo(buttonCreate.bottom)
                }
                .padding(horizontal = 20.dp),
            onClick = {
                navController?.navigate(LoginSections.SEED_INPUT.route)
            },
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
            style = MaterialTheme.typography.caption.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = BrandLight,
            ),
            modifier = Modifier
                .constrainAs(toc) {
                    bottom.linkTo(parent.bottom)
                }
                .padding(20.dp),
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

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        viewModel.onInit()
    }
}
