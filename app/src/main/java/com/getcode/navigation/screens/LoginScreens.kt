package com.getcode.navigation.screens

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.utils.getStackScopedViewModel
import com.getcode.view.login.AccessKey
import com.getcode.view.login.AccessKeyViewModel
import com.getcode.view.login.CameraPermission
import com.getcode.view.login.InviteCode
import com.getcode.view.login.LoginHome
import com.getcode.view.login.NotificationPermission
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.login.PhoneVerify
import com.getcode.view.login.PhoneVerifyViewModel
import com.getcode.view.login.SeedDeepLink
import com.getcode.view.login.SeedInput
import com.getcode.view.login.SeedInputViewModel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class LoginScreen(val seed: String? = null) : LoginGraph {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.action_logIn)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        if (seed != null) {
            SeedDeepLink(getViewModel(), seed)
        } else {
            LoginHome(
                createAccount = {
                    navigator.push(LoginPhoneVerificationScreen(isNewAccount = true))
                },
                login = {
                    navigator.push(AccessKeyLoginScreen())
                }
            )
        }
    }
}

@Parcelize
data class LoginPhoneVerificationScreen(
    val arguments: LoginArgs = LoginArgs()
) : LoginGraph {
    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_enterPhoneNumber)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getStackScopedViewModel<PhoneVerifyViewModel>(key)
        PhoneVerify(viewModel, arguments) {
            navigator.show(PhoneAreaSelectionModal(key))
        }
    }
}

@Parcelize
data class LoginPhoneConfirmationScreen(
    val arguments: LoginArgs = LoginArgs()
) : LoginGraph {
    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_verifyPhoneNumber)

    @Composable
    override fun Content() {
        PhoneConfirm(
            getViewModel(),
            arguments = arguments,
        )
        BackHandler { /* intercept */ }
    }
}

@Parcelize
data class AccessKeyLoginScreen(
    val arguments: LoginArgs = LoginArgs()
) : LoginGraph {

    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_enterAccessKeyWords)

    @Composable
    override fun Content() {
        val viewModel: SeedInputViewModel = getViewModel()
        SeedInput(viewModel, arguments)
    }
}

@Parcelize
data class InviteCodeScreen(
    val arguments: LoginArgs = LoginArgs()
): LoginGraph {

    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.subtitle_inviteCode)
    @Composable
    override fun Content() {
        InviteCode(getViewModel(), arguments)
    }

}


@Parcelize
data class AccessKeyScreen(
    val arguments: LoginArgs = LoginArgs()
) : LoginGraph {

    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_accessKey)

    @Composable
    override fun Content() {
        val viewModel = getViewModel<AccessKeyViewModel>()
        AccessKey(viewModel, arguments)
        BackHandler { /* intercept */ }
    }
}

@Parcelize
sealed interface CodeLoginPermission: Parcelable {
    @Parcelize
    data object Camera : CodeLoginPermission
    @Parcelize
    data object Notifications : CodeLoginPermission
}

@Parcelize
data class PermissionRequestScreen(val permission: CodeLoginPermission) : LoginGraph {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        when (permission) {
            CodeLoginPermission.Camera -> {
                CameraPermission()
            }

            CodeLoginPermission.Notifications -> {
                NotificationPermission()
            }
        }

        BackHandler { /* intercept */ }
    }
}