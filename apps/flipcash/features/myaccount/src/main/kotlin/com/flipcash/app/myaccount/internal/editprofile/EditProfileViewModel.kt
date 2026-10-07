package com.flipcash.app.myaccount.internal.editprofile

import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.AddMoneySource
import com.flipcash.analytics.events.AddMoneyEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.core.TotalBalanceProvider
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.app.userflags.UsernameGate
import com.flipcash.app.userflags.usernameGate
import com.flipcash.core.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/** The gate as the copy quotes it, `$100 USD` rather than `$100.00`. */
private fun Fiat.formattedGate(): String =
    formatted(rule = Fiat.FormattingRule.Truncated, suffix = currencyCode.name)

@HiltViewModel
internal class EditProfileViewModel @Inject constructor(
    userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    totalBalance: TotalBalanceProvider,
    tipPayments: TipPaymentDelegate,
    featuredGroups: FeaturedGroupsStore,
    private val purchaseMethodController: PurchaseMethodController,
    private val analytics: FlipcashAnalytics,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : BaseViewModel<EditProfileViewModel.State, EditProfileViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    internal data class State(
        val userId: ID? = null,
        val displayName: String = "",
        val username: String? = null,
        /** No handle yet, or one the server assigned: the row offers the claim instead of the handle. */
        val usernameNeedsClaim: Boolean = true,
        val bio: String = "",
        val avatar: MediaItem? = null,
        val cover: MediaItem? = null,
        /** What others pay to open a chat, formatted. Null until it resolves. */
        val minimumToChat: String? = null,
        /** How many public groups the profile features; the row reads it from the session's list. */
        val featuredGroupCount: Int = 0,
        val usernameGate: UsernameGate = UsernameGate.Unlocked,
        val usernameMinimumBalance: String = "",
    )

    internal sealed interface Event {
        data class OnProfileChanged(val profile: UserProfile) : Event
        data class OnMinimumToChatChanged(val minimumToChat: String?) : Event
        data class OnFeaturedGroupCountChanged(val count: Int) : Event
        data class OnUsernameGateChanged(val gate: UsernameGate, val minimumBalance: String) : Event

        /** A field card's tap; the screen opens the step in the profile editor. */
        data class OpenStep(val step: UpdateProfileStep) : Event
        data class OpenScreen(val screen: AppRoute) : Event
        data object UsernameClicked : Event
        data class PresentDepositOptions(val source: AddMoneySource) : Event
    }

    init {
        val profile = userManager.state
            .filter { it.authState is AuthState.Ready }
            .map { it.userProfile }
            .distinctUntilChanged()

        profile
            .mapNotNull { it }
            .onEach { dispatchEvent(Event.OnProfileChanged(it)) }
            .launchIn(viewModelScope)

        profile
            .flatMapLatest { tipPayments.startChattingFee(it) }
            .map { it?.formatted() }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnMinimumToChatChanged(it)) }
            .launchIn(viewModelScope)

        featuredGroups.groups
            .map { it.size }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnFeaturedGroupCountChanged(it)) }
            .launchIn(viewModelScope)

        combine(
            profile.map { it?.username to (it?.isUsernameAutoAssigned == true) },
            userFlags.resolvedFlags.map { it.usernameMinBalance.effectiveValue },
            totalBalance.observeTotalBalance(),
        ) { (username, isAutoAssigned), minimum, balance ->
            Event.OnUsernameGateChanged(
                gate = usernameGate(username, isAutoAssigned, minimum, balance),
                minimumBalance = minimum.formattedGate(),
            )
        }
            .distinctUntilChanged()
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .mapNotNull { event ->
                analytics.track(AddMoneyEvents.opened(event.source))
                purchaseMethodController.presentDepositOptions(popToRoot = true)
            }.onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)

        // A held handle goes straight to its editor. Claiming goes through the same balance gate
        // the You tab's progress card uses: the claim step once the balance clears the minimum, the
        // rule stated otherwise.
        eventFlow
            .filterIsInstance<Event.UsernameClicked>()
            .onEach {
                val state = stateFlow.value
                val gate = state.usernameGate
                if (!state.usernameNeedsClaim || gate !is UsernameGate.Locked) {
                    dispatchEvent(Event.OpenStep(UpdateProfileStep.Username))
                    return@onEach
                }
                BottomBarManager.showInfo(
                    title = resources.getString(
                        R.string.error_title_usernameMinimumBalance,
                        state.usernameMinimumBalance,
                    ),
                    message = resources.getString(
                        R.string.error_description_usernameMinimumBalance,
                        state.usernameMinimumBalance,
                    ),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_addMoney),
                            onClick = {
                                dispatchEvent(Event.PresentDepositOptions(AddMoneySource.USERNAME_SHORTFALL))
                            },
                        ),
                        BottomBarAction(
                            text = resources.getString(R.string.action_dismiss),
                            style = BottomBarManager.BottomBarButtonStyle.Text,
                        ),
                    ),
                )
            }
            .launchIn(viewModelScope)
    }

    internal companion object {
        val nameStep = UpdateProfileStep.Name(DisplayNameSource.MyAccount)

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnProfileChanged -> { state ->
                    val profile = event.profile
                    state.copy(
                        userId = profile.userId,
                        displayName = profile.displayName,
                        username = profile.username,
                        usernameNeedsClaim = profile.username.isNullOrBlank() || profile.isUsernameAutoAssigned,
                        bio = profile.bio,
                        avatar = profile.profilePicture,
                        cover = profile.coverPicture,
                    )
                }

                is Event.OnMinimumToChatChanged -> { state -> state.copy(minimumToChat = event.minimumToChat) }
                is Event.OnFeaturedGroupCountChanged -> { state -> state.copy(featuredGroupCount = event.count) }
                is Event.OnUsernameGateChanged -> { state ->
                    state.copy(usernameGate = event.gate, usernameMinimumBalance = event.minimumBalance)
                }

                is Event.OpenStep,
                is Event.OpenScreen,
                Event.UsernameClicked,
                is Event.PresentDepositOptions -> { state -> state }
            }
        }
    }
}
