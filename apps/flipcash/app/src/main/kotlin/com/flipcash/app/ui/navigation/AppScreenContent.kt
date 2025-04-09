package com.flipcash.app.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.login.router.LoginRouter
import com.flipcash.app.login.seed.SeedInputScreen
import com.flipcash.app.scanner.ScannerScreen


@Composable
fun AppScreenContent(content: @Composable () -> Unit) {
    ScreenRegistry {
        register<NavScreenProvider.Login.Home> {
            LoginRouter(it.seed)
        }

        register<NavScreenProvider.Login.SeedInput> {
            SeedInputScreen()
        }

        register<NavScreenProvider.HomeScreen.Scanner> {
            ScannerScreen()
        }

        register<NavScreenProvider.HomeScreen.Give> {
            Dummy()
        }

        register<NavScreenProvider.HomeScreen.Send> {
            Dummy()
        }

        register<NavScreenProvider.HomeScreen.Balance> {
            Dummy()
        }

        register<NavScreenProvider.HomeScreen.Menu> {
            Dummy()
        }
    }
    content()
}

private class Dummy: Screen {
    @Composable
    override fun Content() {

    }
}