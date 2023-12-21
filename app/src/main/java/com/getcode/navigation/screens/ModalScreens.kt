package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.main.account.AccountAccessKey
import com.getcode.view.main.account.AccountDebugOptions
import com.getcode.view.main.account.AccountDeposit
import com.getcode.view.main.account.AccountDetails
import com.getcode.view.main.account.AccountFaq
import com.getcode.view.main.account.ConfirmDeleteAccount
import com.getcode.view.main.account.DeleteCodeAccount
import com.getcode.view.main.getKin.BuyAndSellKin

data object BuySellScreen : MainGraph, ModalContent {
    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is BuySellScreen }) {
            BuyAndSellKin(getViewModel())
        }
    }
}

data object DepositKinScreen : MainGraph, ModalContent, NamedScreen {

    override val name: String
        @Composable get() = stringResource(id = R.string.title_depositKin)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is DepositKinScreen }) {
            AccountDeposit()
        }
    }
}

data object FaqScreen : MainGraph, NamedScreen, ModalContent {

    override val name: String
        @Composable get() = stringResource(id = R.string.title_faq)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is FaqScreen }) {
            AccountFaq(getViewModel())
        }
    }
}

data object AccountDebugOptionsScreen : MainGraph, NamedScreen, ModalContent {
    override val name: String
        @Composable get() = stringResource(id = R.string.title_myAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is AccountDebugOptionsScreen }) {
            AccountDebugOptions(getViewModel())
        }
    }
}

data object AccountDetailsScreen : MainGraph, NamedScreen, ModalContent {

    override val name: String
        @Composable get() = stringResource(id = R.string.title_myAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is AccountDetailsScreen }) {
            AccountDetails(getViewModel())
        }
    }
}

data object AccountAccessKeyScreen : MainGraph, NamedScreen, ModalContent {
    override val name: String
        @Composable get() = stringResource(id = R.string.title_accessKey)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is AccountAccessKeyScreen }) {
            AccountAccessKey(getViewModel())
        }
    }
}

data object PhoneNumberScreen : MainGraph, NamedScreen, ModalContent {
    override val name: String
        @Composable get() = stringResource(id = R.string.title_phoneNumber)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is PhoneNumberScreen }) {
            PhoneConfirm(getViewModel())
        }
    }
}

data object DeleteCodeScreen : MainGraph, NamedScreen, ModalContent {
    override val name: String
        @Composable get() = stringResource(id = R.string.title_deleteAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is DeleteCodeScreen }) {
            DeleteCodeAccount()
        }
    }
}

data object DeleteConfirmationScreen : MainGraph, NamedScreen, ModalContent {
    override val name: String
        @Composable get() = stringResource(id = R.string.title_deleteAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is DeleteConfirmationScreen }) {
            ConfirmDeleteAccount()
        }
    }
}