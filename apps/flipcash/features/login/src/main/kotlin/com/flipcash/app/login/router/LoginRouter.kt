package com.flipcash.app.login.router

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.navigateTo
import com.flipcash.app.login.internal.LoginRouterScreenContent
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun LoginRouter(
    seed: String? = null,
    fromDeeplink: Boolean = false,
) {
    val vm = hiltViewModel<LoginViewModel>()
    val state by vm.stateFlow.collectAsState()
    val navigator = LocalCodeNavigator.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    LaunchedEffect(vm) {
        vm.eventFlow
            .filterIsInstance<LoginViewModel.Event.OnAccountCreated>()
            .onEach { navigator.push(AppRoute.Onboarding.AccessKey) }
            .launchIn(this)
    }

    LaunchedEffect(vm) {
        vm.eventFlow
            .filterIsInstance<LoginViewModel.Event.LoggedInSuccessfully>()
            .onEach { delay(500) }
            .onEach { navigator.replaceAll(AppRoute.Main.Scanner) }
            .launchIn(this)
    }

    LaunchedEffect(vm) {
        vm.eventFlow
            .filterIsInstance<LoginViewModel.Event.LoggedInRequiresPayment>()
            .onEach { delay(500) }
            .onEach {
                navigator.push(
                    routes = listOf(
                        AppRoute.Onboarding.AccessKey,
                        AppRoute.Onboarding.Purchase(true)
                    )
                )
            }
            .launchIn(this)
    }

    LaunchedEffect(seed) {
        if (seed != null) {
            vm.dispatchEvent(LoginViewModel.Event.LogIn(seed, fromDeeplink))
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
    ) {
        LoginRouterScreenContent(
            isLoggingIn = state.loggingIn,
            createAccount = { vm.dispatchEvent(LoginViewModel.Event.CreateAccount) },
            login = { navigator.push(AppRoute.Onboarding.SeedInput) },
            isLabsOpen = state.betaOptionsVisible,
            onLogoTapped = { vm.dispatchEvent(LoginViewModel.Event.OnLogoTapped) },
            openBetaFlags = { navigator.navigateTo(AppRoute.Sheets.Lab) }
        )
    }
}
