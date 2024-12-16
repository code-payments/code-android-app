package xyz.flipchat.app.features.login

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
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class LoginScreen(val seed: String? = null) : Screen, Parcelable {
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
                .onEach { navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.AppHomeScreen())) }
                .launchIn(this)
        }

        if (seed != null) {
//            SeedDeepLink(getViewModel(), seed)
        } else {
            LoginHome(
                isCreatingAccount = state.creatingAccount,
                betaFlagsVisible = state.betaOptionsVisible,
                isSpectatorJoinEnabled = state.followerModeEnabled,
                onLogoTapped = { vm.dispatchEvent(LoginViewModel.Event.OnLogoTapped) },
                openBetaFlags = {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.BetaFlags))
                },
                createAccount = {
                    if (state.followerModeEnabled) {
                        vm.dispatchEvent(LoginViewModel.Event.CreateAccount)
                    } else {
                        navigator.push(ScreenRegistry.get(NavScreenProvider.CreateAccount.NameEntry()))
                    }
                },
                login = {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Login.SeedInput))
                }
            )
        }
    }
}