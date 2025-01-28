package xyz.flipchat.app.features.login.register

import android.os.Parcelable
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.FullScreenModalScreen
import com.getcode.services.utils.onSuccessWithDelay
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.DisableSheetGestures
import com.getcode.util.getActivity
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.app.ui.LocalUserManager
import xyz.flipchat.controllers.PurchaseController
import xyz.flipchat.services.billing.BillingClient
import xyz.flipchat.services.billing.IapPaymentEvent
import xyz.flipchat.services.billing.IapProduct
import xyz.flipchat.services.billing.LocalBillingClient
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@Parcelize
class PurchaseAccountScreen : FullScreenModalScreen, Parcelable {

    @Composable
    override fun ModalContent() {
        Column {
            AppBarWithTitle(
                backButton = false,
            )
            PurchaseAccountScreenContent(getViewModel())
        }
        BackHandler { /** swallow **/ }
        DisableSheetGestures()
    }
}

@Composable
private fun PurchaseAccountScreenContent(viewModel: PurchaseAccountViewModel) {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val billingController = LocalBillingClient.current
    val userManager = LocalUserManager.current
    val composeScope = rememberCoroutineScope()

    val state by viewModel.stateFlow.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PurchaseAccountViewModel.Event.OnAccountCreated>()
            .onEach {
                navigator.hideWithResult(userManager?.userFlags?.isRegistered == true)
            }.launchIn(this)
    }

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
                    isLoading = state.creatingAccount.loading,
                    isSuccess = state.creatingAccount.success,
                    text = stringResource(R.string.action_purchaseAccount),
                ) {
                    composeScope.launch {
                        context.getActivity()?.let {
                            billingController.purchase(it, IapProduct.CreateAccount)
                        }
                    }
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
                    data = state.userId,
                    overlay = {
                        Image(
                            modifier = Modifier.size(60.dp),
                            imageVector = Icons.Default.CheckCircleOutline,
                            colorFilter = ColorFilter.tint(Color.White),
                            contentDescription = null,
                        )
                    }
                )

                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x10),
                    text = stringResource(R.string.title_finalizeAccountCreation),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain
                )

                Text(
                    text = stringResource(
                        R.string.subtitle_finalizeAccountCreation,
                        state.costOfAccount
                    ),
                    style = CodeTheme.typography.textMedium,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }
    }
}

@HiltViewModel
private class PurchaseAccountViewModel @Inject constructor(
    private val userManager: UserManager,
    private val authManager: AuthManager,
    iapController: PurchaseController,
    billingClient: BillingClient,
    resources: ResourceHelper,
) : BaseViewModel2<PurchaseAccountViewModel.State, PurchaseAccountViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val userId: ID? = null,
        val costOfAccount: String = "",
        val creatingAccount: LoadingSuccessState = LoadingSuccessState(),
    )

    sealed interface Event {
        data class OnUserIdChanged(val id: ID): Event
        data class OnCostChanged(val cost: String): Event
        data class OnCreatingChanged(val creating: Boolean, val created: Boolean = false) : Event
        data object OnAccountCreated: Event
    }

    init {
        userManager.state
            .mapNotNull { it.userId }
            .onEach { dispatchEvent(Event.OnUserIdChanged(it)) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            dispatchEvent(Event.OnCostChanged(billingClient.costOf(IapProduct.CreateAccount)))
        }

        billingClient.eventFlow
            .mapNotNull { event ->
                when (event) {
                    IapPaymentEvent.OnCancelled -> null
                    is IapPaymentEvent.OnError -> {
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                resources.getString(R.string.error_title_failedToPurchaseItem),
                                resources.getString(R.string.error_description_failedToPurchaseItem,)
                            )
                        )
                        null
                    }
                    is IapPaymentEvent.OnSuccess -> event
                }
            }.filterIsInstance<IapPaymentEvent.OnSuccess>()
            .onEach {
                dispatchEvent(Event.OnCreatingChanged(true))
                authManager.register(userManager.displayName!!)
                    .onSuccessWithDelay(2.seconds) {
                        dispatchEvent(Event.OnCreatingChanged(creating = false, created = true))
                        delay(2.seconds)
                        dispatchEvent(Event.OnAccountCreated)
                    }.onFailure {
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                resources.getString(R.string.error_title_failedToCreateAccount),
                                resources.getString(R.string.error_description_failedToCreateAccount)
                            )
                        )
                    }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OnAccountCreated -> { state -> state }
                is Event.OnCostChanged -> { state -> state.copy(costOfAccount = event.cost) }
                is Event.OnCreatingChanged -> { state -> state.copy(creatingAccount = LoadingSuccessState(loading = event.creating, success = event.created)) }
                is Event.OnUserIdChanged -> { state -> state.copy(userId = event.id) }
            }
        }
    }
}