package com.flipchat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipchat.features.login.LoginScreen
import com.flipchat.features.home.TabbedHomeScreen
import com.getcode.manager.SessionManager
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.theme.CodeCircularProgressIndicator
import kotlinx.coroutines.delay

internal data object MainRoot : Screen {

    override val key: ScreenKey = uniqueScreenKey

    private fun readResolve(): Any = this

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sessionState by SessionManager.authState.collectAsState()
        var showLoading by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.secondary),

        ) {
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.65f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
            ) {
                Image(
                    painter = painterResource(R.drawable.flipchat_logo),
                    contentDescription = "",
                    modifier = Modifier
                )
                Text(
                    text = stringResource(R.string.app_name_without_variant),
                    style = CodeTheme.typography.displayMedium,
                    color = White
                )

                if (showLoading) {
                    CodeCircularProgressIndicator()
                }
            }

            Spacer(Modifier.weight(1f))
        }

        LaunchedEffect(sessionState.isAuthenticated) {
            if (sessionState.isAuthenticated == null) {
                delay(500)
                showLoading = true
                return@LaunchedEffect
            }
            if (sessionState.isAuthenticated == true) {
                navigator.replace(AppHomeScreen)
            } else {
                navigator.replace(LoginScreen())
            }
        }
    }
}

typealias AppHomeScreen = TabbedHomeScreen


