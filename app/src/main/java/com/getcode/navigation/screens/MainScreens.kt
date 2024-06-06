package com.getcode.navigation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.LocalDownloadQrCode
import com.getcode.R
import com.getcode.analytics.AnalyticsManager
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.model.KinAmount
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.CodeCircularProgressIndicator
import com.getcode.ui.components.Row
import com.getcode.ui.utils.RepeatOnLifecycle
import com.getcode.ui.utils.getActivityScopedViewModel
import com.getcode.util.generateQrCode
import com.getcode.util.rememberQrBitmapPainter
import com.getcode.util.shareDownloadLink
import com.getcode.utils.trace
import com.getcode.view.main.account.AccountHome
import com.getcode.view.main.account.AccountSheetViewModel
import com.getcode.view.main.giveKin.GiveKinScreen
import com.getcode.view.main.home.HomeScreen
import com.getcode.view.main.home.HomeViewModel
import com.getcode.view.main.requestKin.RequestKinScreen
import com.google.zxing.qrcode.encoder.QRCode
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt

sealed interface HomeResult {
    data class Bill(val bill: com.getcode.models.Bill) : HomeResult
    data class Request(val amount: KinAmount) : HomeResult
    data class ConfirmTip(val amount: KinAmount) : HomeResult
    data object ShowTipCard : HomeResult
}

@Parcelize
data class HomeScreen(
    val seed: String? = null,
    val cashLink: String? = null,
    val requestPayload: String? = null,
) : AppScreen(), MainGraph {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val vm = getViewModel<HomeViewModel>()

        trace("home rendered")
        HomeScreen(vm, cashLink, requestPayload)

        OnScreenResult<HomeResult> { result ->
            when (result) {
                is HomeResult.Bill -> {
                    vm.showBill(result.bill)
                }

                is HomeResult.Request -> {
                    vm.presentRequest(amount = result.amount, payload = null, request = null)
                }

                is HomeResult.ConfirmTip -> {
                    vm.presentTipConfirmation(result.amount)
                }

                is HomeResult.ShowTipCard -> {
                    vm.presentShareableTipCard()
                }
            }
        }
    }
}

@Parcelize
data object GiveKinModal : AppScreen(), MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    override val name: String
        @Composable get() = stringResource(id = R.string.title_giveKin)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        ModalContainer(
            closeButtonEnabled = {
                if (navigator.isVisible) {
                    it is GiveKinModal
                } else {
                    navigator.progress > 0f
                }
            },
        ) {
            GiveKinScreen(getViewModel())
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.GiveKin
        )
    }
}

@Parcelize
data class RequestKinModal(
    val showClose: Boolean = false,
) : AppScreen(), MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    override val name: String
        @Composable get() = stringResource(id = R.string.title_requestKin)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        val content = @Composable {
            RequestKinScreen(getViewModel())
        }

        if (showClose) {
            ModalContainer(
                closeButtonEnabled = {
                    if (navigator.isVisible) {
                        it is RequestKinModal
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
                        it is RequestKinModal
                    } else {
                        navigator.progress > 0f
                    }
                }
            ) {
                content()
            }
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.RequestKin
        )
    }
}

@Parcelize
data object AccountModal : MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getActivityScopedViewModel<AccountSheetViewModel>()
        ModalContainer(
            displayLogo = true,
            onLogoClicked = { viewModel.dispatchEvent(AccountSheetViewModel.Event.LogoClicked) },
            closeButtonEnabled = {
                if (navigator.isVisible) {
                    it is AccountModal
                } else {
                    navigator.progress > 0f
                }
            }
        ) {
            AccountHome(viewModel)
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.Settings
        )
    }
}

@Parcelize
data object ShareDownloadLinkModal : MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        ModalContainer(
            closeButtonEnabled = { it is ShareDownloadLinkModal }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x4),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.title_scanToDownloadCode),
                    style = CodeTheme.typography.subtitle1
                )


                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(
                        space = CodeTheme.dimens.grid.x7,
                        alignment = Alignment.CenterVertically
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val qrCode = LocalDownloadQrCode.current

                    if (qrCode != null) {
                        Image(
                            painter = qrCode,
                            contentDescription = "qr"
                        )
                    } else {
                        CodeCircularProgressIndicator()
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            space = CodeTheme.dimens.inset,
                            alignment = Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(painter = painterResource(id = R.drawable.ic_apple_icon), contentDescription = null)
                        Image(painter = painterResource(id = R.drawable.ic_android_icon), contentDescription = null)
                    }
                }

                val context = LocalContext.current
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(id = R.string.action_share),
                    onClick = { context.shareDownloadLink() }
                )
            }
        }
    }

}

@Composable
fun <T> AppScreen.OnScreenResult(block: (T) -> Unit) {
    RepeatOnLifecycle(
        targetState = Lifecycle.State.RESUMED,
    ) {
        result
            .filterNotNull()
            .mapNotNull { it as? T }
            .onEach { runCatching { block(it) } }
            .onEach { result.value = null }
            .launchIn(this)
    }
}
