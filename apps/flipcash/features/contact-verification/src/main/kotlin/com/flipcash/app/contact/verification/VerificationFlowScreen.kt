package com.flipcash.app.contact.verification

import android.content.Context
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.core.lifecycle.LifecycleEffectOnce
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.flipcash.app.contact.verification.email.EmailMagicLinkScreen
import com.flipcash.app.contact.verification.email.EmailVerificationScreen
import com.flipcash.app.contact.verification.internal.VerificationFlowIntroScreen
import com.flipcash.app.contact.verification.phone.PhoneVerificationScreen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.app.navigation.FlowNavigator
import com.flipcash.app.navigation.LocalFlowNavigator
import com.flipcash.app.navigation.NavigationFlowStep
import com.flipcash.features.contact.verification.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ModalScreen
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class VerificationFlowScreen(
    private val origin: AppRoute,
    private val target: AppRoute? = null,
    private val includePhone: Boolean = true,
    private val includeEmail: Boolean = true,
    private val showSuccess: Boolean = target == null && (includePhone xor includeEmail),
    private val emailAddress: String? = null,
    private val emailVerificationCode: String? = null,
) : ModalScreen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "verification_screen"

    @OptIn(ExperimentalVoyagerApi::class)
    @Composable
    override fun ModalContent() {
        val codeNavigator = LocalCodeNavigator.current
        val context = LocalContext.current
        fun showSuccess() {
            if (includePhone) {
                BottomBarManager.showMessage(
                    title = context.getString(R.string.prompt_title_phoneVerifiedSuccessfully),
                    subtitle = context.getString(R.string.prompt_description_phoneVerifiedSuccessfully),
                    actions = listOf(
                        BottomBarAction(text = context.getString(android.R.string.ok))
                    ),
                    type = BottomBarManager.BottomBarMessageType.SUCCESS,
                ) {
                    codeNavigator.pop()
                }
            } else {
                BottomBarManager.showMessage(
                    title = context.getString(R.string.prompt_title_emailVerifiedSuccessfully),
                    subtitle = context.getString(R.string.prompt_description_emailVerifiedSuccessfully),
                    actions = listOf(
                        BottomBarAction(text = context.getString(android.R.string.ok))
                    ),
                    type = BottomBarManager.BottomBarMessageType.SUCCESS,
                ) {
                    codeNavigator.pop()
                }
            }
        }

        fun goToTargetOrReturn(wasSuccessful: Boolean) {
            if (target != null) {
                codeNavigator.replace(ScreenRegistry.get(target))
            } else {
                if (wasSuccessful && showSuccess) {
                    showSuccess()
                } else {
                    codeNavigator.pop()
                }
            }
        }

        LifecycleEffectOnce {
            PhoneVerificationFlow.start(origin)
            EmailVerificationFlow.start(EmailDeeplinkOrigin.fromRoute(origin))
        }

        val screens =
            buildScreenSet(
                origin = origin,
                includePhone = includePhone,
                includeEmail = includeEmail,
                emailAddress = emailAddress,
                emailVerificationCode = emailVerificationCode
            )
        if (screens.isEmpty()) {
            goToTargetOrReturn(false)
            return
        }

        Navigator(screens.toList()) { navigator ->
            val flowNavigator = rememberFlowNavigator(
                target = target,
                includePhone = includePhone,
                includeEmail = includeEmail,
                showSuccess = showSuccess,
                navigator = navigator,
                context = context,
                goToTargetOrReturn = { success -> goToTargetOrReturn(success) }
            )

            CompositionLocalProvider(LocalFlowNavigator provides flowNavigator) {
                SlideTransition(navigator = navigator)
            }
        }
    }
}

private fun buildScreenSet(
    origin: AppRoute,
    includePhone: Boolean,
    includeEmail: Boolean,
    emailAddress: String?,
    emailVerificationCode: String?,
): Set<Screen> {
    if (includePhone && includeEmail) {
        return setOf(VerificationFlowIntroScreen(origin is AppRoute.OnRamp.ProviderList))
    }
    if (includePhone) {
        return setOf(PhoneVerificationScreen())
    }

    if (includeEmail) {
        return buildSet {
            add(EmailVerificationScreen())
            if (emailAddress != null && emailVerificationCode != null) {
                add(EmailMagicLinkScreen(emailAddress, emailVerificationCode))
            }
        }
    }

    return emptySet()
}

enum class VerificationFlowStep: NavigationFlowStep {
    Intro,
    Phone,
    Email;
}

@Composable
fun rememberFlowNavigator(
    target: AppRoute?,
    includePhone: Boolean,
    includeEmail: Boolean,
    showSuccess: Boolean,
    navigator: Navigator,
    context: Context = LocalContext.current,
    goToTargetOrReturn: (Boolean) -> Unit = {},
): FlowNavigator<VerificationFlowStep> {
    return remember(navigator, target, includePhone, includeEmail, showSuccess, context) {
        object : FlowNavigator<VerificationFlowStep> {
            override fun exit(success: Boolean) {
                goToTargetOrReturn(success)
            }

            override fun continueFlowFrom(step: VerificationFlowStep) {
                when (step) {
                    VerificationFlowStep.Intro -> {
                        navigator.push(PhoneVerificationScreen())
                    }
                    VerificationFlowStep.Phone -> {
                        if (includeEmail) {
                            navigator.push(EmailVerificationScreen())
                        } else {
                            goToTargetOrReturn(true)
                        }
                    }
                    VerificationFlowStep.Email -> {
                        goToTargetOrReturn(true)
                    }
                }
            }
        }
    }
}