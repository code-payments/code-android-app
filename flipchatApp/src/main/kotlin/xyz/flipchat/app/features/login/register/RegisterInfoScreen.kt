package xyz.flipchat.app.features.login.register

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.model.ID
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.services.billing.BillingController
import xyz.flipchat.services.billing.IapProduct
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@Parcelize
class RegisterInfoScreen : ModalScreen, Parcelable {


    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current

        Column {
            AppBarWithTitle(
                backButton = false,
                endContent = {
                    AppBarDefaults.Close { navigator.hide() }
                }
            )
            RegisterInfoScreenContent(
                viewModel = getViewModel(),
                onGetStarted = {
                    navigator.push(
                        ScreenRegistry.get(
                            NavScreenProvider.CreateAccount.NameEntry(
                                showInModal = true
                            )
                        )
                    )
                },
                onNotNow = {
                    navigator.hide()
                }
            )
        }
    }
}

@Composable
private fun RegisterInfoScreenContent(
    viewModel: RegisterInfoViewModel,
    onGetStarted: () -> Unit,
    onNotNow: () -> Unit
) {
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_getStarted)
                ) {
                    onGetStarted()
                }

                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonState = ButtonState.Subtle,
                    text = stringResource(R.string.action_notNow),
                ) {
                    onNotNow()
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatar(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    data = viewModel.userId,
                    overlay = {
                        Image(
                            modifier = Modifier.size(60.dp),
                            imageVector = Icons.Default.Person,
                            colorFilter = ColorFilter.tint(Color.White),
                            contentDescription = null,
                        )
                    }
                )

                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x10),
                    text = stringResource(R.string.title_createAccountToJoinRooms),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain
                )

                Text(
                    text = "New accounts cost ${viewModel.costOfAccount}",
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }
    }
}

@HiltViewModel
private class RegisterInfoViewModel @Inject constructor(
    private val userManager: UserManager,
    private val iapController: BillingController
) : ViewModel() {

    val userId: ID?
        get() = userManager.userId

    val costOfAccount: String
        get() = iapController.costOf(IapProduct.CreateAccount)
}