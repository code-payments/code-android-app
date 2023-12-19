package com.getcode.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.view.login.LoginHome
import com.getcode.view.login.LoginViewModel
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.login.PhoneVerify
import com.getcode.view.login.PhoneVerifyViewModel
import com.getcode.view.login.SeedInput
import com.getcode.view.login.SeedInputViewModel

sealed interface NamedScreen {

    val name: String
        @Composable get() = ""

    val hasName: Boolean
        @Composable get() = name.isNotEmpty()
}

sealed interface ModalRoot

