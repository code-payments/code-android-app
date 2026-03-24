package com.flipcash.app.contact.verification

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.flipcash.app.contact.verification.email.EmailMagicLinkContent
import com.flipcash.app.contact.verification.email.EmailVerificationContent
import com.flipcash.app.contact.verification.internal.VerificationFlowIntroContent
import com.flipcash.app.contact.verification.phone.PhoneCodeContent
import com.flipcash.app.contact.verification.phone.PhoneCountryCodeContent
import com.flipcash.app.contact.verification.phone.PhoneVerificationContent
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.app.navigation.FlowNavigator
import com.flipcash.app.navigation.LocalFlowNavigator
import com.flipcash.app.navigation.NavigationFlowStep
import com.flipcash.features.contact.verification.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator

sealed interface VerificationInternalScreen {
    data class Intro(val isForOnRamp: Boolean) : VerificationInternalScreen
    data object PhoneEntry : VerificationInternalScreen
    data object PhoneCode : VerificationInternalScreen
    data object PhoneCountryCode : VerificationInternalScreen
    data object EmailEntry : VerificationInternalScreen
    data class EmailMagicLink(val email: String? = null, val code: String? = null) : VerificationInternalScreen
}

class InternalFlowNavigator {
    val screens = mutableStateListOf<VerificationInternalScreen>()
    private var direction = 1 // 1 = forward, -1 = backward

    fun push(screen: VerificationInternalScreen) {
        direction = 1
        screens.add(screen)
    }

    fun pop(): Boolean {
        if (screens.size > 1) {
            direction = -1
            screens.removeAt(screens.lastIndex)
            return true
        }
        return false
    }

    val current: VerificationInternalScreen? get() = screens.lastOrNull()
    val slideDirection: Int get() = direction
}

@Composable
fun VerificationFlowScreen(
    origin: AppRoute,
    target: AppRoute? = null,
    includePhone: Boolean = true,
    includeEmail: Boolean = true,
    showSuccess: Boolean = target == null && (includePhone xor includeEmail),
    emailAddress: String? = null,
    emailVerificationCode: String? = null,
) {
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
            codeNavigator.replace(target)
        } else {
            if (wasSuccessful && showSuccess) {
                showSuccess()
            } else {
                codeNavigator.pop()
            }
        }
    }

    LaunchedEffect(Unit) {
        PhoneVerificationFlow.start(origin)
        EmailVerificationFlow.start(EmailDeeplinkOrigin.fromRoute(origin))
    }

    val startingScreens = buildStartingScreens(
        includePhone = includePhone,
        includeEmail = includeEmail,
        emailAddress = emailAddress,
        emailVerificationCode = emailVerificationCode,
        origin = origin,
    )

    if (startingScreens.isEmpty()) {
        goToTargetOrReturn(false)
        return
    }

    val internalNav = remember { InternalFlowNavigator() }

    LaunchedEffect(Unit) {
        if (internalNav.screens.isEmpty()) {
            internalNav.screens.addAll(startingScreens)
        }
    }

    val flowNavigator = remember(target, includePhone, includeEmail, showSuccess, context, internalNav) {
        object : FlowNavigator<VerificationFlowStep> {
            override fun exit(success: Boolean) {
                goToTargetOrReturn(success)
            }

            override fun continueFlowFrom(step: VerificationFlowStep) {
                when (step) {
                    VerificationFlowStep.Intro -> {
                        internalNav.push(VerificationInternalScreen.PhoneEntry)
                    }
                    VerificationFlowStep.Phone -> {
                        if (includeEmail) {
                            internalNav.push(VerificationInternalScreen.EmailEntry)
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

    CompositionLocalProvider(LocalFlowNavigator provides flowNavigator) {
        val currentScreen = internalNav.current ?: return@CompositionLocalProvider

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val dir = internalNav.slideDirection
                slideInHorizontally { fullWidth -> dir * fullWidth } togetherWith
                    slideOutHorizontally { fullWidth -> -dir * fullWidth }
            },
            label = "verification_flow"
        ) { screen ->
            when (screen) {
                is VerificationInternalScreen.Intro -> VerificationFlowIntroContent(
                    isForOnRamp = screen.isForOnRamp,
                )
                is VerificationInternalScreen.PhoneEntry -> PhoneVerificationContent(
                    onPushCountryCode = { internalNav.push(VerificationInternalScreen.PhoneCountryCode) },
                    onPushPhoneCode = { internalNav.push(VerificationInternalScreen.PhoneCode) },
                )
                is VerificationInternalScreen.PhoneCode -> PhoneCodeContent()
                is VerificationInternalScreen.PhoneCountryCode -> PhoneCountryCodeContent(
                    onPop = { internalNav.pop() }
                )
                is VerificationInternalScreen.EmailEntry -> EmailVerificationContent(
                    onPushMagicLink = { internalNav.push(VerificationInternalScreen.EmailMagicLink()) },
                )
                is VerificationInternalScreen.EmailMagicLink -> EmailMagicLinkContent(
                    email = screen.email,
                    code = screen.code,
                )
            }
        }
    }
}

private fun buildStartingScreens(
    origin: AppRoute,
    includePhone: Boolean,
    includeEmail: Boolean,
    emailAddress: String?,
    emailVerificationCode: String?,
): List<VerificationInternalScreen> {
    if (includePhone && includeEmail) {
        return listOf(VerificationInternalScreen.Intro(origin is AppRoute.OnRamp.AmountEntry))
    }
    if (includePhone) {
        return listOf(VerificationInternalScreen.PhoneEntry)
    }
    if (includeEmail) {
        return buildList {
            add(VerificationInternalScreen.EmailEntry)
            if (emailAddress != null && emailVerificationCode != null) {
                add(VerificationInternalScreen.EmailMagicLink(emailAddress, emailVerificationCode))
            }
        }
    }
    return emptyList()
}

enum class VerificationFlowStep: NavigationFlowStep {
    Intro,
    Phone,
    Email;
}
