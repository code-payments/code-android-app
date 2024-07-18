package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.utils.getActivityScopedViewModel
import com.getcode.ui.utils.getStackScopedViewModel
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.login.PhoneVerify
import com.getcode.view.login.PhoneVerifyViewModel
import com.getcode.view.main.account.AccountDeposit
import com.getcode.view.main.account.AccountDetails
import com.getcode.view.main.account.AccountFaq
import com.getcode.view.main.account.AccountPhone
import com.getcode.view.main.account.AppSettingsScreen
import com.getcode.view.main.account.BackupKey
import com.getcode.view.main.account.BetaFlagsScreen
import com.getcode.view.main.account.ConfirmDeleteAccount
import com.getcode.view.main.account.DeleteCodeAccount
import com.getcode.view.main.currency.CurrencySelectKind
import com.getcode.view.main.currency.CurrencySelectionSheet
import com.getcode.view.main.currency.CurrencyViewModel
import com.getcode.view.main.getKin.BuyAndSellKin
import com.getcode.view.main.getKin.BuyKinScreen
import com.getcode.view.main.getKin.GetKinSheet
import com.getcode.view.main.getKin.GetKinSheetViewModel
import com.getcode.view.main.tip.EnterTipScreen
import com.getcode.view.main.tip.RequestTipScreen
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize


@Parcelize
data object DepositKinScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_depositKin)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is DepositKinScreen }) {
            AccountDeposit()
        }
    }
}

@Parcelize
data object FaqScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_faq)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is FaqScreen }) {
            AccountFaq(getViewModel())
        }
    }
}

@Parcelize
data object AccountDebugOptionsScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_betaFlags)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is AccountDebugOptionsScreen }) {
            BetaFlagsScreen(getViewModel())
        }
    }
}

@Parcelize
data object AppSettingsScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_appSettings)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is AppSettingsScreen }) {
            AppSettingsScreen(getViewModel())
        }
    }
}

@Parcelize
data object AccountDetailsScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_myAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is AccountDetailsScreen }) {
            AccountDetails(getActivityScopedViewModel())
        }
    }
}

@Parcelize
data object BackupScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_accessKey)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is BackupScreen }) {
            BackupKey(getViewModel())
        }
    }
}

@Parcelize
data object PhoneNumberScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_phoneNumber)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is PhoneNumberScreen }) {
            AccountPhone(getViewModel())
        }
    }
}

@Parcelize
data class PhoneVerificationScreen(
    val arguments: LoginArgs = LoginArgs()
) : MainGraph, ModalContent {
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
        ModalContainer(backButtonEnabled = { it is PhoneVerificationScreen }) {
            PhoneVerify(viewModel, arguments) {
                navigator.show(PhoneAreaSelectionModal(key))
            }
        }
    }
}

@Parcelize
data class PhoneAreaSelectionModal(val providedKey: String) : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_selectCountry)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val vm = getStackScopedViewModel<PhoneVerifyViewModel>(providedKey)

        ModalContainer(closeButtonEnabled = { it is PhoneAreaSelectionModal }) {
            PhoneCountrySelection(viewModel = vm) {
                navigator.hide()
            }
        }
    }
}

@Parcelize
data class PhoneConfirmationScreen(
    val arguments: LoginArgs = LoginArgs()
) : MainGraph, ModalContent {
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
        ModalContainer(backButtonEnabled = { it is PhoneConfirmationScreen }) {
            PhoneConfirm(
                getViewModel(),
                arguments = arguments,
            )
        }
    }
}


@Parcelize
data object DeleteCodeScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.action_deleteAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is DeleteCodeScreen }) {
            DeleteCodeAccount()
        }
    }
}

@Parcelize
data object DeleteConfirmationScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.action_deleteAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is DeleteConfirmationScreen }) {
            ConfirmDeleteAccount(getViewModel())
        }
    }
}

@Parcelize
data class CurrencySelectionModal(val kind: CurrencySelectKind = CurrencySelectKind.Entry) : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    override val name: String
        @Composable get() = stringResource(id = R.string.title_selectCurrency)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getActivityScopedViewModel<CurrencyViewModel>()
        ModalContainer(
            backButtonEnabled = {
                if (navigator.isVisible) {
                    it is CurrencySelectionModal
                } else {
                    navigator.progress > 0f
                }
            }
        ) {
            CurrencySelectionSheet(viewModel = viewModel)
        }

        LaunchedEffect(viewModel, kind) {
            viewModel.dispatchEvent(CurrencyViewModel.Event.OnKindChanged(kind))
        }
    }
}

@Parcelize
data class BuyMoreKinModal(
    val showClose: Boolean = false,
) : MainGraph, ModalRoot {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.action_buyMoreKin)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val content = @Composable {
            BuyKinScreen(
                viewModel = getViewModel(),
                onRedirected = {
                    if (showClose) {
                        navigator.hide()
                    } else {
                        navigator.popAll()
                    }
                }
            )
        }

        if (showClose) {
            ModalContainer(
                closeButtonEnabled = {
                    if (navigator.isVisible) {
                        it is BuyMoreKinModal
                    } else {
                        navigator.progress > 0f
                    }
                }
            ) {
                content()
            }
        } else {
            ModalContainer(
                backButtonEnabled = {
                    if (navigator.isVisible) {
                        it is BuyMoreKinModal
                    } else {
                        navigator.progress > 0f
                    }
                }
            ) {
                content()
            }
        }
    }
}

@Parcelize
data class EnterTipModal(val isInChat: Boolean = false) : MainGraph, ModalRoot {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    override val name: String
        @Composable get() =
            if (isInChat) stringResource(R.string.title_sendKin)
            else stringResource(id = R.string.title_tipKin)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        if (isInChat) {
            ModalContainer(
                backButtonEnabled = {
                    if (navigator.isVisible) {
                        it is EnterTipModal
                    } else {
                        navigator.progress > 0f
                    }
                }
            ) {
                EnterTipScreen(getViewModel()) { result ->
                    navigator.popWithResult(result)
                }
            }
        } else {
            ModalContainer(
                closeButtonEnabled = {
                    if (navigator.isVisible) {
                        it is EnterTipModal
                    } else {
                        navigator.progress > 0f
                    }
                }
            ) {
                EnterTipScreen(getViewModel()) { result ->
                    navigator.hideWithResult(result)
                }
            }
        }
    }

}

@Parcelize
data object RequestTip : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        ModalContainer(
            backButtonEnabled = {
                if (navigator.isVisible) {
                    it is RequestTip
                } else {
                    navigator.progress > 0f
                }
            }
        ) {
            RequestTipScreen(getViewModel())
        }
    }
}

@Parcelize
data object GetKinModal : MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        val viewModel = getViewModel<GetKinSheetViewModel>()
        ModalContainer(
            closeButtonEnabled = {
                if (navigator.isVisible) {
                    it is GetKinModal
                } else {
                    navigator.progress > 0f
                }
            },
        ) {
            GetKinSheet(viewModel)
        }
    }
}

@Parcelize
data object BuySellScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        ModalContainer(backButtonEnabled = { it is BuySellScreen }) {
            BuyAndSellKin(getViewModel())
        }
    }
}
