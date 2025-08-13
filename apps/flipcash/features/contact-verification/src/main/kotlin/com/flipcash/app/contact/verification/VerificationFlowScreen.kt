package com.flipcash.app.contact.verification

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.flipcash.app.contact.verification.phone.PhoneVerificationScreen
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
class VerificationFlowScreen(
    private val origin: NavScreenProvider,
    private val target: NavScreenProvider,
    private val includePhone: Boolean = true,
    private val includeEmail: Boolean = true,
    private val emailAddress: String? = null,
    private val emailVerificationCode: String? = null,
): ModalScreen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalVoyagerApi::class)
    @Composable
    override fun ModalContent() {
        val codeNavigator = LocalCodeNavigator.current
        val screens = buildScreenSet(includePhone, includeEmail, emailAddress, emailVerificationCode)
        if (screens.isEmpty()) {
            codeNavigator.replace(ScreenRegistry.get(target))
            return
        }

        LifecycleEffectOnce {
            PhoneVerificationFlow.start(origin)
            EmailVerificationFlow.start(EmailDeeplinkOrigin.fromScreenProvider(origin))
        }

        Navigator(screens.toList()) { navigator ->
            val flowNavigator = remember {
                VerificationFlowNavigator(
                    exit = { codeNavigator.pop() },
                    continueFlowFrom = { step ->
                        when (step) {
                            VerificationFlowStep.Phone -> {
                                if (includeEmail) {
                                    navigator.push(EmailVerificationScreen())
                                } else {
                                    codeNavigator.replace(ScreenRegistry.get(target))
                                }
                            }
                            VerificationFlowStep.Email -> {
                                codeNavigator.replace(ScreenRegistry.get(target))
                            }
                        }
                    }
                )
            }

            CompositionLocalProvider(LocalVerificationFlowNavigator provides flowNavigator) {
                SlideTransition(navigator = navigator)
            }
        }
    }
}

private fun buildScreenSet(
    includePhone: Boolean,
    includeEmail: Boolean,
    emailAddress: String?,
    emailVerificationCode: String?,
): Set<Screen> {
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

enum class VerificationFlowStep {
    Phone,
    Email;
}

class VerificationFlowNavigator(
    val exit: () -> Unit = { },
    val continueFlowFrom: (VerificationFlowStep) -> Unit = { },
)

val LocalVerificationFlowNavigator = staticCompositionLocalOf { VerificationFlowNavigator() }