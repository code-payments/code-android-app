package com.flipcash.app.login.router

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.login.internal.LoginRouterScreenContent
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration.Companion.seconds

@Parcelize
class LoginRouter(private val seed: String? = null) : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val vm = getViewModel<LoginViewModel>()
        val state by vm.stateFlow.collectAsState()
        val navigator = LocalCodeNavigator.current

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<LoginViewModel.Event.OnAccountCreated>()
                .onEach { delay(2.seconds) }
                .onEach { navigator.push(ScreenRegistry.get(NavScreenProvider.CreateAccount.AccessKey)) }
                .launchIn(this)
        }

        if (seed != null) {
//            SeedDeepLink(getViewModel(), seed)
        } else {
            LoginRouterScreenContent(
                isCreatingAccount = state.creatingAccount,
                betaFlagsVisible = state.betaOptionsVisible,
                isSpectatorJoinEnabled = state.followerModeEnabled,
                onLogoTapped = { vm.dispatchEvent(LoginViewModel.Event.OnLogoTapped) },
                openBetaFlags = {
//                    navigator.push(ScreenRegistry.get(NavScreenProvider.BetaFlags))
                },
                createAccount = {
                    vm.dispatchEvent(LoginViewModel.Event.CreateAccount)
                },
                login = {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Login.SeedInput))
                }
            )
        }
    }
}