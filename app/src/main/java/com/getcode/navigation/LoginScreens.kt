package com.getcode.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.view.login.AccessKey
import com.getcode.view.login.AccessKeyViewModel
import com.getcode.view.login.CameraPermission
import com.getcode.view.login.LoginHome
import com.getcode.view.login.LoginViewModel
import com.getcode.view.login.NotificationPermission
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.login.PhoneVerify
import com.getcode.view.login.PhoneVerifyViewModel
import com.getcode.view.login.SeedInput
import com.getcode.view.login.SeedInputViewModel

sealed interface LoginGraph : Screen {
    fun readResolve(): Any = this
}

data class LoginArgs(
    val signInEntropy: String? = null,
    val isPhoneLinking: Boolean = false,
    val isNewAccount: Boolean = false,
    val phoneNumber: String? = null
)


data object LoginScreen : LoginGraph, NamedScreen {
    override val name: String
        @Composable get() = stringResource(id = R.string.action_logIn)

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getViewModel<LoginViewModel>()
        LoginHome(viewModel)
    }
}

data class PhoneVerificationScreen(
    val arguments: LoginArgs = LoginArgs()
) : LoginGraph, NamedScreen {
    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    override val name: String
        @Composable get() = stringResource(R.string.title_enterPhoneNumber)

    @Composable
    override fun Content() {
        val viewModel = getViewModel<PhoneVerifyViewModel>()
        PhoneVerify(viewModel, arguments)
    }
}

data class PhoneConfirmationScreen(
    val arguments: LoginArgs = LoginArgs()
) : LoginGraph, NamedScreen {
    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    override val name: String
        @Composable get() = stringResource(R.string.title_verifyPhoneNumber)

    @Composable
    override fun Content() {
        PhoneConfirm(arguments = arguments)
    }
}

data class InviteCodeScreen(
    val arguments: LoginArgs = LoginArgs()
) : LoginGraph, NamedScreen {

    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    override val name: String
        @Composable get() = stringResource(R.string.title_enterAccessKeyWords)

    @Composable
    override fun Content() {
        val viewModel: SeedInputViewModel = getViewModel()
        SeedInput(viewModel, arguments)
    }
}

data class AccessKeyScreen(
    val arguments: LoginArgs = LoginArgs()
): LoginGraph, NamedScreen {

    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    override val name: String
        @Composable get() = stringResource(id = R.string.title_accessKey)

    @Composable
    override fun Content() {
        val viewModel = getViewModel<AccessKeyViewModel>()
        AccessKey(viewModel)
    }
}

sealed interface CodeLoginPermission {
    data object Camera: CodeLoginPermission
    data object Notifications: CodeLoginPermission
}

data class PermissionRequestScreen(val permission: CodeLoginPermission): LoginGraph {
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
    }
}