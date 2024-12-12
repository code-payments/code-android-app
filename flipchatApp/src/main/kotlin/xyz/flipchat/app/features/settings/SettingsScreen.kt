package xyz.flipchat.app.features.settings

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SettingsRow
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.getActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R

@Parcelize
class SettingsScreen : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getViewModel<SettingsViewModel>()
        val navigator = LocalCodeNavigator.current
        val context = LocalContext.current
        val composeScope = rememberCoroutineScope()

        val state by viewModel.stateFlow.collectAsState()

        CodeScaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    vertical = CodeTheme.dimens.grid.x2,
                ),
            bottomBar = {
                LogoutButton {
                    composeScope.launch {
                        delay(150) // wait for dismiss
                        context.getActivity()?.let {
                            viewModel.logout(it) {
                                navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    Box(modifier = Modifier.fillParentMaxWidth()) {
                        Image(
                            painter = painterResource(R.drawable.flipchat_logo),
                            contentDescription = "",
                            modifier = Modifier
                                .padding(vertical = CodeTheme.dimens.inset)
                                .align(Alignment.Center)
                                .size(CodeTheme.dimens.staticGrid.x12),

                            )
                    }
                }
                if (state.isStaff) {
                    item {
                        SettingsRow(
                            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                            title = stringResource(R.string.title_betaFlags),
                            icon = rememberVectorPainter(Icons.Outlined.Science)
                        ) {
                            navigator.push(ScreenRegistry.get(NavScreenProvider.BetaFlags))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoutButton(
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    CodeButton(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CodeTheme.dimens.inset),
        buttonState = ButtonState.Filled,
        text = stringResource(R.string.action_logout)
    ) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = context.getString(R.string.prompt_title_logout),
                subtitle = context
                    .getString(R.string.prompt_description_logout),
                positiveText = context.getString(R.string.action_logout),
                tertiaryText = context.getString(R.string.action_cancel),
                onPositive = {
                    onConfirmed()
                }
            )
        )
    }
}