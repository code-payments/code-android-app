package com.flipcash.app.login.accesskey

import android.Manifest
import android.os.Build
import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.login.internal.AccessKeyScreen
import com.flipcash.features.login.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.Cloudy
import com.getcode.ui.components.SelectionContainer
import com.getcode.ui.components.rememberSelectionState
import com.getcode.ui.core.addIf
import com.getcode.ui.core.measured
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.getPermissionLauncher
import com.getcode.util.permissions.rememberPermissionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class AccessKeyScreen : Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_accessKey)

    @Composable
    override fun Content() {
        val viewModel = getViewModel<LoginAccessKeyViewModel>()
        val navigator = LocalCodeNavigator.current
        val permissions = LocalPermissionChecker.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
            )
            AccessKeyScreen(viewModel) { requiresIap ->
                if (requiresIap) {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.CreateAccount.Purchase()))
                } else {
                    when {
                        permissions.isDenied(Manifest.permission.POST_NOTIFICATIONS) -> {
                            navigator.push(ScreenRegistry.get(NavScreenProvider.Permissions.Notification(true)))
                        }

                        permissions.isDenied(Manifest.permission.CAMERA) -> {
                            navigator.push(ScreenRegistry.get(NavScreenProvider.Permissions.Camera(true)))
                        }

                        else -> {
                            navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner()))
                        }
                    }
                }
            }
        }
    }
}