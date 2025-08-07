package com.flipcash.app.onramp

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.onramp.internal.screens.OnRampSuccessScreenContent
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class OnRampSuccessScreen : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val origin = OnRampFlowTracker.source

        val close: () -> Unit = {
            if (origin != null) {
                val screen = ScreenRegistry.get(origin)
                navigator.popUntil {
                    it.key == screen.key
                }
            } else {
                navigator.popAll()
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                isInModal = true,
                backButton = true,
                onBackIconClicked = close
            )

            OnRampSuccessScreenContent(close)
        }

        BackHandler(onBack = close)
    }
}