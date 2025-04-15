package com.flipcash.app.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.give.GiveScreen
import com.flipcash.app.login.accesskey.AccessKeyScreen
import com.flipcash.app.login.permissions.CameraPermissionScreen
import com.flipcash.app.login.permissions.NotificationPermissionScreen
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

        register<NavScreenProvider.Login.AccessKey> {
            if (it.showInModal) {
                AccessKeyScreen()
//                AccessKeyModalScreen()
            } else {
                AccessKeyScreen()
            }
        }

        register<NavScreenProvider.Login.NotificationPermission> {
            NotificationPermissionScreen(it.fromOnboarding)
        }

        register<NavScreenProvider.Login.CameraPermission> {
            CameraPermissionScreen(it.fromOnboarding)
        }

        register<NavScreenProvider.HomeScreen.Scanner> {
            ScannerScreen()
        }

        register<NavScreenProvider.HomeScreen.Give> {
            GiveScreen()
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